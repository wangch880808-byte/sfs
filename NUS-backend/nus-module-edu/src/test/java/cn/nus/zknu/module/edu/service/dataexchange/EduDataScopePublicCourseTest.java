package cn.nus.zknu.module.edu.service.dataexchange;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static org.mockito.Mockito.*;

class EduDataScopePublicCourseTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduDataScopeService service;
    @Mock
    private EduRoleApi eduRoleApi;
    @Mock
    private EduClassCourseMapper classCourseMapper;
    @Mock
    private CapacityCourseApi courseMapper;

    @Mock
    private cn.nus.zknu.module.edu.service.classcourse.EduClassCourseAssistantService assistantService;

    @org.junit.jupiter.api.BeforeAll
    static void initTable() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"),
                cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO.class);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}, claim={1}, status={2}")
    @org.junit.jupiter.params.provider.MethodSource("publicClassCases")
    void publicClassMatrix(String role, Long claimant, int status) {
        var course = onlineCourse();
        var relation = new cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO();
        relation.setId(5L); relation.setClassId(3L); relation.setCourseId(2L);
        relation.setClaimTeacherId(claimant); relation.setStatus(status);
        lenient().when(eduRoleApi.isCurrentUserAdmin()).thenReturn(role.equals("admin"));
        lenient().when(courseMapper.isCourseAdmin(7L)).thenReturn(role.equals("approver"));
        if (role.equals("owner")) course.setTeacherId(7L);
        lenient().when(courseMapper.isCourseMember(2L, 7L)).thenReturn(role.equals("team"));
        lenient().when(courseMapper.getCourse(2L)).thenReturn(course);
        lenient().when(courseMapper.getCourseList(anyCollection())).thenReturn(java.util.List.of(course));
        lenient().when(assistantService.isAssistant(java.util.List.of(5L), 7L)).thenReturn(role.equals("assistant"));
        lenient().when(classCourseMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenAnswer(invocation -> {
                    var wrapper = invocation.<com.baomidou.mybatisplus.core.conditions.Wrapper<?>>getArgument(0);
                    if (wrapper.getSqlSegment().contains("claim_teacher_id")) {
                        return status == 0 && java.util.Objects.equals(claimant, 7L)
                                ? java.util.List.of(relation) : java.util.List.of();
                    }
                    return java.util.List.of(relation);
                });
        boolean full = java.util.Set.of("admin", "approver", "owner").contains(role);
        boolean mine = status == 0 && java.util.Objects.equals(claimant, 7L);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            org.junit.jupiter.api.Assertions.assertEquals(full, service.canViewAllPublic(2L));
            org.junit.jupiter.api.Assertions.assertEquals(full || mine, service.canViewPublicClass(2L, 3L));
            org.junit.jupiter.api.Assertions.assertEquals(role.equals("admin") || mine, service.canAssignToPublicClass(2L, 3L));
            boolean canRoster = role.equals("admin") || role.equals("assistant") || status == 0 && (full || mine);
            if (canRoster) service.validateClassAccess(3L);
            else assertServiceException(() -> service.validateClassAccess(3L), FORBIDDEN);
            if (role.equals("admin") || role.equals("assistant") || mine) service.validateClassWriteAccess(3L);
            else assertServiceException(() -> service.validateClassWriteAccess(3L), FORBIDDEN);
            if (full || mine || role.equals("assistant")) service.validateClassCourseAccess(3L, 2L);
            else assertServiceException(() -> service.validateClassCourseAccess(3L, 2L), FORBIDDEN);
        }
        if (role.equals("team")) verify(courseMapper, never()).isCourseMember(anyLong(), anyLong());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void mixedClassUsesExclusiveTeamForWritingInsteadOfPublicReadAll(boolean team) {
        var publicCourse = onlineCourse(); publicCourse.setTeacherId(7L);
        var exclusive = onlineCourse(); exclusive.setId(4L); exclusive.setIsPublicCourse(1); exclusive.setTeacherId(8L);
        var pubLink = new cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO();
        pubLink.setId(5L); pubLink.setClassId(3L); pubLink.setCourseId(2L); pubLink.setStatus(0);
        var exclusiveLink = new cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO();
        exclusiveLink.setId(6L); exclusiveLink.setClassId(3L); exclusiveLink.setCourseId(4L); exclusiveLink.setStatus(0);
        when(classCourseMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenAnswer(inv -> inv.<com.baomidou.mybatisplus.core.conditions.Wrapper<?>>getArgument(0).getSqlSegment()
                        .contains("claim_teacher_id") ? java.util.List.of() : java.util.List.of(pubLink, exclusiveLink));
        when(courseMapper.getCourseList(java.util.Set.of(2L, 4L))).thenReturn(java.util.List.of(publicCourse, exclusive));
        when(courseMapper.isCourseMember(4L, 7L)).thenReturn(team);
        try (var login = mockStatic(SecurityFrameworkUtils.class)) {
            login.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            if (team) service.validateClassWriteAccess(3L);
            else assertServiceException(() -> service.validateClassWriteAccess(3L), FORBIDDEN);
        }
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> publicClassCases() {
        return java.util.stream.Stream.of("teacher", "team", "owner", "admin", "approver", "assistant")
                .flatMap(role -> java.util.Arrays.<Long>asList(null, 7L, 8L).stream()
                        .flatMap(claim -> java.util.stream.IntStream.range(0, 2).mapToObj(status ->
                                org.junit.jupiter.params.provider.Arguments.of(role, claim, status))));
    }

    @Test
    void teacherCanAccessOnlinePublicCourse() {
        CapacityCourseRespDTO course = onlineCourse();
        when(courseMapper.getCourse(2L)).thenReturn(course);
        when(eduRoleApi.isCurrentUserAdmin()).thenReturn(false);
        when(eduRoleApi.isCurrentUserTeacher()).thenReturn(true);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            service.validateCourseAccess(2L);
        }
    }

    @Test
    void teacherCannotAccessPrivateCourseWithoutAssignment() {
        CapacityCourseRespDTO course = onlineCourse();
        course.setIsPublicCourse(1);
        when(courseMapper.getCourse(2L)).thenReturn(course);
        when(eduRoleApi.isCurrentUserAdmin()).thenReturn(false);
        when(eduRoleApi.isCurrentUserTeacher()).thenReturn(true);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertServiceException(() -> service.validateCourseAccess(2L), FORBIDDEN);
        }
    }

    @Test
    void configuredAdminSkipsCourseLookup() {
        when(eduRoleApi.isCurrentUserAdmin()).thenReturn(true);

        service.validateCourseAccess(2L);

        verifyNoInteractions(courseMapper);
    }

    private CapacityCourseRespDTO onlineCourse() {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(2L);
        course.setTeacherId(99L);
        course.setIsPublicCourse(0);
        course.setPublicAuditStatus(2);
        course.setStatus(0);
        return course;
    }
}
