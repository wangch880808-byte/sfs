package cn.nus.zknu.module.edu.service.classcourse;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAssigneeDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkCopyService;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.edu.service.semester.EduTermFlowPolicy;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EduPublicCourseBackfillServiceTest extends BaseMockitoUnitTest {

    private static final String SEMESTER = "2026-2027 上学期";
    private static final LocalDateTime DEADLINE = LocalDateTime.now().plusDays(5).withNano(0);

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, EduHomeworkDO.class);
        TableInfoHelper.initTableInfo(assistant, EduClassCourseDO.class);
    }

    @InjectMocks
    private EduPublicCourseBackfillService service;
    @Mock
    private EduHomeworkMapper homeworkMapper;
    @Mock
    private EduHomeworkAssigneeMapper assigneeMapper;
    @Mock
    private EduClassCourseMapper classCourseMapper;
    @Mock
    private EduClassMapper classMapper;
    @Mock
    private EduClassStudentMapper classStudentMapper;
    @Mock
    private CapacityCourseApi courseMapper;
    @Mock
    private EduBusinessNotifyService notifyService;
    @Mock
    private EduTermFlowPolicy termFlowPolicy;
    @Mock
    private EduHomeworkCopyService copyService;

    @BeforeEach
    void setUp() {
        lenient().when(classCourseMapper.selectPendingBackfillLinks(2L, SEMESTER))
                .thenReturn(List.of(link(3L)));
        EduClassCourseDO claimed = link(3L); claimed.setClaimTeacherId(7L);
        lenient().when(classCourseMapper.selectClaimRelation(2L, 3L, SEMESTER)).thenReturn(claimed);
        lenient().when(classMapper.selectById(3L)).thenReturn(classes(List.of(3L)).get(0));
        lenient().when(termFlowPolicy.publicCourseBackfillEnabled()).thenReturn(true);
        lenient().when(courseMapper.getCourse(2L)).thenReturn(onlineCourse());
        lenient().when(copyService.uniqueName(anyString(), any())).thenCallRealMethod();
        lenient().when(classStudentMapper.selectListByClassIds(any()))
                .thenReturn(List.of(student(31L), student(32L), student(31L)));
    }

    @Test
    void courseWideTaskIsReplicatedOnceWithAssigneesAndNotices() {
        // 新班级 3；既有班级 1、2 都有教师 7 的「实训一」，只有班级 1 有教师 8 的「补充」
        when(classMapper.selectByIds(any())).thenAnswer(invocation -> classes(invocation.getArgument(0)));
        when(classCourseMapper.selectList(any())).thenReturn(List.of(link(1L), link(2L), link(3L)));
        when(homeworkMapper.selectList(any())).thenReturn(List.of(
                task(100L, 1L, 7L, "实训一"), task(101L, 2L, 7L, "实训一"), task(102L, 1L, 8L, "补充")), List.of());
        doAnswer(invocation -> {
            invocation.<EduHomeworkDO>getArgument(0).setId(500L);
            return 1;
        }).when(homeworkMapper).insert(any(EduHomeworkDO.class));

        EduPublicCourseBackfillService.BackfillResult result = service.backfillForClaim(2L, SEMESTER, 7L, 3L);

        ArgumentCaptor<EduHomeworkDO> taskCaptor = ArgumentCaptor.forClass(EduHomeworkDO.class);
        verify(homeworkMapper, times(1)).insert(taskCaptor.capture());
        EduHomeworkDO created = taskCaptor.getValue();
        assertEquals(3L, created.getClassId());
        assertEquals(101L, created.getSourceHomeworkId());
        assertEquals(EduHomeworkStatusEnum.PUBLISHED, created.getStatus());
        assertEquals(DEADLINE, created.getDeadline());
        assertEquals(7L, created.getTeacherId());
        assertEquals(SEMESTER, created.getSemesterName());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<EduHomeworkAssigneeDO>> assigneeCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(assigneeMapper).insertBatch(assigneeCaptor.capture());
        assertEquals(Set.of(31L, 32L), assigneeCaptor.getValue().stream()
                .map(EduHomeworkAssigneeDO::getStudentId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2, assigneeCaptor.getValue().size());
        assertTrue(assigneeCaptor.getValue().stream().allMatch(item -> item.getHomeworkId().equals(500L)
                && item.getClassId().equals(3L)));
        verify(notifyService, never()).sendToUsers(any(), eq(EduBusinessNotifyService.COURSE_ASSIGNED), any(), any());
        verify(notifyService).sendToUsers(eq(List.of(31L, 32L)), eq(EduBusinessNotifyService.HOMEWORK_CREATED),
                any(), eq("/student/homework/submit/500"));
        assertEquals(1, result.createdTasks());
        assertEquals(2, result.createdAssignees());
        var ordered = inOrder(classCourseMapper, homeworkMapper, assigneeMapper);
        ordered.verify(classCourseMapper).lockPublicCourse(2L);
        ordered.verify(classCourseMapper).selectClaimRelation(2L, 3L, SEMESTER);
        ordered.verify(homeworkMapper).insert(any(EduHomeworkDO.class));
        ordered.verify(assigneeMapper).insertBatch(any());

    }

    @Test
    void resyncDoesNotDuplicateReplicatedTask() {
        when(classMapper.selectByIds(any())).thenAnswer(invocation -> classes(invocation.getArgument(0)));
        when(classCourseMapper.selectList(any())).thenReturn(List.of(link(1L), link(3L)));
        EduHomeworkDO copied = task(500L, 3L, 7L, "实训一");
        copied.setSourceHomeworkId(100L);
        when(homeworkMapper.selectList(any())).thenReturn(List.of(task(100L, 1L, 7L, "实训一")), List.of(copied));

        EduPublicCourseBackfillService.BackfillResult result = service.backfillForClaim(2L, SEMESTER, 7L, 3L);

        verify(homeworkMapper, never()).insert(any(EduHomeworkDO.class));
        verify(assigneeMapper, never()).insertBatch(any());
        verify(notifyService, never()).sendToUsers(any(), eq(EduBusinessNotifyService.HOMEWORK_CREATED), any(), any());
        assertEquals(0, result.createdTasks());
    }

    @Test
    void allNewClassesMeansNothingToReplicate() {
        when(classCourseMapper.selectPendingBackfillLinks(2L, SEMESTER)).thenReturn(List.of(link(1L), link(3L)));
        when(classMapper.selectByIds(any())).thenAnswer(invocation -> classes(invocation.getArgument(0)));

        service.backfill(2L, SEMESTER, Set.of(1L, 3L));

        verify(homeworkMapper, never()).selectList(any());
        verify(homeworkMapper, never()).insert(any(EduHomeworkDO.class));
        verify(notifyService, times(2)).sendToUsers(any(), eq(EduBusinessNotifyService.COURSE_ASSIGNED), any(), any());
    }

    @Test
    void disabledBackfillDoesNothing() {
        when(termFlowPolicy.publicCourseBackfillEnabled()).thenReturn(false);

        assertEquals(EduPublicCourseBackfillService.BackfillResult.EMPTY, service.backfill(2L, SEMESTER, Set.of(3L)));

        verifyNoInteractions(homeworkMapper, assigneeMapper, notifyService, classMapper);
    }

    @Test
    void staleConcurrentCallbackDoesNotDuplicateNoticesOrAssignees() {
        when(classCourseMapper.selectPendingBackfillLinks(2L, SEMESTER)).thenReturn(List.of());

        assertEquals(EduPublicCourseBackfillService.BackfillResult.EMPTY,
                service.backfill(2L, SEMESTER, Set.of(3L)));

        verifyNoInteractions(homeworkMapper, assigneeMapper, classMapper, notifyService);
        verify(classCourseMapper, never()).completeBackfill(any(), any());
    }

    @Test
    void failedAssigneeInsertDoesNotSendTaskNotices() {
        when(classMapper.selectByIds(any())).thenAnswer(invocation -> classes(invocation.getArgument(0)));
        when(classCourseMapper.selectList(any())).thenReturn(List.of(link(1L), link(3L)));
        when(homeworkMapper.selectList(any())).thenReturn(List.of(task(100L, 1L, 7L, "实训一")), List.of());
        when(assigneeMapper.insertBatch(any())).thenThrow(new IllegalStateException("write failed"));

        assertThrows(IllegalStateException.class, () -> service.backfillForClaim(2L, SEMESTER, 7L, 3L));

        verify(classCourseMapper, never()).completeBackfill(any(), any());
        verifyNoInteractions(notifyService);
    }

    @Test
    void sameTeacherNameAndTrimmedSemesterPreventsDuplicateWithoutSourceId() {
        when(classMapper.selectByIds(any())).thenAnswer(invocation -> classes(invocation.getArgument(0)));
        when(classCourseMapper.selectList(any())).thenReturn(List.of(link(1L), link(3L)));
        EduHomeworkDO existing = task(500L, 3L, 7L, "实训一");
        existing.setSemesterName(" " + SEMESTER + " ");
        when(homeworkMapper.selectList(any())).thenReturn(List.of(task(100L, 1L, 7L, "实训一")), List.of(existing));

        var result = service.backfillForClaim(2L, SEMESTER, 7L, 3L);

        assertEquals(0, result.createdTasks());
        verify(homeworkMapper, never()).insert(any(EduHomeworkDO.class));
        verify(assigneeMapper, never()).insertBatch(any());
        verify(classCourseMapper, never()).completeBackfill(any(), any());
    }

    @Test
    void unrelatedTasksKeepOriginalNameSuffixRule() {
        when(classMapper.selectByIds(any())).thenAnswer(invocation -> classes(invocation.getArgument(0)));
        when(classCourseMapper.selectList(any())).thenReturn(List.of(link(1L), link(3L)));
        when(homeworkMapper.selectList(any())).thenReturn(List.of(task(100L, 1L, 7L, "实训一")),
                List.of(task(500L, 3L, 8L, "实训一"), task(501L, 3L, 8L, "实训一(3)")));

        service.backfillForClaim(2L, SEMESTER, 7L, 3L);

        ArgumentCaptor<EduHomeworkDO> captor = ArgumentCaptor.forClass(EduHomeworkDO.class);
        verify(homeworkMapper).insert(captor.capture());
        assertEquals("实训一(4)", captor.getValue().getHomeworkName());
    }

    @Test
    void syncOnlyNotifiesAndClearsMarkerWithoutReadingOrWritingTasks() {
        when(classMapper.selectByIds(any())).thenAnswer(invocation -> classes(invocation.getArgument(0)));
        var result = service.backfill(2L, SEMESTER, Set.of(3L));
        assertEquals(1, result.classCount()); assertEquals(0, result.createdTasks());
        verifyNoInteractions(homeworkMapper, assigneeMapper);
        verify(classCourseMapper).completeBackfill(2L, SEMESTER);
        verify(notifyService).sendToUsers(eq(List.of(31L, 32L)), eq(EduBusinessNotifyService.COURSE_ASSIGNED), any(), any());
    }

    @Test
    void noOtherClaimedClassDoesNotReadTasksOrReplicate() {
        when(classCourseMapper.selectList(any())).thenReturn(List.of(link(3L)));
        assertEquals(0, service.backfillForClaim(2L, SEMESTER, 7L, 3L).createdTasks());
        verifyNoInteractions(homeworkMapper, assigneeMapper, notifyService);
    }

    @Test
    void teacherCoverageQueryExcludesOtherTeachersAndCopiedSources() {
        when(classMapper.selectByIds(any())).thenAnswer(invocation -> classes(invocation.getArgument(0)));
        when(classCourseMapper.selectList(any())).thenReturn(List.of(link(1L), link(3L)));
        EduHomeworkDO copied = task(102L, 1L, 7L, "副本"); copied.setSourceHomeworkId(20L);
        when(homeworkMapper.selectList(any())).thenReturn(List.of(task(100L, 1L, 8L, "他人"), copied));
        assertEquals(0, service.backfillForClaim(2L, SEMESTER, 7L, 3L).createdTasks());
        verify(homeworkMapper, never()).insert(any(EduHomeworkDO.class));
        verify(classCourseMapper).selectList(argThat((com.baomidou.mybatisplus.core.conditions.Wrapper<EduClassCourseDO> w) ->
                w.getSqlSegment().contains("claim_teacher_id =") && w.getSqlSegment().contains("status =")));
        verify(homeworkMapper).selectList(argThat((com.baomidou.mybatisplus.core.conditions.Wrapper<EduHomeworkDO> w) ->
                w.getSqlSegment().contains("teacher_id =") && w.getSqlSegment().contains("source_homework_id IS NULL")));
    }

    @Test
    void targetReleasedOrReclaimedDoesNothing() {
        EduClassCourseDO target = link(3L); target.setClaimTeacherId(8L);
        when(classCourseMapper.selectClaimRelation(2L, 3L, SEMESTER)).thenReturn(target);
        assertEquals(EduPublicCourseBackfillService.BackfillResult.EMPTY,
                service.backfillForClaim(2L, SEMESTER, 7L, 3L));
        verifyNoInteractions(homeworkMapper, assigneeMapper, notifyService);
    }

    @Test
    void batchTargetsDoNotBecomeCoverageForEachOther() {
        when(classMapper.selectByIds(any())).thenAnswer(invocation -> classes(invocation.getArgument(0)));
        when(classCourseMapper.selectList(any())).thenReturn(List.of(link(1L), link(3L), link(4L)));
        var target = link(4L); target.setClaimTeacherId(7L);
        when(classCourseMapper.selectClaimRelation(2L, 4L, SEMESTER)).thenReturn(target);
        when(classMapper.selectById(4L)).thenReturn(classes(List.of(4L)).get(0));
        when(homeworkMapper.selectList(any())).thenReturn(List.of(task(100L, 1L, 7L, "实训一")), List.of(),
                List.of(task(100L, 1L, 7L, "实训一")), List.of());
        doAnswer(invocation -> { invocation.<EduHomeworkDO>getArgument(0).setId(500L); return 1; })
                .when(homeworkMapper).insert(any(EduHomeworkDO.class));
        assertEquals(1, service.backfillForClaim(2L, SEMESTER, 7L, 3L, Set.of(3L, 4L)).createdTasks());
        assertEquals(1, service.backfillForClaim(2L, SEMESTER, 7L, 4L, Set.of(3L, 4L)).createdTasks());
        verify(classMapper, times(2)).selectByIds(Set.of(1L));
        var captor = ArgumentCaptor.forClass(EduHomeworkDO.class);
        verify(homeworkMapper, times(2)).insert(captor.capture());
        assertEquals(List.of(3L, 4L), captor.getAllValues().stream().map(EduHomeworkDO::getClassId).toList());
        assertTrue(captor.getAllValues().stream().allMatch(task -> task.getSourceHomeworkId().equals(100L)));
    }

    @Test
    void batchWithNoPreviousClassesDoesNotReplicateEvenWhenNewClassesHaveTasks() {
        when(classCourseMapper.selectList(any())).thenReturn(List.of(link(3L), link(4L)));
        assertEquals(0, service.backfillForClaim(2L, SEMESTER, 7L, 3L, Set.of(3L, 4L)).createdTasks());
        verifyNoInteractions(homeworkMapper, assigneeMapper, notifyService);
    }

    private List<EduClassDO> classes(Collection<Long> ids) {
        return ids.stream().map(id -> {
            EduClassDO clazz = new EduClassDO();
            clazz.setId(id);
            clazz.setClassName("班级" + id);
            clazz.setStatus(0);
            return clazz;
        }).toList();
    }

    private EduClassCourseDO link(Long classId) {
        EduClassCourseDO link = new EduClassCourseDO();
        link.setClassId(classId);
        link.setCourseId(2L);
        link.setStatus(0);
        return link;
    }

    private EduHomeworkDO task(Long id, Long classId, Long teacherId, String name) {
        EduHomeworkDO task = new EduHomeworkDO();
        task.setId(id);
        task.setClassId(classId);
        task.setCourseId(2L);
        task.setTeacherId(teacherId);
        task.setHomeworkName(name);
        task.setDeadline(DEADLINE);
        task.setStatus(EduHomeworkStatusEnum.PUBLISHED);
        task.setSemesterName(SEMESTER);
        return task;
    }

    private EduClassStudentDO student(Long studentId) {
        EduClassStudentDO relation = new EduClassStudentDO();
        relation.setStudentId(studentId);
        relation.setClassId(3L);
        return relation;
    }

    private CapacityCourseRespDTO onlineCourse() {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(2L);
        course.setCourseName("公共课");
        course.setIsPublicCourse(0);
        course.setPublicAuditStatus(2);
        course.setStatus(0);
        return course;
    }
}
