package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.service.aireview.EduAiBusinessAccessService;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 已发布任务保存后只交给变更通知判断，不再无条件通知全部学生。 */
class EduHomeworkUpdateNotifyTest extends BaseMockitoUnitTest {

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
    private CapacityCourseApi courseMapper;
    @Mock
    private EduClassMapper classMapper;
    @Mock
    private EduClassCourseMapper classCourseMapper;
    @Mock
    private EduAiBusinessAccessService accessService;
    @Mock
    private EduBusinessNotifyService businessNotifyService;
    @Mock
    private EduHomeworkChangeNotifier homeworkChangeNotifier;
    @Mock
    private EduHomeworkRecordMapper recordMapper;
    @Mock
    private EduHomeworkChangeLogService changeLogService;
    @Mock
    private EduHomeworkAssigneeMapper assigneeMapper;
    @Mock
    private EduClassStudentMapper classStudentMapper;

    @BeforeEach
    void setUpCollaborators() {
        EduHomeworkCreationSupport creationSupport = new EduHomeworkCreationSupport();
        ReflectionTestUtils.setField(creationSupport, "eduHomeworkMapper", homeworkMapper);
        ReflectionTestUtils.setField(service, "creationSupport", creationSupport);
        EduHomeworkPublishService publishService = new EduHomeworkPublishService();
        ReflectionTestUtils.setField(publishService, "capacityCourseApi", courseMapper);
        ReflectionTestUtils.setField(publishService, "eduClassStudentMapper", classStudentMapper);
        ReflectionTestUtils.setField(publishService, "eduHomeworkAssigneeMapper", assigneeMapper);
        ReflectionTestUtils.setField(publishService, "businessNotifyService", businessNotifyService);
        ReflectionTestUtils.setField(publishService, "homeworkChangeNotifier", homeworkChangeNotifier);
        ReflectionTestUtils.setField(service, "publishService", publishService);
    }

    @Test
    void savingPublishedTaskDelegatesToChangeNotifier() {
        EduHomeworkDO current = homework(1);
        EduHomeworkDO updated = homework(1);
        updated.setScore(90);
        when(homeworkMapper.selectById(1L)).thenReturn(current, updated);
        allowRelation();

        update(1);

        verify(homeworkChangeNotifier).notifyKeyChanges(current, updated);
        verify(businessNotifyService, never()).sendToUsers(any(), eq(EduBusinessNotifyService.HOMEWORK_UPDATED),
                any(), any());
    }

    @Test
    void publishingDraftStillSendsCreatedNotification() {
        EduHomeworkDO draft = homework(0);
        EduHomeworkDO published = homework(1);
        when(homeworkMapper.selectById(1L)).thenReturn(draft, published);
        allowRelation();

        update(1);

        verify(businessNotifyService).sendToUsers(any(), eq(EduBusinessNotifyService.HOMEWORK_CREATED), any(), any());
        verifyNoInteractions(homeworkChangeNotifier);
    }

    @Test
    void releasedAuthorMayEditOriginalPublicScopeButMovingOrCreatingStillRequiresAssignment() {
        var current = homework(1);
        when(homeworkMapper.selectById(1L)).thenReturn(current);
        allowRelation();
        when(dataScopeService.isPublicCourse(2L)).thenReturn(true);
        doThrow(new cn.nus.zknu.framework.common.exception.ServiceException(
                cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN))
                .when(accessService).validateCourseClassAccess(anyLong(), anyLong());
        update(1);
        verify(homeworkMapper).updateById(any(EduHomeworkDO.class));
        verify(accessService, never()).validateCourseClassAccess(anyLong(), anyLong());
        var request = new EduHomeworkSaveReqVO(); request.setId(1L); request.setCourseId(2L);
        request.setClassId(4L); request.setStatus(1); request.setDeadline(java.time.LocalDateTime.now().plusDays(1)); request.setScore(100); request.setPassScore(60);
        current.setStatus(0);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            org.junit.jupiter.api.Assertions.assertThrows(cn.nus.zknu.framework.common.exception.ServiceException.class,
                    () -> service.updateHomework(request));
            request.setClassId(3L);
            org.junit.jupiter.api.Assertions.assertThrows(cn.nus.zknu.framework.common.exception.ServiceException.class,
                    () -> service.createHomework(request));
        }
        verify(accessService).validateCourseClassAccess(2L, 4L);
        verify(accessService).validateCourseClassAccess(2L, 3L);
    }

    @Test
    void releasedAuthorStillCannotEditAnArchivedClassBeforeRestore() {
        when(homeworkMapper.selectById(1L)).thenReturn(homework(1));
        when(dataScopeService.isPublicCourse(2L)).thenReturn(true);
        var course = new CapacityCourseRespDTO(); course.setId(2L); course.setStatus(0);
        when(courseMapper.getCourse(2L)).thenReturn(course);
        var clazz = new EduClassDO(); clazz.setId(3L); clazz.setStatus(1);
        when(classMapper.selectById(3L)).thenReturn(clazz);
        cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException(() -> update(1),
                cn.nus.zknu.module.edu.enums.ErrorCodeConstants.CLASS_ARCHIVED);
        verify(accessService, never()).validateCourseClassAccess(anyLong(), anyLong());
        verify(homeworkMapper, never()).updateById(any(EduHomeworkDO.class));
    }

    private void update(int status) {
        EduHomeworkSaveReqVO request = new EduHomeworkSaveReqVO();
        request.setId(1L);
        request.setDeadline(java.time.LocalDateTime.now().plusDays(1));
        request.setStatus(status);
        request.setCourseId(2L);
        request.setClassId(3L);
        request.setPassScore(60);
        request.setScore(100);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            service.updateHomework(request);
        }
    }

    private void allowRelation() {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setCourseName("普通话");
        lenient().when(courseMapper.getCourse(2L)).thenReturn(course);
        when(classMapper.selectById(3L)).thenReturn(new EduClassDO());
        EduClassCourseDO binding = new EduClassCourseDO();
        binding.setStatus(0);
        when(classCourseMapper.selectList(any())).thenReturn(List.of(binding));
    }

    private static EduHomeworkDO homework(int status) {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(1L);
        homework.setTeacherId(10L);
        homework.setStatus(status);
        homework.setCourseId(2L);
        homework.setClassId(3L);
        homework.setScore(100);
        homework.setPassScore(60);
        return homework;
    }

}
