package cn.nus.zknu.module.edu.controller.admin.classcourse;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.classcourse.vo.EduClassCourseRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.service.classcourse.EduClassCourseAssistantService;
import cn.nus.zknu.module.edu.service.classcourse.EduClassCourseService;
import cn.nus.zknu.module.edu.service.clazz.EduClassService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EduClassCourseControllerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduClassCourseController controller;
    @Mock
    private EduClassCourseService eduClassCourseService;
    @Mock
    private EduClassService eduClassService;
    @Mock
    private CapacityCourseApi capacityCourseLiteMapper;
    @Mock
    private EduClassCourseAssistantService assistantService;

    @Mock
    private cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService dataScopeService;
    @Mock
    private cn.nus.zknu.module.system.api.user.AdminUserApi adminUserApi;

    @Test
    void listByCourse_fillsClassAndCourseNamesWithOneBatchLookupEach() {
        when(eduClassCourseService.listByCourse(5L, false))
                .thenReturn(List.of(relation(11L, 1L, 5L), relation(12L, 2L, 5L)));
        when(eduClassService.getClassList(eq(Set.of(1L, 2L))))
                .thenReturn(List.of(clazz(1L, "软件工程 2025 级 1 班"), clazz(2L, "软件工程 2025 级 2 班")));
        when(capacityCourseLiteMapper.getCourseList(eq(Set.of(5L))))
                .thenReturn(List.of(course(5L, "Web 前端开发实训")));
        when(assistantService.getTeacherIdsMap(eq(Set.of(11L, 12L)))).thenReturn(Map.of(11L, List.of(31L, 30L)));

        List<EduClassCourseRespVO> rows = controller.listByCourse(5L, false).getData();

        // 助教取自 edu_class_course_assistant，不用旧列（relation 里旧列写的是 99）
        assertEquals("31,30", rows.get(0).getAssistantTeacherIds());
        assertNull(rows.get(1).getAssistantTeacherIds());
        verify(assistantService, times(1)).getTeacherIdsMap(anyCollection());

        assertEquals(2, rows.size());
        assertEquals("软件工程 2025 级 1 班", rows.get(0).getClassName());
        assertEquals("软件工程 2025 级 2 班", rows.get(1).getClassName());
        assertEquals("Web 前端开发实训", rows.get(0).getCourseName());
        assertEquals("Web 前端开发实训", rows.get(1).getCourseName());
        verify(eduClassService, times(1)).getClassList(anyCollection());
        verify(eduClassService, never()).getClass(anyLong());
        verify(capacityCourseLiteMapper, times(1)).getCourseList(anyCollection());
    }

    @Test
    void listByClass_mapsEachRelationToItsOwnCourseAndLeavesMissingCourseEmpty() {
        when(eduClassCourseService.listByClass(1L, true))
                .thenReturn(List.of(relation(21L, 1L, 5L), relation(22L, 1L, 6L), relation(23L, 1L, 7L)));
        when(eduClassService.getClassList(eq(Set.of(1L))))
                .thenReturn(List.of(clazz(1L, "软件工程 2025 级 1 班")));
        when(capacityCourseLiteMapper.getCourseList(eq(Set.of(5L, 6L, 7L))))
                .thenReturn(List.of(course(5L, "Web 前端开发实训"), course(6L, "教育心理学")));

        List<EduClassCourseRespVO> rows = controller.listByClass(1L, true).getData();

        assertEquals(List.of(5L, 6L, 7L), rows.stream().map(EduClassCourseRespVO::getCourseId).toList());
        assertEquals("Web 前端开发实训", rows.get(0).getCourseName());
        assertEquals("教育心理学", rows.get(1).getCourseName());
        assertNull(rows.get(2).getCourseName());
        assertTrue(rows.stream().allMatch(row -> "软件工程 2025 级 1 班".equals(row.getClassName())));
    }

    @Test
    void listByCourse_emptyRelationsSkipNameLookups() {
        when(eduClassCourseService.listByCourse(5L, false)).thenReturn(List.of());

        assertTrue(controller.listByCourse(5L, false).getData().isEmpty());
        verify(eduClassService, never()).getClassList(anyCollection());
        verify(capacityCourseLiteMapper, never()).getCourseList(anyCollection());
    }

    @Test
    void publicRowsBatchTeacherNamesAndUseRowClaimForOperate() {
        var own = relation(11L, 1L, 5L); own.setClaimTeacherId(7L); own.setStatus(1);
        var other = relation(12L, 2L, 5L); other.setClaimTeacherId(8L);
        var course = course(5L, "公共课"); course.setIsPublicCourse(0);
        when(eduClassCourseService.listByCourse(5L, true)).thenReturn(List.of(own, other));
        when(capacityCourseLiteMapper.getCourseList(Set.of(5L))).thenReturn(List.of(course));
        var teacher = new cn.nus.zknu.module.system.api.user.dto.AdminUserRespDTO();
        teacher.setId(8L); teacher.setNickname("张");
        when(adminUserApi.getUserList(Set.of(7L, 8L))).thenReturn(List.of(teacher));
        when(dataScopeService.canReleasePublicClaim(7L)).thenReturn(true);
        try (var security = org.mockito.Mockito.mockStatic(cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.class)) {
            security.when(cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            var rows = controller.listByCourse(5L, true).getData();
            assertTrue(rows.get(0).getCanOperate());
            assertTrue(rows.get(0).getCanRelease());
            org.junit.jupiter.api.Assertions.assertFalse(rows.get(1).getCanRelease());
            org.junit.jupiter.api.Assertions.assertFalse(rows.get(1).getCanOperate());
            assertEquals(8L, rows.get(1).getClaimTeacherId()); assertEquals("张", rows.get(1).getClaimTeacherName());
            when(dataScopeService.isGlobalAdmin()).thenReturn(true);
            when(dataScopeService.canReleasePublicClaim(8L)).thenReturn(true);
            var adminRows = controller.listByCourse(5L, true).getData();
            assertTrue(adminRows.stream().allMatch(EduClassCourseRespVO::getCanOperate));
            assertTrue(adminRows.stream().allMatch(EduClassCourseRespVO::getCanRelease));
        }
        verify(adminUserApi, times(2)).getUserList(anyCollection());
        verify(adminUserApi, never()).getUser(anyLong());
    }

    @Test
    void listByClassIncludesReleaseFlagForPublicClaimedRowOnly() {
        var claimed = relation(11L, 1L, 5L); claimed.setClaimTeacherId(8L);
        var empty = relation(12L, 1L, 5L);
        var exclusive = relation(13L, 1L, 6L); exclusive.setClaimTeacherId(8L);
        var publicCourse = course(5L, "公共课"); publicCourse.setIsPublicCourse(0);
        when(eduClassCourseService.listByClass(1L, true)).thenReturn(List.of(claimed, empty, exclusive));
        when(capacityCourseLiteMapper.getCourseList(Set.of(5L, 6L)))
                .thenReturn(List.of(publicCourse, course(6L, "专属课")));
        when(dataScopeService.canReleasePublicClaim(8L)).thenReturn(true);
        var rows = controller.listByClass(1L, true).getData();
        assertEquals(List.of(true, false, false), rows.stream().map(EduClassCourseRespVO::getCanRelease).toList());
    }

    @Test
    void claimEndpointsDelegateAndHaveBindPermission() throws Exception {
        var request = new cn.nus.zknu.module.edu.controller.admin.classcourse.vo.EduClassCourseClaimReqVO();
        assertTrue(controller.claimClassCourse(request).getData());
        assertTrue(controller.releaseClassCourse(11L).getData());
        controller.claimOptions(5L, null);
        verify(eduClassCourseService).claimClassCourse(request);
        verify(eduClassCourseService).releaseClassCourse(11L);
        verify(eduClassCourseService).claimOptions(5L, null);
        for (var method : EduClassCourseController.class.getMethods()) {
            if (Set.of("claimClassCourse", "releaseClassCourse", "claimOptions").contains(method.getName())) {
                assertEquals("@ss.hasPermission('edu:class-course:bind')", method.getAnnotation(
                        org.springframework.security.access.prepost.PreAuthorize.class).value());
            }
        }
    }

    private EduClassCourseDO relation(Long id, Long classId, Long courseId) {
        EduClassCourseDO relation = new EduClassCourseDO();
        relation.setId(id);
        relation.setClassId(classId);
        relation.setCourseId(courseId);
        relation.setStatus(0);
        relation.setAssistantTeacherIds("99");
        return relation;
    }

    private EduClassDO clazz(Long id, String name) {
        EduClassDO clazz = new EduClassDO();
        clazz.setId(id);
        clazz.setClassName(name);
        return clazz;
    }

    private CapacityCourseRespDTO course(Long id, String name) {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(id);
        course.setCourseName(name);
        return course;
    }

}
