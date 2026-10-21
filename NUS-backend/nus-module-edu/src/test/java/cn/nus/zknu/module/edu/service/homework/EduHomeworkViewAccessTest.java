package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.controller.admin.homework.EduHomeworkController;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.*;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.*;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.*;
import cn.nus.zknu.module.edu.service.aireview.EduAiBusinessAccessService;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 四个只读入口复用真实课程权限服务，写入口继续沿用布置者规则。 */
class EduHomeworkViewAccessTest extends BaseMockitoUnitTest {
    @Mock private EduHomeworkMapper homeworkMapper;
    @Mock private EduHomeworkRecordMapper recordMapper;
    @Mock private EduHomeworkExtensionMapper extensionMapper;
    @Mock private EduClassCourseMapper classCourseMapper;
    @Mock private CapacityCourseApi courses;
    @Mock private EduRoleApi roles;
    @Mock private EduHomeworkStatisticsService statistics;
    @Mock private EduHomeworkGradingActionService gradingActions;
    @Mock private cn.nus.zknu.framework.security.core.service.SecurityFrameworkService securityFrameworkService;
    private EduHomeworkServiceImpl homeworks;
    private EduHomeworkRecordServiceImpl records;
    private EduHomeworkExtensionServiceImpl extensions;
    private EduHomeworkController controller;
    private EduAiBusinessAccessService access;
    private EduHomeworkDO homework;

    @BeforeEach
    void setUp() {
        var scope = new EduDataScopeService();
        ReflectionTestUtils.setField(scope, "capacityCourseApi", courses);
        ReflectionTestUtils.setField(scope, "eduRoleApi", roles);
        access = new EduAiBusinessAccessService();
        ReflectionTestUtils.setField(access, "dataScopeService", scope);
        ReflectionTestUtils.setField(access, "homeworkMapper", homeworkMapper);
        ReflectionTestUtils.setField(access, "classCourseMapper", classCourseMapper);
        homeworks = new EduHomeworkServiceImpl();
        ReflectionTestUtils.setField(homeworks, "eduHomeworkMapper", homeworkMapper);
        ReflectionTestUtils.setField(homeworks, "aiBusinessAccessService", access);
        ReflectionTestUtils.setField(homeworks, "statisticsService", statistics);
        records = new EduHomeworkRecordServiceImpl();
        ReflectionTestUtils.setField(records, "eduHomeworkMapper", homeworkMapper);
        ReflectionTestUtils.setField(records, "eduHomeworkRecordMapper", recordMapper);
        ReflectionTestUtils.setField(records, "businessAccessService", access);
        ReflectionTestUtils.setField(records, "gradingActionService", gradingActions);
        extensions = new EduHomeworkExtensionServiceImpl();
        ReflectionTestUtils.setField(extensions, "homeworkMapper", homeworkMapper);
        ReflectionTestUtils.setField(extensions, "extensionMapper", extensionMapper);
        ReflectionTestUtils.setField(extensions, "businessAccessService", access);
        controller = new EduHomeworkController();
        ReflectionTestUtils.setField(controller, "eduHomeworkService", homeworks);
        ReflectionTestUtils.setField(controller, "securityFrameworkService", securityFrameworkService);
        lenient().when(securityFrameworkService.hasPermission("edu:homework-record:correct")).thenReturn(true);
        homework = new EduHomeworkDO();
        homework.setId(100L); homework.setTeacherId(7L); homework.setCourseId(2L);
        homework.setClassId(3L); homework.setStatus(1);
        when(homeworkMapper.selectById(100L)).thenReturn(homework);
    }

    @ParameterizedTest
    @CsvSource({"owner,0,true", "owner,1,true", "admin,0,true", "admin,1,true",
            "leader,0,true", "leader,1,true", "member,0,true", "member,1,true",
            "approver,0,true", "approver,1,true", "unrelated,0,true", "unrelated,1,true",
            "student,0,true", "student,1,true", "owner,0,false", "owner,1,false",
            "admin,0,false", "admin,1,false", "leader,0,false", "leader,1,false",
            "member,0,false", "member,1,false", "approver,0,false", "approver,1,false",
            "unrelated,0,false", "unrelated,1,false", "student,0,false", "student,1,false"})
    void fourReadEndpointsAndCanGradeRespectRolesForActiveAndArchivedBindings(String role, int bindingStatus, boolean publicCourse) {
        long userId = switch (role) { case "owner" -> 7L; case "leader" -> 8L; default -> 9L; };
        lenient().when(roles.isCurrentUserAdmin()).thenReturn(role.equals("admin"));
        var course = new CapacityCourseRespDTO();
        course.setId(2L); course.setTeacherId(8L); course.setStatus(0);
        course.setIsPublicCourse(publicCourse ? 0 : 1); course.setPublicAuditStatus(2);
        lenient().when(courses.getCourse(2L)).thenReturn(course);
        lenient().when(courses.isCourseMember(2L, userId)).thenReturn(role.equals("member"));
        lenient().when(courses.isCourseAdmin(userId)).thenReturn(role.equals("approver"));
        var binding = new EduClassCourseDO(); binding.setStatus(bindingStatus);
        lenient().when(classCourseMapper.selectOne(any())).thenReturn(binding);
        var record = new EduHomeworkRecordDO(); record.setId(1L); record.setHomeworkId(100L); record.setStatus(0);
        when(recordMapper.selectById(1L)).thenReturn(record);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(userId);
            if (role.equals("unrelated") || role.equals("student")
                    || publicCourse && role.equals("member") || !publicCourse && role.equals("approver")) {
                assertServiceException(() -> controller.getHomework(100L), FORBIDDEN);
                assertServiceException(() -> controller.getHomeworkStatistics(100L), FORBIDDEN);
                assertServiceException(() -> extensions.listExtensions(100L), FORBIDDEN);
                assertServiceException(() -> records.getGradingQueue(100L), FORBIDDEN);
                verifyNoInteractions(statistics, extensionMapper);
                verify(recordMapper, never()).selectList(any());
            } else {
                assertEquals(role.equals("owner"), controller.getHomework(100L).getData().getCanGrade());
                var expectedStats = new EduHomeworkStatisticsRespVO(); expectedStats.setReadOnly(bindingStatus == 1);
                when(statistics.getHomeworkStatistics(100L, homework)).thenReturn(expectedStats);
                assertSame(expectedStats, controller.getHomeworkStatistics(100L).getData());
                assertTrue(extensions.listExtensions(100L).isEmpty());
                assertEquals(0, records.getGradingQueue(100L).getTotal());
            }
            var correct = new EduHomeworkRecordCorrectReqVO(); correct.setId(1L);
            if (role.equals("owner")) {
                assertServiceException(() -> records.correctRecord(correct), HOMEWORK_RECORD_CORRECT_STATUS_INVALID);
            } else {
                var update = new EduHomeworkSaveReqVO(); update.setId(100L);
                assertServiceException(() -> homeworks.updateHomework(update), HOMEWORK_NOT_EXISTS);
                assertServiceException(() -> homeworks.deleteHomework(100L), HOMEWORK_NOT_EXISTS);
                assertServiceException(() -> homeworks.restoreHomework(100L), HOMEWORK_NOT_EXISTS);
                var extension = new EduHomeworkExtensionSetReqVO(); extension.setHomeworkId(100L);
                extension.setDeadline(LocalDateTime.now().plusDays(1));
                assertServiceException(() -> extensions.setExtensions(extension), HOMEWORK_NOT_EXISTS);
                var saved = new EduHomeworkExtensionDO(); saved.setId(1L); saved.setHomeworkId(100L);
                when(extensionMapper.selectById(1L)).thenReturn(saved);
                assertServiceException(() -> extensions.deleteExtension(1L), HOMEWORK_NOT_EXISTS);
                assertServiceException(() -> records.correctRecord(correct), HOMEWORK_NOT_EXISTS);
                assertServiceException(() -> records.redoRecord(1L, null, null), HOMEWORK_NOT_EXISTS);
                assertServiceException(() -> records.redoRecordBatch(java.util.List.of(1L), null), HOMEWORK_NOT_EXISTS);
                var action = new EduHomeworkGradingActionDO(); action.setRecordId(1L); action.setOperatorId(userId);
                when(gradingActions.getAction(10L)).thenReturn(action);
                assertServiceException(() -> records.undoGrading(10L), HOMEWORK_NOT_EXISTS);
            }
            // 查看不再依赖在用班课；写操作也不会经由查看校验而得到扩大后的权限。
            verifyNoInteractions(classCourseMapper);
            verify(homeworkMapper, never()).updateById(any(EduHomeworkDO.class));
            verify(recordMapper, never()).update(any(), any());
        }
    }

    @Test
    void archivedTaskRemainsViewableButOwnerCannotGrade() {
        homework.setStatus(3);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertFalse(controller.getHomework(100L).getData().getCanGrade());
            var record = new EduHomeworkRecordDO(); record.setId(1L); record.setHomeworkId(100L); record.setStatus(1);
            when(recordMapper.selectById(1L)).thenReturn(record);
            var request = new EduHomeworkRecordCorrectReqVO(); request.setId(1L);
            assertServiceException(() -> records.correctRecord(request), HOMEWORK_ARCHIVED);
        }
    }

    @Test
    void ownerWithoutGradingFunctionPermissionIsReadOnly() {
        when(securityFrameworkService.hasPermission("edu:homework-record:correct")).thenReturn(false);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertFalse(controller.getHomework(100L).getData().getCanGrade());
        }
    }

    @Test
    void noLoginCannotReadAnyEndpointOrGrade() {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(null);
            assertServiceException(() -> controller.getHomework(100L), FORBIDDEN);
            assertServiceException(() -> controller.getHomeworkStatistics(100L), FORBIDDEN);
            assertServiceException(() -> extensions.listExtensions(100L), FORBIDDEN);
            assertServiceException(() -> records.getGradingQueue(100L), FORBIDDEN);
            assertFalse(HomeworkWriteAccess.canGrade(homework));
        }
    }
}
