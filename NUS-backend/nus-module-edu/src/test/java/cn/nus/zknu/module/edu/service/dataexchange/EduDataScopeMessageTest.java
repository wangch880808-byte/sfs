package cn.nus.zknu.module.edu.service.dataexchange;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.service.classcourse.EduClassCourseAssistantService;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import java.util.List;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 班课与课程访问：课程团队读 capacity_course_member，班课助教读 edu_class_course_assistant，不再读逗号串。 */
class EduDataScopeMessageTest extends BaseMockitoUnitTest {
    @InjectMocks private EduDataScopeService service;
    @Mock private EduRoleApi eduRoleApi;
    @Mock private EduClassCourseMapper classCourseMapper;
    @Mock private CapacityCourseApi courseApi;
    @Mock private EduClassCourseAssistantService assistantService;

    @Test
    void teacherOutsideCourseAndClassTeamIsDenied() {
        when(classCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(binding("7,8")));
        when(assistantService.isAssistant(List.of(11L), 9L)).thenReturn(false);
        when(courseApi.getCourse(2L)).thenReturn(course(6L));
        when(courseApi.isCourseMember(2L, 9L)).thenReturn(false);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(9L);
            assertServiceException(() -> service.validateClassCourseAccess(1L, 2L), FORBIDDEN);
        }
    }

    @Test
    void classAssistantCanAccessConversation() {
        when(classCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(binding(null)));
        when(assistantService.isAssistant(List.of(11L), 7L)).thenReturn(true);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            service.validateClassCourseAccess(1L, 2L);
        }
        verifyNoInteractions(courseApi);
    }

    @Test
    void courseTeamMemberFromMemberTableCanAccess() {
        when(classCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(binding(null)));
        when(courseApi.getCourse(2L)).thenReturn(course(6L));
        when(courseApi.isCourseMember(2L, 5L)).thenReturn(true);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(5L);
            assertDoesNotThrow(() -> service.validateClassCourseAccess(1L, 2L));
            assertDoesNotThrow(() -> service.validateCourseAccess(2L));
        }
    }

    @Test
    void staleAssistantCsvWithoutTableRowGrantsNothing() {
        // 旧列里有 9，但关联表里没有：以关联表为准
        when(classCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(binding("9")));
        when(assistantService.isAssistant(List.of(11L), 9L)).thenReturn(false);
        when(courseApi.getCourseList(any())).thenReturn(List.of(course(6L)));
        when(courseApi.isCourseMember(2L, 9L)).thenReturn(false);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(9L);
            assertServiceException(() -> service.validateClassAccess(1L), FORBIDDEN);
        }
    }

    @Test
    void classAccessAllowsCourseTeamMemberOfAnyBoundCourse() {
        when(classCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(binding(null)));
        when(courseApi.getCourseList(any())).thenReturn(List.of(course(6L)));
        when(courseApi.isCourseMember(2L, 5L)).thenReturn(true);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(5L);
            assertDoesNotThrow(() -> service.validateClassAccess(1L));
        }
    }

    private static EduClassCourseDO binding(String legacyCsv) {
        EduClassCourseDO binding = new EduClassCourseDO();
        binding.setId(11L);
        binding.setClassId(1L);
        binding.setCourseId(2L);
        binding.setStatus(0);
        binding.setAssistantTeacherIds(legacyCsv);
        return binding;
    }

    private static CapacityCourseRespDTO course(Long teacherId) {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(2L);
        course.setTeacherId(teacherId);
        course.setIsPublicCourse(1);
        course.setStatus(0);
        return course;
    }
}
