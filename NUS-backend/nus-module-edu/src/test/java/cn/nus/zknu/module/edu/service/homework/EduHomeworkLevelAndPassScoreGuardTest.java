package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkSaveReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkStatisticsRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.coursemanage.EduCourseStatMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.service.aireview.EduAiBusinessAccessService;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.List;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_LEVEL_LOCKED_AFTER_SUBMISSION;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 已有提交后不能改等级；改达标分数线允许，但记入操作日志；归档后的任务统计只读。 */
class EduHomeworkLevelAndPassScoreGuardTest extends BaseMockitoUnitTest {

    @Mock
    private EduHomeworkPublishService publishService;
    @Mock
    private EduHomeworkAiCreateService aiCreateService;

    @Mock
    private cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService dataScopeService;

    @InjectMocks
    private EduHomeworkServiceImpl service;
    @Mock
    private EduHomeworkMapper homeworkMapper;
    @Mock
    private EduHomeworkRecordMapper recordMapper;
    @Mock
    private EduHomeworkAssigneeMapper assigneeMapper;
    @Mock
    private EduCourseStatMapper courseStatMapper;
    @Mock
    private EduAiBusinessAccessService aiBusinessAccessService;
    @Mock
    private CapacityCourseApi capacityCourseMapper;
    @Mock
    private EduClassMapper classMapper;
    @Mock
    private EduClassCourseMapper classCourseMapper;
    @Mock
    private EduMetricPolicy metricPolicy;
    @Mock
    private EduHomeworkChangeLogService changeLogService;
    @Mock
    private EduHomeworkArchiveAccessService archiveAccessService;
    @Mock
    private EduBusinessNotifyService notifyService;
    @Mock
    private EduHomeworkChangeNotifier homeworkChangeNotifier;

    private MockedStatic<SecurityFrameworkUtils> security;
    private EduHomeworkDO current;

    @BeforeEach
    void setUp() {
        EduHomeworkCreationSupport creationSupport = new EduHomeworkCreationSupport();
        ReflectionTestUtils.setField(creationSupport, "eduHomeworkMapper", homeworkMapper);
        ReflectionTestUtils.setField(creationSupport, "metricPolicy", metricPolicy);
        ReflectionTestUtils.setField(service, "creationSupport", creationSupport);
        EduHomeworkStatisticsService statisticsService = new EduHomeworkStatisticsService();
        ReflectionTestUtils.setField(statisticsService, "courseStatMapper", courseStatMapper);
        lenient().when(courseStatMapper.selectHomeworkStatistics(eq(1L), anyInt()))
                .thenReturn(new EduCourseStatMapper.HomeworkStat());
        ReflectionTestUtils.setField(statisticsService, "archiveAccessService", archiveAccessService);
        ReflectionTestUtils.setField(statisticsService, "metricPolicy", metricPolicy);
        ReflectionTestUtils.setField(service, "statisticsService", statisticsService);

        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
        current = new EduHomeworkDO();
        current.setId(1L);
        current.setTeacherId(7L);
        current.setStatus(1);
        current.setClassId(3L);
        current.setCourseId(4L);
        current.setHomeworkLevel(1);
        current.setPassScore(60);
        current.setScore(100);
        current.setHomeworkName("口语训练一");
        lenient().when(homeworkMapper.selectById(1L)).thenReturn(current);
        EduClassCourseDO relation = new EduClassCourseDO();
        relation.setStatus(0);
        lenient().when(classCourseMapper.selectList(any())).thenReturn(List.of(relation));
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void levelCannotChangeOnceStudentsSubmitted() {
        when(recordMapper.selectCount(any(SFunction.class), eq(1L))).thenReturn(2L);

        assertServiceException(() -> service.updateHomework(request(2, 60)), HOMEWORK_LEVEL_LOCKED_AFTER_SUBMISSION);

        verify(homeworkMapper, never()).updateById(any(EduHomeworkDO.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void levelCanChangeBeforeAnySubmission() {
        when(recordMapper.selectCount(any(SFunction.class), eq(1L))).thenReturn(0L);

        service.updateHomework(request(2, 60));

        verify(homeworkMapper).updateById(argThat((EduHomeworkDO update) -> update.getHomeworkLevel() == 2));
        verifyNoInteractions(changeLogService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void passScoreChangeIsAllowedAndRecorded() {
        when(recordMapper.selectCount(any(SFunction.class), eq(1L))).thenReturn(5L);

        service.updateHomework(request(1, 70));

        verify(homeworkMapper).updateById(argThat((EduHomeworkDO update) -> update.getPassScore() == 70));
        verify(changeLogService).recordPassScoreChange(current, 60, 70, 5L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void draftPassScoreChangeIsNotRecorded() {
        current.setStatus(0);
        when(recordMapper.selectCount(any(SFunction.class), eq(1L))).thenReturn(0L);

        service.updateHomework(request(1, 70));

        verifyNoInteractions(changeLogService);
    }

    @Test
    void archivedHomeworkStatisticsAreReadOnlyInsteadOfMissing() {
        when(aiBusinessAccessService.requireHomeworkViewAccess(1L)).thenReturn(current);
        current.setStatus(3);
        var stat = new EduCourseStatMapper.HomeworkStat();
        stat.setPassCount(1);
        when(courseStatMapper.selectHomeworkStatistics(1L, 60)).thenReturn(stat);
        when(metricPolicy.passScore(current)).thenReturn(60);

        EduHomeworkStatisticsRespVO stats = service.getHomeworkStatistics(1L);

        assertTrue(stats.getReadOnly());
        assertEquals(1, stats.getPassCount());
    }

    @Test
    void archivedClassStatisticsAreReadOnly() {
        when(aiBusinessAccessService.requireHomeworkViewAccess(1L)).thenReturn(current);
        when(archiveAccessService.isInActiveScope(current)).thenReturn(false);

        assertTrue(service.getHomeworkStatistics(1L).getReadOnly());
    }

    @Test
    void activeStatisticsAreNotReadOnly() {
        when(aiBusinessAccessService.requireHomeworkViewAccess(1L)).thenReturn(current);
        when(archiveAccessService.isInActiveScope(current)).thenReturn(true);

        assertFalse(service.getHomeworkStatistics(1L).getReadOnly());
    }

    @Test
    void draftStatisticsStayMissing() {
        when(aiBusinessAccessService.requireHomeworkViewAccess(1L)).thenReturn(current);
        current.setStatus(0);
        assertServiceException(() -> service.getHomeworkStatistics(1L), HOMEWORK_NOT_EXISTS);
    }

    private EduHomeworkSaveReqVO request(int level, int passScore) {
        EduHomeworkSaveReqVO request = new EduHomeworkSaveReqVO();
        request.setId(1L);
        request.setStatus(current.getStatus());
        request.setClassId(3L);
        request.setCourseId(4L);
        request.setHomeworkLevel(level);
        request.setPassScore(passScore);
        request.setScore(100);
        request.setHomeworkName("口语训练一");
        request.setDeadline(LocalDateTime.now().plusDays(3));
        return request;
    }
}
