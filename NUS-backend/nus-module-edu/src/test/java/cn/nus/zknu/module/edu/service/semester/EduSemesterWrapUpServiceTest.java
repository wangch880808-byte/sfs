package cn.nus.zknu.module.edu.service.semester;

import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterOptionRespVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterWrapUpArchiveReqVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterWrapUpArchiveRespVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterWrapUpPreviewRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum;
import cn.nus.zknu.module.edu.service.classcourse.EduClassCourseService;
import cn.nus.zknu.module.edu.service.clazz.EduClassService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;

import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.SEMESTER_WRAP_UP_CLASS_COURSE_INVALID;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.SEMESTER_WRAP_UP_CLASS_INVALID;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.SEMESTER_WRAP_UP_EMPTY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EduSemesterWrapUpServiceTest extends BaseMockitoUnitTest {

    private static final String SEMESTER = "2025-2026 下学期";

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, EduHomeworkDO.class);
        TableInfoHelper.initTableInfo(assistant, EduClassCourseDO.class);
        TableInfoHelper.initTableInfo(assistant, EduHomeworkRecordDO.class);
    }

    @InjectMocks
    private EduSemesterWrapUpService service;
    @Mock
    private EduSemesterOptionService semesterOptionService;
    @Mock
    private EduClassCourseMapper classCourseMapper;
    @Mock
    private EduClassMapper classMapper;
    @Mock
    private CapacityCourseApi courseMapper;
    @Mock
    private EduHomeworkMapper homeworkMapper;
    @Mock
    private EduHomeworkRecordMapper recordMapper;
    @Mock
    private EduClassCourseService classCourseService;
    @Mock
    private EduClassService classService;
    @Mock
    private EduSemesterCacheInvalidator cacheInvalidator;

    @Test
    void previewCountsOpenTasksPendingReviewsAndPublicLinks() {
        semester();
        EduClassCourseDO manual = link(10L, 1L, 5L, 0, "1");
        EduClassCourseDO auto = link(11L, 2L, 6L, 0, EduClassCourseMapper.SYNC_OPERATOR);
        EduClassCourseDO autoArchived = link(12L, 3L, 6L, 1, EduClassCourseMapper.SYNC_OPERATOR);
        // 第一次：本学期全部关联；第二次：候选班级在其他学期的人工关联
        when(classCourseMapper.selectList(any())).thenReturn(List.of(manual, auto, autoArchived), List.of());
        when(classMapper.selectByIds(any())).thenReturn(List.of(clazz(1L), clazz(2L)));
        when(courseMapper.getCourseList(any())).thenReturn(List.of(course(5L), course(6L)));
        LocalDateTime future = LocalDateTime.now().plusDays(3);
        when(homeworkMapper.selectList(any())).thenReturn(List.of(
                homework(100L, 1L, 5L, EduHomeworkStatusEnum.PUBLISHED, future),
                homework(101L, 1L, 5L, EduHomeworkStatusEnum.PUBLISHED, null),
                homework(102L, 1L, 5L, EduHomeworkStatusEnum.PUBLISHED, LocalDateTime.now().minusDays(1)),
                homework(103L, 2L, 6L, EduHomeworkStatusEnum.CLOSED, null),
                // 班级 9 不在本学期在用关联里，不计入
                homework(104L, 9L, 5L, EduHomeworkStatusEnum.PUBLISHED, future)));
        when(recordMapper.selectList(any())).thenReturn(List.of(record(102L), record(102L), record(103L)));

        EduSemesterWrapUpPreviewRespVO preview = service.preview(SEMESTER);

        assertEquals(SEMESTER, preview.getSemesterName());
        assertEquals(2, preview.getActiveClassCourseCount());
        assertEquals(2, preview.getOpenTaskCount());
        assertEquals(3, preview.getPendingReviewCount());
        assertEquals(2, preview.getPublicCourseLinkCount());
        assertEquals(1, preview.getActivePublicCourseLinkCount());
        EduSemesterWrapUpPreviewRespVO.ClassCourse first = preview.getClassCourses().get(0);
        assertEquals(10L, first.getClassCourseId());
        assertFalse(first.getAutoCreated());
        assertEquals(2, first.getOpenTaskCount());
        assertEquals(2, first.getPendingReviewCount());
        assertTrue(preview.getClassCourses().get(1).getAutoCreated());
        // 只有人工关联的班级 1 是候选，自动关联的班级 2 不参与
        assertEquals(1, preview.getClasses().size());
        assertEquals(1L, preview.getClasses().get(0).getClassId());
    }

    @Test
    void claimedSystemLinkIsTeachingAndCountsAsCandidateClass() {
        semester();
        EduClassCourseDO claimed = link(11L, 2L, 6L, 0, EduClassCourseMapper.SYNC_OPERATOR);
        claimed.setClaimTeacherId(7L);
        when(classCourseMapper.selectList(any())).thenReturn(List.of(claimed), List.of());
        when(classMapper.selectByIds(any())).thenReturn(List.of(clazz(2L)));
        when(courseMapper.getCourseList(any())).thenReturn(List.of(course(6L)));
        var preview = service.preview(SEMESTER);
        assertEquals(0, preview.getPublicCourseLinkCount());
        assertFalse(preview.getClassCourses().get(0).getAutoCreated());
        assertEquals(2L, preview.getClasses().get(0).getClassId());
    }

    @Test
    void classWithManualLinkInOtherSemesterIsNotCandidate() {
        EduClassCourseDO elsewhere = link(30L, 1L, 7L, 0, "1");
        when(classMapper.selectByIds(any())).thenReturn(List.of(clazz(1L)));
        when(classCourseMapper.selectList(any())).thenReturn(List.of(elsewhere));

        assertTrue(service.candidateClassIds(SEMESTER, List.of(link(10L, 1L, 5L, 0, "1"))).isEmpty());
    }

    @Test
    void claimedSystemLinkInOtherSemesterPreventsWholeClassArchive() {
        var elsewhere = link(30L, 1L, 7L, 0, EduClassCourseMapper.SYNC_OPERATOR);
        elsewhere.setClaimTeacherId(7L);
        when(classMapper.selectByIds(any())).thenReturn(List.of(clazz(1L)));
        when(classCourseMapper.selectList(any())).thenReturn(List.of(elsewhere));
        assertTrue(service.candidateClassIds(SEMESTER, List.of(link(10L, 1L, 5L, 0, "1"))).isEmpty());
        verify(classCourseMapper).selectList(argThat((com.baomidou.mybatisplus.core.conditions.Wrapper<EduClassCourseDO> w) ->
                w.getSqlSegment().contains("claim_teacher_id IS NOT NULL")
                && w.getSqlSegment().contains(" OR ") && w.getSqlSegment().contains("semester_name")));
    }

    @Test
    void archiveUsesExistingArchiveServices() {
        semester();
        when(classCourseMapper.selectList(any())).thenReturn(
                List.of(link(10L, 1L, 5L, 0, "1"), link(11L, 2L, 6L, 0, EduClassCourseMapper.SYNC_OPERATOR)),
                List.of());
        when(classMapper.selectByIds(any())).thenReturn(List.of(clazz(1L)));

        EduSemesterWrapUpArchiveRespVO resp = service.archive(archiveReq(List.of(10L, 11L), List.of(1L)));

        verify(cacheInvalidator).evictOptions();
        verify(classCourseService).unbindClassCourse(10L);
        verify(classCourseService).unbindClassCourse(11L);
        verify(classService).deleteClassList(List.of(1L));
        assertEquals(2, resp.getArchivedClassCourseCount());
        assertEquals(1, resp.getArchivedClassCount());
    }

    @Test
    void archiveRejectsLinkOfAnotherSemester() {
        semester();
        when(classCourseMapper.selectList(any())).thenReturn(List.of(link(10L, 1L, 5L, 0, "1")));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.archive(archiveReq(List.of(99L), List.of())));

        assertEquals(SEMESTER_WRAP_UP_CLASS_COURSE_INVALID.getCode(), ex.getCode());
        verifyNoInteractions(classCourseService, classService, cacheInvalidator);
    }

    @Test
    void archiveRejectsClassThatIsNotCandidate() {
        semester();
        when(classCourseMapper.selectList(any())).thenReturn(
                List.of(link(11L, 2L, 6L, 0, EduClassCourseMapper.SYNC_OPERATOR)));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.archive(archiveReq(List.of(), List.of(2L))));

        assertEquals(SEMESTER_WRAP_UP_CLASS_INVALID.getCode(), ex.getCode());
        verifyNoInteractions(classCourseService, classService, cacheInvalidator);
    }

    @Test
    void archiveRequiresSelection() {
        semester();

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.archive(archiveReq(List.of(), List.of())));

        assertEquals(SEMESTER_WRAP_UP_EMPTY.getCode(), ex.getCode());
    }

    @Test
    void archiveEvictsOnlyAfterCommitAndRollbackKeepsCache() {
        semester();
        when(classCourseMapper.selectList(any())).thenReturn(
                List.of(link(11L, 2L, 6L, 0, EduClassCourseMapper.SYNC_OPERATOR)));
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.archive(archiveReq(List.of(11L), List.of()));
            verifyNoInteractions(cacheInvalidator);
            var callbacks = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, callbacks.size());
            callbacks.forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(cacheInvalidator).evictOptions();
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
        clearInvocations(cacheInvalidator);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.archive(archiveReq(List.of(11L), List.of()));
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(callback -> callback.afterCompletion(
                            org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(cacheInvalidator);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void semester() {
        EduSemesterOptionRespVO option = new EduSemesterOptionRespVO();
        option.setSemesterName(SEMESTER);
        option.setCurrent(false);
        when(semesterOptionService.resolve(SEMESTER)).thenReturn(option);
    }

    private EduSemesterWrapUpArchiveReqVO archiveReq(List<Long> classCourseIds, List<Long> classIds) {
        EduSemesterWrapUpArchiveReqVO req = new EduSemesterWrapUpArchiveReqVO();
        req.setSemesterName(SEMESTER);
        req.setClassCourseIds(classCourseIds);
        req.setClassIds(classIds);
        return req;
    }

    private EduClassCourseDO link(Long id, Long classId, Long courseId, int status, String creator) {
        EduClassCourseDO link = new EduClassCourseDO();
        link.setId(id);
        link.setClassId(classId);
        link.setCourseId(courseId);
        link.setStatus(status);
        link.setSemesterName(SEMESTER);
        link.setCreator(creator);
        return link;
    }

    private EduClassDO clazz(Long id) {
        EduClassDO clazz = new EduClassDO();
        clazz.setId(id);
        clazz.setClassName("班级" + id);
        clazz.setStatus(0);
        return clazz;
    }

    private CapacityCourseRespDTO course(Long id) {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(id);
        course.setCourseName("课程" + id);
        return course;
    }

    private EduHomeworkDO homework(Long id, Long classId, Long courseId, int status, LocalDateTime deadline) {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(id);
        homework.setClassId(classId);
        homework.setCourseId(courseId);
        homework.setStatus(status);
        homework.setDeadline(deadline);
        return homework;
    }

    private EduHomeworkRecordDO record(Long homeworkId) {
        EduHomeworkRecordDO record = new EduHomeworkRecordDO();
        record.setHomeworkId(homeworkId);
        return record;
    }
}
