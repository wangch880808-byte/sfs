package cn.nus.zknu.module.edu.service.classcourse;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.service.semester.EduSemesterService;
import cn.nus.zknu.module.edu.service.semester.EduTermFlowPolicy;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EduPublicCourseSyncServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduPublicCourseSyncService service;
    @Mock
    private EduSemesterService semesterService;
    @Mock
    private CapacityCourseApi courseMapper;
    @Mock
    private EduClassMapper classMapper;
    @Mock
    private EduClassCourseMapper classCourseMapper;
    @Mock
    private EduPublicCourseBackfillService backfillService;
    @Mock
    private EduTermFlowPolicy termFlowPolicy;

    @Test
    void approvedCourseOnlyBindsNormalClassesAndIsIdempotent() {
        semester();
        CapacityCourseRespDTO course = onlineCourse();
        when(courseMapper.getCourse(2L)).thenReturn(course);
        EduClassDO clazz = new EduClassDO();
        clazz.setId(1L);
        clazz.setStatus(0);
        when(classMapper.selectList(any(Wrapper.class))).thenReturn(List.of(clazz));
        EduClassCourseDO existing = relation(1L, 2L, 0);
        when(classCourseMapper.selectList(any())).thenReturn(List.of(), List.of(existing));

        service.syncCourse(2L);
        service.syncCourse(2L);

        verify(classCourseMapper, times(1)).insert(argThat((EduClassCourseDO relation) ->
                relation.getClassId().equals(1L) && relation.getCourseId().equals(2L)
                        && Boolean.TRUE.equals(relation.getBackfillPending())
                        && relation.getSemesterName().equals("2026-2027 上学期")
                        && relation.getSemesterStartDate().equals(LocalDate.of(2026, 8, 1))));
        verify(classCourseMapper, times(2)).selectList(any());
        verify(classCourseMapper, never()).selectOne(any());
        // 只有第一次真正新建了关联，补发也只触发一次
        verify(backfillService, times(1)).backfill(2L, "2026-2027 上学期", Set.of(1L));
        verify(classCourseMapper, times(2)).restoreSyncArchivedLinks(2L, "2026-2027 上学期");
    }

    @Test
    void archivedRelationIsNeverRestored() {
        semester();
        when(courseMapper.getCourse(2L)).thenReturn(onlineCourse());
        EduClassDO clazz = new EduClassDO();
        clazz.setId(1L);
        when(classMapper.selectList(any(Wrapper.class))).thenReturn(List.of(clazz));
        EduClassCourseDO archived = relation(1L, 2L, 1);
        archived.setSemesterName(" 2026-2027 上学期 ");
        when(classCourseMapper.selectList(any())).thenReturn(List.of(archived));

        service.syncCourse(2L);

        verify(classCourseMapper, never()).insert(any(EduClassCourseDO.class));
        verify(classCourseMapper, never()).updateById(any(EduClassCourseDO.class));
        verify(classCourseMapper, times(1)).selectList(argThat(wrapper ->
                wrapper.getSqlSegment().contains("TRIM(semester_name)")));
    }

    @Test
    void noSemesterSkipsQueries() {
        EduSemesterRespVO unset = new EduSemesterRespVO();
        unset.setSemesterName("未设置");
        when(semesterService.getCurrentSemester()).thenReturn(unset);

        service.syncAll();

        verifyNoInteractions(courseMapper, classMapper, classCourseMapper);
    }

    @Test
    void newClassBindsOnlineCourses() {
        semester();
        EduClassDO clazz = new EduClassDO();
        clazz.setId(1L);
        clazz.setStatus(0);
        when(classMapper.selectById(1L)).thenReturn(clazz);
        CapacityCourseRespDTO secondCourse = onlineCourse();
        secondCourse.setId(4L);
        when(courseMapper.getOnlinePublicCourseList()).thenReturn(List.of(onlineCourse(), secondCourse));
        when(classCourseMapper.selectList(any())).thenReturn(List.of(relation(1L, 2L, 1)));

        service.syncClass(1L);

        verify(classCourseMapper).insert(argThat((EduClassCourseDO relation) ->
                relation.getClassId().equals(1L) && relation.getCourseId().equals(4L)));
        verify(classCourseMapper, times(1)).selectList(any());
    }

    @Test
    void syncAllLoadsExistingPairsOnce() {
        semester();
        EduClassDO first = new EduClassDO();
        first.setId(1L);
        EduClassDO second = new EduClassDO();
        second.setId(3L);
        when(classMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));
        when(courseMapper.getOnlinePublicCourseList()).thenReturn(List.of(onlineCourse()));
        when(classCourseMapper.selectList(any())).thenReturn(List.of(relation(1L, 2L, 1)));

        service.syncAll();

        verify(classCourseMapper, times(1)).selectList(any());
        verify(classCourseMapper, times(1)).insert(argThat((EduClassCourseDO relation) ->
                relation.getClassId().equals(3L) && relation.getCourseId().equals(2L)));
    }

    @Test
    void offlineCourseArchivesOnlyIdleAutoLinksAndBindsNothing() {
        CapacityCourseRespDTO course = onlineCourse();
        course.setPublicAuditStatus(3);
        when(courseMapper.getCourse(2L)).thenReturn(course);
        when(classCourseMapper.archiveIdleAutoLinksOfOfflineCourses(2L)).thenReturn(3);

        service.syncCourse(2L);

        verify(classCourseMapper).archiveIdleAutoLinksOfOfflineCourses(2L);
        verify(classCourseMapper, never()).insert(any(EduClassCourseDO.class));
        verify(classCourseMapper, never()).restoreSyncArchivedLinks(any(), any());
        verifyNoInteractions(classMapper, backfillService, semesterService);
    }

    @Test
    void syncAllArchivesPreviousSemesterLinksWhenEnabled() {
        semester();
        when(termFlowPolicy.archivePreviousSemesterPublicLinks()).thenReturn(true);

        service.syncAll();

        verify(classCourseMapper).archiveIdleAutoLinksOutsideSemester("2026-2027 上学期");
        verify(classCourseMapper).archiveIdleAutoLinksOfOfflineCourses(null);
        verify(classCourseMapper).restoreSyncArchivedLinks(null, "2026-2027 上学期");
        verifyNoInteractions(backfillService);
    }

    @Test
    void syncAllKeepsPreviousSemesterLinksWhenDisabled() {
        semester();
        when(termFlowPolicy.archivePreviousSemesterPublicLinks()).thenReturn(false);

        service.syncAll();

        verify(classCourseMapper, never()).archiveIdleAutoLinksOutsideSemester(any());
        verify(classCourseMapper).archiveIdleAutoLinksOfOfflineCourses(null);
    }

    @Test
    void syncAllBackfillsNewLinksGroupedByCourse() {
        semester();
        EduClassDO first = new EduClassDO();
        first.setId(1L);
        EduClassDO second = new EduClassDO();
        second.setId(3L);
        when(classMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));
        CapacityCourseRespDTO other = onlineCourse();
        other.setId(4L);
        when(courseMapper.getOnlinePublicCourseList()).thenReturn(List.of(onlineCourse(), other));
        when(classCourseMapper.selectList(any())).thenReturn(List.of(relation(1L, 2L, 0)));

        service.syncAll();

        verify(backfillService).backfill(2L, "2026-2027 上学期", Set.of(3L));
        verify(backfillService).backfill(4L, "2026-2027 上学期", Set.of(1L, 3L));
    }

    @Test
    void backfillFailureDoesNotBreakSync() {
        semester();
        EduClassDO clazz = new EduClassDO();
        clazz.setId(1L);
        clazz.setStatus(0);
        when(classMapper.selectById(1L)).thenReturn(clazz);
        when(courseMapper.getOnlinePublicCourseList()).thenReturn(List.of(onlineCourse()));
        when(classCourseMapper.selectList(any())).thenReturn(List.of());
        when(backfillService.backfill(any(), any(), any())).thenThrow(new IllegalStateException("boom"));

        service.syncClass(1L);

        verify(classCourseMapper).insert(any(EduClassCourseDO.class));
    }

    @Test
    void unfinishedBatchIsRetriedBeforeAddingAnotherClass() {
        semester();
        EduClassDO clazz = new EduClassDO();
        clazz.setId(3L);
        clazz.setStatus(0);
        when(classMapper.selectById(3L)).thenReturn(clazz);
        when(courseMapper.getOnlinePublicCourseList()).thenReturn(List.of(onlineCourse()));
        when(classCourseMapper.selectPendingBackfillLinks(2L, "2026-2027 上学期"))
                .thenReturn(List.of(relation(1L, 2L, 0)));

        service.syncClass(3L);

        var ordered = inOrder(classCourseMapper, backfillService);
        ordered.verify(classCourseMapper).lockPublicCourse(2L);
        ordered.verify(classCourseMapper).selectPendingBackfillLinks(2L, "2026-2027 上学期");
        ordered.verify(backfillService).backfill(2L, "2026-2027 上学期", Set.of(1L));
        verify(classCourseMapper, never()).insert(any(EduClassCourseDO.class));
    }

    @Test
    void failedCommittedBackfillIsRetriedOnNextSync() {
        semester();
        when(courseMapper.getCourse(2L)).thenReturn(onlineCourse());
        EduClassDO clazz = new EduClassDO();
        clazz.setId(1L);
        clazz.setStatus(0);
        when(classMapper.selectList(any(Wrapper.class))).thenReturn(List.of(clazz));
        EduClassCourseDO pending = relation(1L, 2L, 0);
        pending.setBackfillPending(true);
        when(classCourseMapper.selectList(any())).thenReturn(List.of(), List.of(pending));
        when(classCourseMapper.selectPendingBackfillLinks(2L, "2026-2027 上学期"))
                .thenReturn(List.of(), List.of(pending));
        when(backfillService.backfill(any(), any(), any()))
                .thenThrow(new IllegalStateException("write failed"))
                .thenReturn(EduPublicCourseBackfillService.BackfillResult.EMPTY);

        assertDoesNotThrow(() -> service.syncCourse(2L));
        assertDoesNotThrow(() -> service.syncCourse(2L));

        verify(classCourseMapper, times(1)).insert(any(EduClassCourseDO.class));
        verify(backfillService, times(2)).backfill(2L, "2026-2027 上学期", Set.of(1L));
    }

    @Test
    void dailySyncRetriesPendingBackfillFromPreviousSemester() {
        semester();
        EduClassCourseDO old = relation(1L, 2L, 0);
        old.setSemesterName("2025-2026 下学期");
        old.setBackfillPending(true);
        when(classCourseMapper.selectBackfillBacklog()).thenReturn(List.of(old));

        service.syncAll();

        verify(backfillService).backfill(2L, "2025-2026 下学期", Set.of(1L));
    }

    @Test
    void multiCourseSyncLocksCoursesInAscendingOrderBeforeReadingLinks() {
        semester();
        EduClassDO clazz = new EduClassDO();
        clazz.setId(1L);
        clazz.setStatus(0);
        when(classMapper.selectById(1L)).thenReturn(clazz);
        CapacityCourseRespDTO other = onlineCourse();
        other.setId(4L);
        when(courseMapper.getOnlinePublicCourseList()).thenReturn(List.of(other, onlineCourse()));

        service.syncClass(1L);

        var ordered = inOrder(classCourseMapper);
        ordered.verify(classCourseMapper).lockPublicCourse(2L);
        ordered.verify(classCourseMapper).lockPublicCourse(4L);
        ordered.verify(classCourseMapper).selectList(argThat(wrapper -> wrapper.getSqlSegment().contains("FOR UPDATE")));
    }

    private void semester() {
        EduSemesterRespVO semester = new EduSemesterRespVO();
        semester.setSemesterName("2026-2027 上学期");
        semester.setSemesterStartDate(LocalDate.of(2026, 8, 1));
        semester.setSemesterEndDate(LocalDate.of(2027, 1, 31));
        when(semesterService.getCurrentSemester()).thenReturn(semester);
    }

    private CapacityCourseRespDTO onlineCourse() {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(2L);
        course.setIsPublicCourse(0);
        course.setPublicAuditStatus(2);
        course.setStatus(0);
        return course;
    }

    private EduClassCourseDO relation(Long classId, Long courseId, Integer status) {
        EduClassCourseDO relation = new EduClassCourseDO();
        relation.setClassId(classId);
        relation.setCourseId(courseId);
        relation.setStatus(status);
        return relation;
    }
}
