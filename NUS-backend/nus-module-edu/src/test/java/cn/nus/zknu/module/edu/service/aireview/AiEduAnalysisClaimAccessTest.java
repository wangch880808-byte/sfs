package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.ai.api.edu.AiEduReviewApi;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import cn.nus.zknu.module.edu.dal.mysql.statistics.StatisticsMapper;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import cn.nus.zknu.framework.datapermission.core.db.DataPermissionRuleHandler;
import cn.nus.zknu.framework.datapermission.core.rule.DataPermissionRuleFactory;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.mockito.ArgumentCaptor;
import java.util.UUID;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.AI_ANALYSIS_SCOPE_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import reactor.core.publisher.Flux;

import java.util.List;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static org.mockito.Mockito.*;

class AiEduAnalysisClaimAccessTest extends BaseMockitoUnitTest {
    @InjectMocks private AiEduAnalysisServiceImpl service;
    @Mock private EduAiBusinessAccessService accessService;
    @Mock private cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService dataScopeService;
    @Mock private EduClassMapper classMapper;
    @Mock private CapacityCourseApi capacityCourseApi;
    @Mock private EduClassStudentMapper classStudentMapper;
    @Mock private EduHomeworkMapper homeworkMapper;
    @Mock private EduHomeworkRecordMapper homeworkRecordMapper;
    @Mock private EduHomeworkAssigneeMapper assigneeMapper;
    @Mock private EduMetricPolicy metricPolicy;
    @Mock private AiEduReviewApi aiEduReviewApi;
    @Mock private StatisticsMapper statisticsMapper;

    @BeforeEach
    void setUp() {
        lenient().when(statisticsMapper.selectAnalysisClassCourseExists(2L, 3L)).thenReturn(true);
    }

    @Test
    void readOnlyReviewerCanAnalyzeWithoutAssignmentPermission() {
        var member = new EduClassStudentDO(); member.setStudentId(10L);
        when(classStudentMapper.selectListByClassId(3L)).thenReturn(List.of(member));
        var task = new EduHomeworkDO(); task.setId(1L); task.setClassId(3L); task.setCourseId(2L);
        when(homeworkMapper.selectList(any())).thenReturn(List.of(task));
        when(dataScopeService.isPublicCourse(2L)).thenReturn(true);
        when(aiEduReviewApi.generateCommentStream(any())).thenReturn(Flux.just("研判结果"));
        service.analyzeClassScoreStream(3L, 2L, 1L).blockLast();
        verify(dataScopeService).validateClassCourseAccess(3L, 2L);
        verify(statisticsMapper).selectAnalysisClassCourseExists(2L, 3L);
        verify(accessService).validateHomeworkViewAccess(task);
        verify(accessService, never()).validateCourseClassAccess(anyLong(), anyLong());
    }

    @Test
    void newClaimantCannotAnalyzeFormerTeachersTask() {
        var member = new EduClassStudentDO(); member.setStudentId(10L);
        when(classStudentMapper.selectListByClassId(3L)).thenReturn(List.of(member));
        var task = new EduHomeworkDO(); task.setId(1L); task.setClassId(3L); task.setCourseId(2L);
        when(homeworkMapper.selectList(any())).thenReturn(List.of(task));
        when(dataScopeService.isPublicCourse(2L)).thenReturn(true);
        doThrow(exception(FORBIDDEN)).when(accessService).validateHomeworkViewAccess(task);
        assertServiceException(() -> service.analyzeClassScoreStream(3L, 2L, 1L), FORBIDDEN);
        verifyNoInteractions(homeworkRecordMapper, assigneeMapper, aiEduReviewApi);
    }

    @Test
    void inaccessibleClassIsRejectedBeforeLoadingStudentsOrCallingAi() {
        doThrow(exception(FORBIDDEN)).when(dataScopeService).validateCourseAccess(2L);
        assertServiceException(() -> service.analyzeClassScoreStream(3L, 2L, 1L), FORBIDDEN);
        verifyNoInteractions(classStudentMapper, homeworkMapper, homeworkRecordMapper, aiEduReviewApi, statisticsMapper);
    }

    @Test
    void deletedAnalysisScopeIsRejectedBeforeLoadingHistory() {
        when(statisticsMapper.selectAnalysisClassCourseExists(2L, 3L)).thenReturn(false);
        assertServiceException(() -> service.analyzeClassScoreStream(3L, 2L, 1L), AI_ANALYSIS_SCOPE_INVALID);
        verify(dataScopeService).validateCourseAccess(2L);
        verify(dataScopeService).validateClassAccess(3L);
        verifyNoInteractions(classStudentMapper, homeworkMapper, homeworkRecordMapper, aiEduReviewApi);
    }

    @ParameterizedTest
    @CsvSource({"1,0,true", "2,0,true", "3,0,true", "0,0,false", "3,1,false", "1,1,false"})
    void analysisUsesRealHomeworkQueryToIncludeArchivedAndExcludeDraftOrDeleted(int status, int deleted,
                                                                              boolean included) throws Exception {
        var configuration = new MybatisConfiguration();
        var ds = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:ai_analysis_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=FALSE", "sa", "");
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        var ruleFactory = mock(DataPermissionRuleFactory.class);
        when(ruleFactory.getDataPermissionRule(anyString())).thenReturn(List.of());
        var chain = new MybatisPlusInterceptor();
        chain.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionRuleHandler(ruleFactory)));
        chain.addInnerInterceptor(new PaginationInnerInterceptor());
        configuration.addInterceptor(chain);
        configuration.addMapper(EduHomeworkMapper.class);
        try (var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession()) {
            var info = TableInfoHelper.getTableInfo(EduHomeworkDO.class);
            String columns = info.getFieldList().stream().map(field -> field.getColumn() + " "
                    + (field.getPropertyType() == String.class ? "VARCHAR(200)"
                    : field.getPropertyType() == java.time.LocalDateTime.class ? "TIMESTAMP"
                    : field.getPropertyType() == Boolean.class ? "BOOLEAN" : "BIGINT"))
                    .collect(java.util.stream.Collectors.joining(","));
            try (var statement = session.getConnection().createStatement()) {
                statement.execute("CREATE TABLE edu_homework (id BIGINT PRIMARY KEY," + columns + ")");
                statement.execute("INSERT INTO edu_homework (id,homework_name,class_id,course_id,status,deleted) VALUES "
                        + "(1,'历史任务',3,2," + status + "," + (deleted == 1 ? "TRUE" : "FALSE") + ")");
            }
            doAnswer(invocation -> session.getMapper(EduHomeworkMapper.class)
                    .selectList(invocation.<Wrapper<EduHomeworkDO>>getArgument(0)))
                    .when(homeworkMapper).selectList(any());
            var member = new EduClassStudentDO(); member.setStudentId(10L);
            when(classStudentMapper.selectListByClassId(3L)).thenReturn(List.of(member));
            var clazz = new EduClassDO(); clazz.setClassName("封存班级"); clazz.setStatus(1);
            when(classMapper.selectById(3L)).thenReturn(clazz);
            var course = new CapacityCourseRespDTO(); course.setCourseName("封存课程"); course.setStatus(1);
            when(capacityCourseApi.getCourse(2L)).thenReturn(course);
            if (included) {
                when(aiEduReviewApi.generateCommentStream(any())).thenReturn(Flux.just("历史分析"));
                assertEquals("历史分析", service.analyzeClassScoreStream(3L, 2L, 1L).blockLast());
                var request = ArgumentCaptor.forClass(cn.nus.zknu.module.ai.api.edu.dto.AiEduGenerateReqDTO.class);
                verify(aiEduReviewApi).generateCommentStream(request.capture());
                assertTrue(request.getValue().getStudentData().contains("任务数量：1"));
                verify(dataScopeService).validateCourseAccess(2L);
                verify(dataScopeService).validateClassAccess(3L);
                verify(accessService, never()).validateCourseClassViewAccess(anyLong(), anyLong());
            } else {
                assertServiceException(() -> service.analyzeClassScoreStream(3L, 2L, 1L), AI_ANALYSIS_SCOPE_INVALID);
                verifyNoInteractions(homeworkRecordMapper, assigneeMapper, aiEduReviewApi);
            }
        }
    }
}
