package cn.nus.zknu.module.edu.service.dataexchange;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.service.classcourse.EduClassCourseAssistantService;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.service.dept.DeptService;
import cn.nus.zknu.module.system.service.user.UserDeptScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EduPermissionRequestCacheTest extends BaseMockitoUnitTest {
    @InjectMocks private EduDataScopeService dataScope;
    @InjectMocks private EduDeptScopeService deptScope;
    @Mock private CapacityCourseApi courses;
    @Mock private EduRoleApi roles;
    @Mock private UserDeptScopeService userScope;
    @Mock private EduClassMapper classMapper;
    @Mock private DeptService depts;
    @Mock private EduClassCourseMapper bindings;
    @Mock private EduClassCourseAssistantService assistants;

    @AfterEach void tearDown() { RequestContextHolder.resetRequestAttributes(); }
    private void newRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @Test
    void courseAndMembershipLookupsShareOneRequestButSeparateUsersAndRequests() {
        var course = new CapacityCourseRespDTO();
        course.setId(2L);
        course.setTeacherId(99L);
        when(courses.getCourse(2L)).thenReturn(course);
        when(courses.isCourseMember(2L,7L)).thenReturn(true);
        newRequest();
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            for (int i = 0; i < 10; i++) {
                dataScope.validateCourseAccess(2L);
                assertTrue(dataScope.canManageCourse(2L));
            }
            verify(courses).getCourse(2L);
            verify(courses).isCourseMember(2L,7L);
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(8L);
            assertFalse(dataScope.canManageCourse(2L));
            verify(courses).isCourseMember(2L,8L);
            verify(courses).getCourse(2L);
            newRequest();
            assertFalse(dataScope.canManageCourse(2L));
            verify(courses, times(2)).getCourse(2L);
            verify(courses, times(2)).isCourseMember(2L,8L);
        }
    }

    @Test
    void classDepartmentAndCodeCacheEntitiesNotPermissionDecisions() {
        when(userScope.isRestricted()).thenReturn(true);
        var clazz = new EduClassDO();
        clazz.setId(1L);
        clazz.setDeptId(10L);
        var dept = new DeptDO();
        dept.setId(10L);
        when(classMapper.selectById(1L)).thenReturn(clazz);
        when(depts.getDeptByCode("CS")).thenReturn(dept);
        when(userScope.isDeptInScope(10L)).thenReturn(true);
        newRequest();
        for (int i = 0; i < 5; i++) {
            assertTrue(deptScope.isClassInScope(1L));
            assertTrue(deptScope.isDeptCodeInScope("CS"));
            assertTrue(deptScope.isExistingDeptCodeInScope("CS"));
            assertFalse(deptScope.isClassInScope(2L));
        }
        verify(classMapper).selectById(1L);
        verify(classMapper).selectById(2L);
        verify(depts).getDeptByCode("CS");
        when(userScope.isDeptInScope(10L)).thenReturn(false);
        assertFalse(deptScope.isClassInScope(1L));
        newRequest();
        assertFalse(deptScope.isClassInScope(1L));
        verify(classMapper,times(2)).selectById(1L);
    }

    @Test
    void repeatedClassChecksReuseCourseListAndMembership() {
        var binding = new EduClassCourseDO();
        binding.setId(10L);
        binding.setClassId(1L);
        binding.setCourseId(2L);
        when(bindings.selectList(any())).thenReturn(List.of(binding));
        var course = new CapacityCourseRespDTO();
        course.setId(2L);
        course.setTeacherId(99L);
        when(courses.getCourseList(Set.of(2L))).thenReturn(List.of(course));
        when(courses.isCourseMember(2L, 7L)).thenReturn(true);
        newRequest();
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            for (int i = 0; i < 5; i++) dataScope.validateClassAccess(1L);
        }
        verify(courses).getCourseList(Set.of(2L));
        verify(courses).isCourseMember(2L, 7L);
    }

    @Test
    void noRequestReadsCourseMembershipAndClassEachTime() {
        var course = new CapacityCourseRespDTO();
        course.setId(2L);
        course.setTeacherId(99L);
        when(courses.getCourse(2L)).thenReturn(course);
        when(userScope.isRestricted()).thenReturn(true);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            for (int i = 0; i < 3; i++) {
                assertFalse(dataScope.canManageCourse(2L));
                assertFalse(deptScope.isClassInScope(1L));
            }
        }
        verify(courses,times(3)).getCourse(2L);
        verify(courses,times(3)).isCourseMember(2L,7L);
        verify(classMapper,times(3)).selectById(1L);
    }
}
