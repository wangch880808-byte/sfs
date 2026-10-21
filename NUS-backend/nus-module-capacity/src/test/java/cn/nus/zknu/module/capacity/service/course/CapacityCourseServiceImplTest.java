package cn.nus.zknu.module.capacity.service.course;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.PublicCourseChangedEvent;
import cn.nus.zknu.module.capacity.controller.admin.course.vo.CapacityCourseAuditReqVO;
import cn.nus.zknu.module.capacity.controller.admin.course.vo.CapacityCourseSaveReqVO;
import cn.nus.zknu.module.capacity.dal.dataobject.course.CapacityCourseDO;
import cn.nus.zknu.module.capacity.dal.dataobject.indicator.CapacityIndicatorDO;
import cn.nus.zknu.module.capacity.dal.mysql.course.CapacityCourseMapper;
import cn.nus.zknu.module.capacity.dal.mysql.indicator.CapacityIndicatorMapper;
import cn.nus.zknu.module.infra.api.file.FileApi;
import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;
import cn.nus.zknu.module.system.api.notify.NotifyMessageSendApi;
import cn.nus.zknu.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.system.api.permission.PermissionApi;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import cn.nus.zknu.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.capacity.enums.ErrorCodeConstants.COURSE_IMAGE_URL_INVALID;
import static cn.nus.zknu.module.capacity.enums.ErrorCodeConstants.COURSE_NOT_EXISTS;
import static cn.nus.zknu.module.capacity.enums.ErrorCodeConstants.COURSE_PUBLIC_AUDIT_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CapacityCourseServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private CapacityCourseServiceImpl courseService;
    @Mock
    private CapacityCourseMapper courseMapper;
    @Mock
    private CapacityIndicatorMapper indicatorMapper;
    @Mock
    private AdminUserApi adminUserApi;
    @Mock
    private FileApi fileApi;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private EduRoleApi eduRoleApi;
    @Mock
    private NotifyMessageSendApi notifyMessageSendApi;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private CapacityCourseMemberService memberService;

    private static final String AUDIT = "capacity:course:audit";
    private static final String OLD_COVER = "https://school.example/admin-api/infra/file/1/get/capacity/course/20260926/a_1.png";
    private static final String DEFAULT_COVER = "/static/imgs/course-covers/21d8e7bf2a3e43a975033ded3a8c6cdb.jpg";

    @Test
    void createCourse_nonAdminUsesLoginUserAsTeacher() throws Exception {
        long loginUserId = 10L;
        CapacityCourseSaveReqVO request = new CapacityCourseSaveReqVO();
        request.setIndicatorId(100L);
        request.setTeacherId(20L);
        CapacityIndicatorDO indicator = new CapacityIndicatorDO();
        indicator.setIndicatorName("indicator");
        when(indicatorMapper.selectById(100L)).thenReturn(indicator);
        when(permissionApi.hasAnyPermissions(loginUserId, AUDIT)).thenReturn(false);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(loginUserId);
            courseService.createCourse(request, null);
        }

        ArgumentCaptor<CapacityCourseDO> captor = ArgumentCaptor.forClass(CapacityCourseDO.class);
        verify(courseMapper).insert(captor.capture());
        assertEquals(loginUserId, captor.getValue().getTeacherId());
        verify(adminUserApi).validateUser(loginUserId);
    }

    @Test
    void createCourse_removesLoginUserAndTeacherFromAssistants() throws Exception {
        long loginUserId = 10L;
        CapacityCourseSaveReqVO request = new CapacityCourseSaveReqVO();
        request.setIndicatorId(100L);
        request.setTeacherId(20L);
        request.setTeachAssistantIds("10,20,30,30");
        CapacityIndicatorDO indicator = new CapacityIndicatorDO();
        indicator.setIndicatorName("indicator");
        when(indicatorMapper.selectById(100L)).thenReturn(indicator);
        when(permissionApi.hasAnyPermissions(loginUserId, AUDIT)).thenReturn(true);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(loginUserId);
            courseService.createCourse(request, null);
        }

        ArgumentCaptor<CapacityCourseDO> captor = ArgumentCaptor.forClass(CapacityCourseDO.class);
        verify(courseMapper).insert(captor.capture());
        assertEquals("30", captor.getValue().getTeachAssistantIds());
        verify(adminUserApi).validateUserList(java.util.List.of(30L));
        // 课程团队写入关联表（旧列同步写入，便于回退）
        verify(memberService).replaceMembers(captor.getValue().getId(), java.util.List.of(30L));
    }

    @Test
    void updateCourse_writesTeamToMemberTableAndMirrorsCsv() throws Exception {
        when(courseMapper.selectById(1L)).thenReturn(ownCourse(null));
        when(indicatorMapper.selectById(100L)).thenReturn(new CapacityIndicatorDO());
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(false);
        CapacityCourseSaveReqVO request = updateRequest(null);
        request.setTeachAssistantIds("31,10,32,31");

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            courseService.updateCourse(request, null);
        }

        ArgumentCaptor<CapacityCourseDO> captor = ArgumentCaptor.forClass(CapacityCourseDO.class);
        verify(courseMapper).updateById(captor.capture());
        assertEquals("31,32", captor.getValue().getTeachAssistantIds());
        verify(memberService).replaceMembers(1L, java.util.List.of(31L, 32L));
    }

    @Test
    void updateCourse_removingWholeTeamClearsTableAndCsv() throws Exception {
        when(courseMapper.selectById(1L)).thenReturn(ownCourse(null));
        when(indicatorMapper.selectById(100L)).thenReturn(new CapacityIndicatorDO());
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(false);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            courseService.updateCourse(updateRequest(null), null);
        }

        ArgumentCaptor<CapacityCourseDO> captor = ArgumentCaptor.forClass(CapacityCourseDO.class);
        verify(courseMapper).updateById(captor.capture());
        // 空串而不是 null：null 字段不会被 updateById 写回，旧列会残留已移除的成员
        assertEquals("", captor.getValue().getTeachAssistantIds());
        verify(memberService).replaceMembers(1L, java.util.List.of());
    }

    @Test
    void updateCourse_otherTeacherIsRejected() {
        long loginUserId = 10L;
        CapacityCourseDO course = new CapacityCourseDO();
        course.setId(1L);
        course.setTeacherId(20L);
        when(courseMapper.selectById(1L)).thenReturn(course);
        when(permissionApi.hasAnyPermissions(loginUserId, AUDIT)).thenReturn(false);
        CapacityCourseSaveReqVO request = new CapacityCourseSaveReqVO();
        request.setId(1L);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(loginUserId);
            assertServiceException(() -> courseService.updateCourse(request, null), COURSE_NOT_EXISTS);
        }

        verify(courseMapper, never()).updateById(any(CapacityCourseDO.class));
    }

    @Test
    void deleteCourseList_withOtherTeachersCourseIsRejected() {
        long loginUserId = 10L;
        CapacityCourseDO ownCourse = new CapacityCourseDO();
        ownCourse.setId(1L);
        ownCourse.setTeacherId(loginUserId);
        CapacityCourseDO otherCourse = new CapacityCourseDO();
        otherCourse.setId(2L);
        otherCourse.setTeacherId(20L);
        when(courseMapper.selectByIds(any())).thenReturn(java.util.List.of(ownCourse, otherCourse));
        when(permissionApi.hasAnyPermissions(loginUserId, AUDIT)).thenReturn(false);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(loginUserId);
            assertServiceException(() -> courseService.deleteCourseList(java.util.List.of(1L, 2L)), COURSE_NOT_EXISTS);
        }

        verify(courseMapper, never()).deleteByIds(any());
        verify(fileApi, never()).deleteFile(any());
    }

    @Test
    void createCourse_ignoresClientSuppliedImageUrl() throws Exception {
        CapacityCourseSaveReqVO request = new CapacityCourseSaveReqVO();
        request.setIndicatorId(100L);
        request.setCourseImageUrl("https://school.example/admin-api/infra/file/1/get/edu/homework-submission/1/a.png");
        when(indicatorMapper.selectById(100L)).thenReturn(new CapacityIndicatorDO());
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(false);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            courseService.createCourse(request, null);
        }

        ArgumentCaptor<CapacityCourseDO> captor = ArgumentCaptor.forClass(CapacityCourseDO.class);
        verify(courseMapper).insert(captor.capture());
        assertNull(captor.getValue().getCourseImageUrl());
    }

    @Test
    void createCourse_keepsBuiltInDefaultCover() throws Exception {
        CapacityCourseSaveReqVO request = new CapacityCourseSaveReqVO();
        request.setIndicatorId(100L);
        request.setCourseImageUrl(DEFAULT_COVER);
        when(indicatorMapper.selectById(100L)).thenReturn(new CapacityIndicatorDO());
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(false);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            courseService.createCourse(request, null);
        }

        ArgumentCaptor<CapacityCourseDO> captor = ArgumentCaptor.forClass(CapacityCourseDO.class);
        verify(courseMapper).insert(captor.capture());
        assertEquals(DEFAULT_COVER, captor.getValue().getCourseImageUrl());
    }

    @Test
    void updateCourse_foreignImageUrlIsRejected() {
        CapacityCourseDO course = ownCourse(OLD_COVER);
        when(courseMapper.selectById(1L)).thenReturn(course);
        when(indicatorMapper.selectById(100L)).thenReturn(new CapacityIndicatorDO());
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(false);
        CapacityCourseSaveReqVO request = updateRequest(
                "https://school.example/admin-api/infra/file/1/get/edu/homework-submission/1/a.png");

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            assertServiceException(() -> courseService.updateCourse(request, null), COURSE_IMAGE_URL_INVALID);
        }

        verify(courseMapper, never()).updateById(any(CapacityCourseDO.class));
        verify(fileApi, never()).deleteFile(any());
    }

    @Test
    void updateCourse_clearingCoverPersistsEmptyUrlAndDeletesOnlyCourseImage() throws Exception {
        when(courseMapper.selectById(1L)).thenReturn(ownCourse(OLD_COVER));
        when(indicatorMapper.selectById(100L)).thenReturn(new CapacityIndicatorDO());
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(false);
        when(fileApi.getFile(OLD_COVER)).thenReturn(platformFile("capacity/course/20260926/a_1.png"));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            courseService.updateCourse(updateRequest(null), null);
        }

        ArgumentCaptor<CapacityCourseDO> captor = ArgumentCaptor.forClass(CapacityCourseDO.class);
        verify(courseMapper).updateById(captor.capture());
        assertEquals("", captor.getValue().getCourseImageUrl());
        verify(fileApi).deleteFile(OLD_COVER);
    }

    @Test
    void updateCourse_keepingOldCoverDoesNotDeleteIt() throws Exception {
        when(courseMapper.selectById(1L)).thenReturn(ownCourse(OLD_COVER));
        when(indicatorMapper.selectById(100L)).thenReturn(new CapacityIndicatorDO());
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(false);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            courseService.updateCourse(updateRequest(OLD_COVER), null);
        }

        verify(courseMapper).updateById(argThat((CapacityCourseDO update) -> OLD_COVER.equals(update.getCourseImageUrl())));
        verify(fileApi, never()).deleteFile(any());
    }

    @Test
    void deleteCourse_archivesWithoutDeletingForeignImage() {
        String foreignUrl = "https://school.example/admin-api/infra/file/1/get/edu/homework-submission/1/a.png";
        when(courseMapper.selectById(1L)).thenReturn(ownCourse(foreignUrl));
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(false);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            courseService.deleteCourse(1L);
        }

        verify(fileApi, never()).deleteFile(any());
        verify(courseMapper).updateById(argThat((CapacityCourseDO update) ->
                update.getId().equals(1L) && update.getStatus().equals(1)));
    }

    @Test
    void deleteCourse_archivesWithoutDeletingUnregisteredImage() {
        when(courseMapper.selectById(1L)).thenReturn(ownCourse("http://169.254.169.254/latest"));
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(false);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            courseService.deleteCourse(1L);
        }

        verify(fileApi, never()).deleteFile(any());
        verify(courseMapper).updateById(argThat((CapacityCourseDO update) ->
                update.getId().equals(1L) && update.getStatus().equals(1)));
    }

    @Test
    void restoreCourseEnablesArchivedCourse() {
        when(courseMapper.selectById(1L)).thenReturn(ownCourse(OLD_COVER));
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(false);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            courseService.restoreCourse(1L);
        }

        verify(courseMapper).updateById(argThat((CapacityCourseDO update) ->
                update.getId().equals(1L) && update.getStatus().equals(0)));
        verify(fileApi, never()).deleteFile(any());
    }

    @Test
    void createCourse_publicCourseNotifiesApprovers() throws Exception {
        CapacityCourseSaveReqVO request = new CapacityCourseSaveReqVO();
        request.setIndicatorId(100L);
        request.setIsPublicCourse(0);
        request.setCourseName("普通话训练");
        CapacityIndicatorDO indicator = new CapacityIndicatorDO();
        indicator.setIndicatorName("语言能力");
        when(indicatorMapper.selectById(100L)).thenReturn(indicator);
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(false);
        when(permissionApi.getEnableUserIdListByPermission(AUDIT)).thenReturn(java.util.Set.of(1L, 2L));
        AdminUserRespDTO teacher = new AdminUserRespDTO();
        teacher.setNickname("张老师");
        when(adminUserApi.getUser(10L)).thenReturn(teacher);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            courseService.createCourse(request, null);
        }

        ArgumentCaptor<NotifySendSingleToUserReqDTO> captor =
                ArgumentCaptor.forClass(NotifySendSingleToUserReqDTO.class);
        verify(notifyMessageSendApi, times(2)).sendSingleMessageToAdmin(captor.capture());
        assertEquals(java.util.Set.of(1L, 2L), captor.getAllValues().stream()
                .map(NotifySendSingleToUserReqDTO::getUserId).collect(java.util.stream.Collectors.toSet()));
        assertEquals("edu.admin.public-course.pending", captor.getValue().getTemplateCode());
        assertEquals("/capacity/course_manager/course",
                captor.getValue().getTemplateParams().get("targetUrl"));
    }

    @Test
    void auditPublicCourse_notifiesTeacherAndIgnoresNotifyFailure() {
        CapacityCourseDO course = new CapacityCourseDO();
        course.setId(1L);
        course.setTeacherId(20L);
        course.setCourseName("普通话训练");
        course.setIsPublicCourse(0);
        course.setPublicAuditStatus(1);
        when(courseMapper.selectById(1L)).thenReturn(course);
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(true);
        when(notifyMessageSendApi.sendSingleMessageToAdmin(any()))
                .thenThrow(new IllegalStateException("notify failed"));
        CapacityCourseAuditReqVO request = new CapacityCourseAuditReqVO();
        request.setId(1L);
        request.setApproved(true);
        request.setRemark("同意");

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            courseService.auditPublicCourse(request);
        }

        verify(courseMapper).updateById(course);
        verify(notifyMessageSendApi).sendSingleMessageToAdmin(argThat(req ->
                req.getUserId().equals(20L)
                        && req.getTemplateCode().equals("edu.teacher.public-course.audit-result")
                        && req.getTemplateParams().get("targetUrl").equals("/capacity/course/detail/1")));
    }

    @Test
    void approvedPublicCoursePublishesSyncEvent() {
        CapacityCourseDO course = new CapacityCourseDO();
        course.setId(1L);
        course.setTeacherId(20L);
        course.setIsPublicCourse(0);
        course.setPublicAuditStatus(1);
        course.setStatus(0);
        when(courseMapper.selectById(1L)).thenReturn(course);
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(true);
        CapacityCourseAuditReqVO request = new CapacityCourseAuditReqVO();
        request.setId(1L);
        request.setApproved(true);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            courseService.auditPublicCourse(request);
        }

        verify(eventPublisher).publishEvent(new PublicCourseChangedEvent(1L));
    }

    @Test
    void auditPublicCourse_withoutAuditPermissionIsForbidden() {
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(false);
        CapacityCourseAuditReqVO request = new CapacityCourseAuditReqVO();
        request.setId(1L);
        request.setApproved(true);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            assertServiceException(() -> courseService.auditPublicCourse(request), COURSE_PUBLIC_AUDIT_FORBIDDEN);
        }

        verify(courseMapper, never()).updateById(any(CapacityCourseDO.class));
        // 角色不再参与审批判断
        verify(permissionApi, never()).hasAnyRoles(any(), any(String[].class));
    }

    @Test
    void createCourse_byAuditPermissionHolderIsApprovedWithoutNotice() throws Exception {
        CapacityCourseSaveReqVO request = new CapacityCourseSaveReqVO();
        request.setIndicatorId(100L);
        request.setIsPublicCourse(0);
        CapacityIndicatorDO indicator = new CapacityIndicatorDO();
        when(indicatorMapper.selectById(100L)).thenReturn(indicator);
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(true);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            courseService.createCourse(request, null);
        }

        ArgumentCaptor<CapacityCourseDO> captor = ArgumentCaptor.forClass(CapacityCourseDO.class);
        verify(courseMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getPublicAuditStatus());
        verify(permissionApi, never()).getEnableUserIdListByPermission(any());
    }

    @Test
    void getCourse_onlinePublicCourseVisibleToConfiguredTeacherRole() {
        CapacityCourseDO course = new CapacityCourseDO();
        course.setId(1L);
        course.setTeacherId(20L);
        course.setIsPublicCourse(0);
        course.setPublicAuditStatus(2);
        course.setStatus(0);
        when(courseMapper.selectById(1L)).thenReturn(course);
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(false);
        when(eduRoleApi.isTeacher(10L)).thenReturn(true);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            assertEquals(course, courseService.getCourse(1L));
        }
    }

    @Test
    void getCourse_teamMemberIsReadFromMemberTable() {
        CapacityCourseDO course = new CapacityCourseDO();
        course.setId(1L);
        course.setTeacherId(20L);
        course.setIsPublicCourse(1);
        course.setTeachAssistantIds("99");
        when(courseMapper.selectById(1L)).thenReturn(course);
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(false);
        when(memberService.isMember(1L, 10L)).thenReturn(true);
        when(memberService.getMemberUserIdsMap(java.util.List.of(1L)))
                .thenReturn(java.util.Map.of(1L, java.util.List.of(10L, 11L)));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            CapacityCourseDO result = courseService.getCourse(1L);
            // 返回给前端的 teachAssistantIds 取自关联表，不是旧列
            assertEquals("10,11", result.getTeachAssistantIds());
        }
    }

    @Test
    void getCourse_staleCsvWithoutMemberRowIsRejected() {
        CapacityCourseDO course = new CapacityCourseDO();
        course.setId(1L);
        course.setTeacherId(20L);
        course.setIsPublicCourse(1);
        course.setTeachAssistantIds("10");
        when(courseMapper.selectById(1L)).thenReturn(course);
        when(permissionApi.hasAnyPermissions(10L, AUDIT)).thenReturn(false);
        when(memberService.isMember(1L, 10L)).thenReturn(false);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            assertServiceException(() -> courseService.getCourse(1L), COURSE_NOT_EXISTS);
        }
    }

    private static CapacityCourseDO ownCourse(String courseImageUrl) {
        CapacityCourseDO course = new CapacityCourseDO();
        course.setId(1L);
        course.setTeacherId(10L);
        course.setCourseImageUrl(courseImageUrl);
        return course;
    }

    private static CapacityCourseSaveReqVO updateRequest(String courseImageUrl) {
        CapacityCourseSaveReqVO request = new CapacityCourseSaveReqVO();
        request.setId(1L);
        request.setIndicatorId(100L);
        request.setCourseImageUrl(courseImageUrl);
        return request;
    }

    private static FileContentRespDTO platformFile(String path) {
        FileContentRespDTO file = new FileContentRespDTO();
        file.setPath(path);
        return file;
    }
}
