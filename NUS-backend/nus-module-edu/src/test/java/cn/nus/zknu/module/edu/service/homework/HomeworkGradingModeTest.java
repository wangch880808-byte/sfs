package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.service.aireview.EduAiBusinessAccessService;
import cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 原「AI 自动批改」（autoGrade=1、aiReviewMode=0）保存时一律归一化为 AI 初评。 */
class HomeworkGradingModeTest extends BaseMockitoUnitTest {

    @Mock
    private EduHomeworkPublishService publishService;
    @Mock
    private EduHomeworkAiCreateService aiCreateService;
    @Mock
    private EduHomeworkStatisticsService statisticsService;

    @Mock
    private cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService dataScopeService;

    @InjectMocks
    private EduHomeworkServiceImpl service;
    @Mock
    private EduHomeworkMapper homeworkMapper;
    @Mock
    private EduClassCourseMapper classCourseMapper;
    @Mock
    private EduClassMapper classMapper;
    @Mock
    private CapacityCourseApi courseMapper;
    @Mock
    private EduAiBusinessAccessService accessService;
    @Mock
    private EduMetricPolicy metricPolicy;
    @Mock
    private EduHomeworkRecordMapper recordMapper;
    @Mock
    private EduHomeworkChangeNotifier homeworkChangeNotifier;
    @Mock
    private EduHomeworkChangeLogService changeLogService;

    @BeforeEach
    void setUpCollaborators() {
        EduHomeworkCreationSupport creationSupport = new EduHomeworkCreationSupport();
        ReflectionTestUtils.setField(creationSupport, "eduHomeworkMapper", homeworkMapper);
        ReflectionTestUtils.setField(creationSupport, "metricPolicy", metricPolicy);
        ReflectionTestUtils.setField(service, "creationSupport", creationSupport);
    }

    @Test
    void normalizeFollowsAutoGrade() {
        assertEquals(1, HomeworkGradingMode.normalizeAiReviewMode(1, 0));
        assertEquals(1, HomeworkGradingMode.normalizeAiReviewMode(1, null));
        assertEquals(1, HomeworkGradingMode.normalizeAiReviewMode(1, 1));
        assertEquals(0, HomeworkGradingMode.normalizeAiReviewMode(0, 1));
        assertEquals(0, HomeworkGradingMode.normalizeAiReviewMode(0, null));
        assertEquals(1, HomeworkGradingMode.normalizeAiReviewMode(null, 1));
        assertNull(HomeworkGradingMode.normalizeAiReviewMode(null, null));
    }

    @Test
    void createNormalizesLegacyAutoGrade() {
        stubCourseClassRelation();
        EduHomeworkSaveReqVO request = request(null);
        request.setStatus(1);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            service.createHomework(request);
        }

        ArgumentCaptor<EduHomeworkDO> inserted = ArgumentCaptor.forClass(EduHomeworkDO.class);
        verify(homeworkMapper).insert(inserted.capture());
        assertEquals(1, inserted.getValue().getAutoGrade());
        assertEquals(1, inserted.getValue().getAiReviewMode());
    }

    @Test
    void updateNormalizesLegacyAutoGrade() {
        stubCourseClassRelation();
        EduHomeworkDO current = new EduHomeworkDO();
        current.setId(1L);
        current.setTeacherId(7L);
        current.setStatus(0);
        current.setCourseId(2L);
        current.setClassId(3L);
        current.setAutoGrade(1);
        current.setAiReviewMode(0);
        when(homeworkMapper.selectById(1L)).thenReturn(current);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            service.updateHomework(request(1L));
        }

        ArgumentCaptor<EduHomeworkDO> updated = ArgumentCaptor.forClass(EduHomeworkDO.class);
        verify(homeworkMapper).updateById(updated.capture());
        assertEquals(1, updated.getValue().getAutoGrade());
        assertEquals(1, updated.getValue().getAiReviewMode());
    }

    private void stubCourseClassRelation() {
        EduClassCourseDO relation = new EduClassCourseDO();
        relation.setCourseId(2L);
        relation.setClassId(3L);
        relation.setStatus(0);
        relation.setSemesterName("2026-2027-1");
        when(classCourseMapper.selectList(any())).thenReturn(List.of(relation));
    }

    private static EduHomeworkSaveReqVO request(Long id) {
        EduHomeworkSaveReqVO request = new EduHomeworkSaveReqVO();
        request.setId(id);
        request.setHomeworkName("教案设计");
        request.setCourseId(2L);
        request.setClassId(3L);
        request.setStatus(0);
        request.setScore(100);
        request.setPassScore(60);
        request.setDeadline(LocalDateTime.now().plusDays(3));
        request.setAutoGrade(1);
        request.setAiReviewMode(0);
        return request;
    }
}
