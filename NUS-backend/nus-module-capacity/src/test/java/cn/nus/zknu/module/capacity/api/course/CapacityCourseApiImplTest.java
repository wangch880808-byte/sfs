package cn.nus.zknu.module.capacity.api.course;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.capacity.dal.dataobject.course.CapacityCourseDO;
import cn.nus.zknu.module.capacity.dal.mysql.course.CapacityCourseMapper;
import cn.nus.zknu.module.capacity.service.course.CapacityCourseMemberService;
import cn.nus.zknu.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CapacityCourseApiImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private CapacityCourseApiImpl courseApi;
    @Mock
    private CapacityCourseMapper courseMapper;
    @Mock
    private CapacityCourseMemberService memberService;
    @Mock
    private PermissionApi permissionApi;

    @Test
    void getCourseCopiesFieldsAndFlagsOnlinePublicCourse() {
        CapacityCourseDO course = new CapacityCourseDO();
        course.setId(1L);
        course.setCourseName("课程");
        course.setTeacherId(20L);
        course.setIndicatorId(3L);
        course.setIsPublicCourse(0);
        course.setPublicAuditStatus(2);
        course.setStatus(0);
        when(courseMapper.selectById(1L)).thenReturn(course);

        CapacityCourseRespDTO dto = courseApi.getCourse(1L);

        assertEquals("课程", dto.getCourseName());
        assertEquals(20L, dto.getTeacherId());
        assertEquals(3L, dto.getIndicatorId());
        assertTrue(dto.isOnlinePublicCourse());
        dto.setStatus(1);
        assertFalse(dto.isOnlinePublicCourse());
        assertNull(courseApi.getCourse(2L));
        assertNull(courseApi.getCourse(null));
        assertTrue(courseApi.getCourseList(List.of()).isEmpty());
    }

    @Test
    void courseAdminIsTheAuditPermissionHolder() {
        when(permissionApi.hasAnyPermissions(10L, "capacity:course:audit")).thenReturn(true);

        assertTrue(courseApi.isCourseAdmin(10L));
        assertFalse(courseApi.isCourseAdmin(11L));
        assertFalse(courseApi.isCourseAdmin(null));
    }

    @Test
    void teamQueriesDelegateToMemberService() {
        when(memberService.getMemberUserIds(1L)).thenReturn(List.of(10L));
        when(memberService.isMember(1L, 10L)).thenReturn(true);

        assertEquals(List.of(10L), courseApi.getCourseMemberUserIds(1L));
        assertTrue(courseApi.isCourseMember(1L, 10L));
    }

}
