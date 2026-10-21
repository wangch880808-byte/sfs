package cn.nus.zknu.module.edu.service.student;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentHomeworkDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentHomeworkPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentHomeworkRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentUnlockStatusRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAssigneeDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentHomeworkMapper;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkRecordService;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkArchiveAccessService;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkExtensionService;
import cn.nus.zknu.module.edu.service.homework.HomeworkAttachmentLimits;
import cn.nus.zknu.module.edu.service.homework.HomeworkAttachmentService;
import cn.nus.zknu.module.infra.api.file.FileApi;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_RECORD_DEADLINE_EXCEEDED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_HOMEWORK_NOT_ASSIGNED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_HOMEWORK_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_COURSE_LEVEL_LOCKED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

class StudentHomeworkServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private StudentHomeworkServiceImpl homeworkService;
    @Mock
    private EduHomeworkAssigneeMapper homeworkAssigneeMapper;
    @Mock
    private EduHomeworkMapper homeworkMapper;
    @Mock
    private EduHomeworkRecordMapper homeworkRecordMapper;
    @Mock
    private EduHomeworkRecordService homeworkRecordService;
    @Mock
    private CapacityCourseApi capacityCourseLiteMapper;
    @Mock
    private StudentHomeworkMapper studentHomeworkMapper;
    @Mock
    private HomeworkAttachmentService homeworkAttachmentService;
    @Mock
    private FileApi fileApi;
    @Mock
    private EduHomeworkArchiveAccessService archiveAccessService;
    @Mock
    private StudentCourseServiceImpl studentCourseService;
    @Mock
    private EduHomeworkExtensionService homeworkExtensionService;

    @BeforeEach
    void allowActiveHomework() {
        lenient().when(archiveAccessService.canView(any())).thenReturn(true);
        lenient().when(archiveAccessService.canSubmit(any())).thenReturn(true);
        lenient().when(homeworkAttachmentService.getLimits()).thenReturn(new HomeworkAttachmentLimits(10, 100, 300));
        lenient().when(archiveAccessService.canViewHistory(any(), anyBoolean())).thenReturn(true);
    }

    @Test
    void getHomeworkList_historyScopeAsksForPastTasks() {
        Page<AppStudentHomeworkRespVO> mapperPage = new Page<>(1, 20, 0);
        when(studentHomeworkMapper.selectHomeworkPage(any(), eq(10L), isNull(), isNull(), eq(true)))
                .thenReturn(mapperPage);
        AppStudentHomeworkPageReqVO request = new AppStudentHomeworkPageReqVO();
        request.setPageNo(1);
        request.setPageSize(20);
        request.setScope(AppStudentHomeworkPageReqVO.SCOPE_HISTORY);

        assertEquals(0L, homeworkService.getHomeworkList(10L, request).getTotal());
    }

    @Test
    void archivedClassHomeworkStaysViewableButReadOnly() {
        EduHomeworkDO homework = publishedHomework(LocalDateTime.now().minusDays(30));
        homework.setCourseId(20L);
        homework.setHomeworkLevel(3);
        when(homeworkMapper.selectById(1L)).thenReturn(homework);
        when(homeworkAssigneeMapper.selectByHomeworkIdAndStudentId(1L, 10L)).thenReturn(new EduHomeworkAssigneeDO());
        EduHomeworkRecordDO graded = record(2, null);
        graded.setScore(88);
        when(homeworkRecordMapper.selectOne(any())).thenReturn(graded);
        when(archiveAccessService.canView(homework)).thenReturn(false);
        when(archiveAccessService.canViewHistory(homework, true)).thenReturn(true);

        AppStudentHomeworkDetailRespVO detail = homeworkService.getHomeworkDetail(10L, 1L);

        assertEquals(88, detail.getMyScore());
        assertEquals(true, detail.getHistoryOnly());
        assertFalse(detail.getCanSubmit());
        verify(studentCourseService, never()).validateLevelUnlocked(any(), any(), any());
    }

    @Test
    void homeworkOutsideHistoryIsStillHidden() {
        EduHomeworkDO homework = publishedHomework(LocalDateTime.now().plusDays(1));
        homework.setStatus(3);
        when(homeworkMapper.selectById(1L)).thenReturn(homework);
        when(archiveAccessService.canViewHistory(homework, false)).thenReturn(false);

        assertServiceException(() -> homeworkService.getHomeworkDetail(10L, 1L), STUDENT_HOMEWORK_NOT_EXISTS);
    }

    @Test
    void getHomeworkList_statusIsFilteredBeforePagination() {
        AppStudentHomeworkRespVO item = new AppStudentHomeworkRespVO();
        item.setHomeworkId(1L);
        item.setStatus(2);
        Page<AppStudentHomeworkRespVO> mapperPage = new Page<>(1, 20, 3);
        mapperPage.setRecords(java.util.List.of(item));
        when(studentHomeworkMapper.selectHomeworkPage(any(), eq(10L), isNull(), eq(2), eq(false))).thenReturn(mapperPage);
        AppStudentHomeworkPageReqVO request = new AppStudentHomeworkPageReqVO();
        request.setPageNo(1);
        request.setPageSize(20);
        request.setStatus(2);

        PageResult<AppStudentHomeworkRespVO> result = homeworkService.getHomeworkList(10L, request);

        assertEquals(3L, result.getTotal());
        assertEquals(java.util.List.of(item), result.getList());
    }


    @Test
    void uploadAttachment_archivedHomeworkIsRejectedBeforeFileWrite() {
        EduHomeworkDO homework = publishedHomework(LocalDateTime.now().plusDays(1));
        when(homeworkMapper.selectById(1L)).thenReturn(homework);
        when(archiveAccessService.canSubmit(homework)).thenReturn(false);

        assertServiceException(() -> homeworkService.uploadAttachment(10L, 1L,
                new ByteArrayInputStream(new byte[]{1}), 1L, "a.png", "image/png"),
                STUDENT_HOMEWORK_NOT_EXISTS);
        verify(fileApi, never()).createFile(any(InputStream.class), anyLong(), any(), any(), any());
    }

    @Test
    void uploadAttachment_storesFileUnderHomeworkSubmissionDirectory() {
        EduHomeworkDO homework = publishedHomework(LocalDateTime.now().plusDays(1));
        when(homeworkMapper.selectById(1L)).thenReturn(homework);
        when(homeworkAssigneeMapper.selectByHomeworkIdAndStudentId(1L, 10L)).thenReturn(new EduHomeworkAssigneeDO());
        InputStream content = new ByteArrayInputStream(new byte[]{1});
        when(fileApi.createFile(content, 1L, "a.png", "edu/homework-submission/1", "image/png"))
                .thenReturn("https://school.example/a.png");

        String url = homeworkService.uploadAttachment(10L, 1L, content, 1L, "a.png", "image/png");

        assertEquals("https://school.example/a.png", url);
        verify(homeworkAttachmentService).validateUpload(homework, 1L, "image/png");
    }

    @Test
    void uploadAttachment_unassignedStudentIsRejected() {
        when(homeworkMapper.selectById(1L)).thenReturn(publishedHomework(LocalDateTime.now().plusDays(1)));
        when(homeworkAssigneeMapper.selectByHomeworkIdAndStudentId(1L, 10L)).thenReturn(null);

        assertServiceException(() -> homeworkService.uploadAttachment(10L, 1L,
                new ByteArrayInputStream(new byte[]{1}), 1L, "a.png", "image/png"), STUDENT_HOMEWORK_NOT_ASSIGNED);

        verify(fileApi, never()).createFile(any(InputStream.class), anyLong(), any(), any(), any());
    }

    @Test
    void uploadAttachment_afterTaskDeadlineIsRejected() {
        when(homeworkMapper.selectById(1L)).thenReturn(publishedHomework(LocalDateTime.now().minusMinutes(1)));
        when(homeworkAssigneeMapper.selectByHomeworkIdAndStudentId(1L, 10L)).thenReturn(new EduHomeworkAssigneeDO());

        assertServiceException(() -> homeworkService.uploadAttachment(10L, 1L,
                new ByteArrayInputStream(new byte[]{1}), 1L, "a.png", "image/png"), HOMEWORK_RECORD_DEADLINE_EXCEEDED);

        verify(fileApi, never()).createFile(any(InputStream.class), anyLong(), any(), any(), any());
    }

    @Test
    void uploadAttachment_redoRecordWithinRedoDeadlineCanUploadAfterTaskDeadline() {
        EduHomeworkDO homework = publishedHomework(LocalDateTime.now().minusDays(1));
        when(homeworkMapper.selectById(1L)).thenReturn(homework);
        when(homeworkAssigneeMapper.selectByHomeworkIdAndStudentId(1L, 10L)).thenReturn(new EduHomeworkAssigneeDO());
        when(homeworkRecordMapper.selectOne(any())).thenReturn(record(3, LocalDateTime.now().plusDays(2)));
        InputStream content = new ByteArrayInputStream(new byte[]{1});
        when(fileApi.createFile(content, 1L, "a.png", "edu/homework-submission/1", "image/png"))
                .thenReturn("https://school.example/a.png");

        assertEquals("https://school.example/a.png",
                homeworkService.uploadAttachment(10L, 1L, content, 1L, "a.png", "image/png"));
    }

    @Test
    void uploadAttachment_redoDeadlineOnlyAppliesToRedoRecords() {
        when(homeworkMapper.selectById(1L)).thenReturn(publishedHomework(LocalDateTime.now().minusDays(1)));
        when(homeworkAssigneeMapper.selectByHomeworkIdAndStudentId(1L, 10L)).thenReturn(new EduHomeworkAssigneeDO());
        when(homeworkRecordMapper.selectOne(any())).thenReturn(record(4, LocalDateTime.now().plusDays(2)));

        assertServiceException(() -> homeworkService.uploadAttachment(10L, 1L,
                new ByteArrayInputStream(new byte[]{1}), 1L, "a.png", "image/png"), HOMEWORK_RECORD_DEADLINE_EXCEEDED);
    }

    @Test
    void getHomeworkDetail_exposesEffectiveDeadlineAndSubmitFlag() {
        LocalDateTime taskDeadline = LocalDateTime.now().minusDays(1);
        LocalDateTime redoDeadline = LocalDateTime.now().plusDays(2);
        when(homeworkMapper.selectById(1L)).thenReturn(publishedHomework(taskDeadline));
        when(homeworkAssigneeMapper.selectByHomeworkIdAndStudentId(1L, 10L)).thenReturn(new EduHomeworkAssigneeDO());
        when(homeworkRecordMapper.selectOne(any())).thenReturn(record(3, redoDeadline), record(2, redoDeadline), null);

        AppStudentHomeworkDetailRespVO redo = homeworkService.getHomeworkDetail(10L, 1L);
        assertEquals(redoDeadline, redo.getRedoDeadline());
        assertEquals(redoDeadline, redo.getEffectiveDeadline());
        assertTrue(redo.getCanSubmit());

        assertFalse(homeworkService.getHomeworkDetail(10L, 1L).getCanSubmit());

        AppStudentHomeworkDetailRespVO notSubmitted = homeworkService.getHomeworkDetail(10L, 1L);
        assertEquals(taskDeadline, notSubmitted.getEffectiveDeadline());
        assertFalse(notSubmitted.getCanSubmit());
    }

    @Test
    void getHomeworkDetail_personalExtensionExtendsDeadlineOfUnsubmittedTask() {
        LocalDateTime taskDeadline = LocalDateTime.now().minusHours(2);
        LocalDateTime personalDeadline = LocalDateTime.now().plusDays(1);
        when(homeworkMapper.selectById(1L)).thenReturn(publishedHomework(taskDeadline));
        when(homeworkAssigneeMapper.selectByHomeworkIdAndStudentId(1L, 10L)).thenReturn(new EduHomeworkAssigneeDO());
        when(homeworkExtensionService.getPersonalDeadline(1L, 10L)).thenReturn(personalDeadline);

        AppStudentHomeworkDetailRespVO detail = homeworkService.getHomeworkDetail(10L, 1L);

        assertEquals(taskDeadline, detail.getDeadline());
        assertEquals(personalDeadline, detail.getPersonalDeadline());
        assertEquals(personalDeadline, detail.getEffectiveDeadline());
        assertTrue(detail.getCanSubmit());
    }

    @Test
    void getHomeworkDetail_extensionEarlierThanTaskDeadlineIsIgnored() {
        LocalDateTime taskDeadline = LocalDateTime.now().plusDays(3);
        when(homeworkMapper.selectById(1L)).thenReturn(publishedHomework(taskDeadline));
        when(homeworkAssigneeMapper.selectByHomeworkIdAndStudentId(1L, 10L)).thenReturn(new EduHomeworkAssigneeDO());
        when(homeworkExtensionService.getPersonalDeadline(1L, 10L)).thenReturn(LocalDateTime.now().plusDays(1));

        AppStudentHomeworkDetailRespVO detail = homeworkService.getHomeworkDetail(10L, 1L);

        assertEquals(null, detail.getPersonalDeadline());
        assertEquals(taskDeadline, detail.getEffectiveDeadline());
    }

    @Test
    void getHomeworkDetail_redoDeadlineStillWinsOverExtension() {
        LocalDateTime redoDeadline = LocalDateTime.now().plusDays(1);
        when(homeworkMapper.selectById(1L)).thenReturn(publishedHomework(LocalDateTime.now().minusDays(1)));
        when(homeworkAssigneeMapper.selectByHomeworkIdAndStudentId(1L, 10L)).thenReturn(new EduHomeworkAssigneeDO());
        when(homeworkRecordMapper.selectOne(any())).thenReturn(record(3, redoDeadline));
        when(homeworkExtensionService.getPersonalDeadline(1L, 10L)).thenReturn(LocalDateTime.now().plusDays(5));

        assertEquals(redoDeadline, homeworkService.getHomeworkDetail(10L, 1L).getEffectiveDeadline());
    }

    @Test
    void uploadAttachment_personalExtensionAllowsUploadAfterTaskDeadline() {
        EduHomeworkDO homework = publishedHomework(LocalDateTime.now().minusHours(1));
        when(homeworkMapper.selectById(1L)).thenReturn(homework);
        when(homeworkAssigneeMapper.selectByHomeworkIdAndStudentId(1L, 10L)).thenReturn(new EduHomeworkAssigneeDO());
        when(homeworkExtensionService.getPersonalDeadline(1L, 10L)).thenReturn(LocalDateTime.now().plusHours(5));
        InputStream content = new ByteArrayInputStream(new byte[]{1});
        when(fileApi.createFile(content, 1L, "a.png", "edu/homework-submission/1", "image/png"))
                .thenReturn("https://school.example/a.png");

        assertEquals("https://school.example/a.png",
                homeworkService.uploadAttachment(10L, 1L, content, 1L, "a.png", "image/png"));
    }

    @Test
    void getHomeworkList_fillsPersonalAndEffectiveDeadline() {
        LocalDateTime taskDeadline = LocalDateTime.now().minusHours(1);
        LocalDateTime personalDeadline = LocalDateTime.now().plusDays(1);
        AppStudentHomeworkRespVO extended = new AppStudentHomeworkRespVO();
        extended.setHomeworkId(1L);
        extended.setStatus(0);
        extended.setDeadline(taskDeadline);
        AppStudentHomeworkRespVO redo = new AppStudentHomeworkRespVO();
        redo.setHomeworkId(2L);
        redo.setRecordId(9L);
        redo.setStatus(3);
        redo.setDeadline(taskDeadline);
        redo.setRedoDeadline(personalDeadline.plusDays(1));
        Page<AppStudentHomeworkRespVO> mapperPage = new Page<>(1, 20, 2);
        mapperPage.setRecords(java.util.List.of(extended, redo));
        when(studentHomeworkMapper.selectHomeworkPage(any(), eq(10L), isNull(), isNull(), eq(false))).thenReturn(mapperPage);
        when(homeworkExtensionService.getPersonalDeadlineMap(eq(10L), any()))
                .thenReturn(java.util.Map.of(1L, personalDeadline, 2L, personalDeadline));
        AppStudentHomeworkPageReqVO request = new AppStudentHomeworkPageReqVO();
        request.setPageNo(1);
        request.setPageSize(20);

        homeworkService.getHomeworkList(10L, request);

        assertEquals(personalDeadline, extended.getPersonalDeadline());
        assertEquals(personalDeadline, extended.getEffectiveDeadline());
        assertEquals(personalDeadline, redo.getPersonalDeadline());
        assertEquals(personalDeadline.plusDays(1), redo.getEffectiveDeadline());
    }

    @Test
    void getHomeworkDetail_exposesAttachmentLimitsAndAllowedTypes() {
        EduHomeworkDO homework = publishedHomework(LocalDateTime.now().plusDays(1));
        when(homeworkMapper.selectById(1L)).thenReturn(homework);
        when(homeworkAssigneeMapper.selectByHomeworkIdAndStudentId(1L, 10L)).thenReturn(new EduHomeworkAssigneeDO());
        when(homeworkAttachmentService.getLimits()).thenReturn(new HomeworkAttachmentLimits(5, 20, 60));
        when(homeworkAttachmentService.getAllowedTypes(homework)).thenReturn(java.util.List.of("audio", "image"));

        AppStudentHomeworkDetailRespVO.AttachmentLimits limits =
                homeworkService.getHomeworkDetail(10L, 1L).getAttachmentLimits();
        assertEquals(5, limits.getMaxCount());
        assertEquals(20, limits.getMaxFileSizeMb());
        assertEquals(60, limits.getMaxTotalSizeMb());
        assertEquals(java.util.List.of("audio", "image"), limits.getAllowedTypes());
    }

    @Test
    void expiredHomeworkCanBeViewedButCannotBeSubmitted() {
        EduHomeworkDO homework = publishedHomework(LocalDateTime.now().plusDays(1));
        homework.setStatus(2);
        when(homeworkMapper.selectById(1L)).thenReturn(homework);
        when(homeworkAssigneeMapper.selectByHomeworkIdAndStudentId(1L, 10L))
                .thenReturn(new EduHomeworkAssigneeDO());
        when(archiveAccessService.canSubmit(homework)).thenReturn(false);

        AppStudentHomeworkDetailRespVO detail = homeworkService.getHomeworkDetail(10L, 1L);
        assertEquals(1L, detail.getHomeworkId());
        assertFalse(detail.getCanSubmit());

        cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentHomeworkSubmitReqVO request =
                new cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentHomeworkSubmitReqVO();
        request.setHomeworkId(1L);
        assertServiceException(() -> homeworkService.submitHomework(10L, request), STUDENT_HOMEWORK_NOT_EXISTS);
        verifyNoInteractions(homeworkRecordService);
    }

    @Test
    void lockedLevelBlocksDetailAndSubmit() {
        EduHomeworkDO homework = publishedHomework(LocalDateTime.now().plusDays(1));
        homework.setCourseId(20L);
        homework.setHomeworkLevel(2);
        when(homeworkMapper.selectById(1L)).thenReturn(homework);
        when(homeworkAssigneeMapper.selectByHomeworkIdAndStudentId(1L, 10L)).thenReturn(new EduHomeworkAssigneeDO());
        doThrow(cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception(STUDENT_COURSE_LEVEL_LOCKED))
                .when(studentCourseService).validateLevelUnlocked(10L, 20L, 2);

        assertServiceException(() -> homeworkService.getHomeworkDetail(10L, 1L), STUDENT_COURSE_LEVEL_LOCKED);
        var request = new cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentHomeworkSubmitReqVO();
        request.setHomeworkId(1L);
        assertServiceException(() -> homeworkService.submitHomework(10L, request), STUDENT_COURSE_LEVEL_LOCKED);
        assertServiceException(() -> homeworkService.uploadAttachment(10L, 1L,
                new ByteArrayInputStream(new byte[]{1}), 1L, "a.png", "image/png"), STUDENT_COURSE_LEVEL_LOCKED);
        verifyNoInteractions(homeworkRecordService);
    }

    @Test
    void existingSubmissionCanBeViewedResubmittedAndUploadedWithoutUnlockCheck() {
        EduHomeworkDO homework = publishedHomework(LocalDateTime.now().plusDays(1));
        homework.setCourseId(20L);
        homework.setHomeworkLevel(2);
        when(homeworkMapper.selectById(1L)).thenReturn(homework);
        when(homeworkAssigneeMapper.selectByHomeworkIdAndStudentId(1L, 10L)).thenReturn(new EduHomeworkAssigneeDO());
        EduHomeworkRecordDO returned = record(3, LocalDateTime.now().plusDays(2));
        when(homeworkRecordMapper.selectOne(any())).thenReturn(returned);
        when(homeworkRecordMapper.selectById(5L)).thenReturn(returned);
        InputStream content = new ByteArrayInputStream(new byte[]{1});
        when(fileApi.createFile(content, 1L, "a.png", "edu/homework-submission/1", "image/png"))
                .thenReturn("https://school.example/a.png");

        assertEquals(5L, homeworkService.getHomeworkDetail(10L, 1L).getRecordId());
        var update = new cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentHomeworkUpdateReqVO();
        update.setId(5L);
        homeworkService.updateHomework(10L, update);
        assertEquals("https://school.example/a.png",
                homeworkService.uploadAttachment(10L, 1L, content, 1L, "a.png", "image/png"));
        var submit = new cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentHomeworkSubmitReqVO();
        submit.setHomeworkId(1L);
        homeworkService.submitHomework(10L, submit);

        verify(homeworkRecordService).updateRecord(any(), eq(10L));
        verify(studentCourseService, never()).validateLevelUnlocked(any(), any(), any());
    }

    @Test
    void getHomeworkListReturnsLockReasonAndCalculatesEachCourseOnce() {
        AppStudentHomeworkRespVO locked = listHomework(1L, 20L, 2);
        AppStudentHomeworkRespVO sameCourseLocked = listHomework(2L, 20L, 2);
        sameCourseLocked.setRecordId(5L);
        sameCourseLocked.setStatus(3);
        AppStudentHomeworkRespVO unlocked = listHomework(3L, 20L, 3);
        AppStudentHomeworkRespVO otherCourse = listHomework(4L, 21L, 2);
        Page<AppStudentHomeworkRespVO> mapperPage = new Page<>(1, 20, 4);
        mapperPage.setRecords(java.util.List.of(locked, sameCourseLocked, unlocked, otherCourse));
        when(studentHomeworkMapper.selectHomeworkPage(any(), eq(10L), isNull(), isNull(), eq(false)))
                .thenReturn(mapperPage);
        String reason = "初级还差 2 个任务达标（需达标 80%）";
        when(studentCourseService.getUnlockStatus(10L, 20L)).thenReturn(unlockStatus(
                unlockLevel(2, false, reason), unlockLevel(3, true, null)));
        when(studentCourseService.getUnlockStatus(10L, 21L)).thenReturn(unlockStatus(
                unlockLevel(2, true, null)));

        PageResult<AppStudentHomeworkRespVO> result = homeworkService.getHomeworkList(10L, listRequest());

        assertEquals(4L, result.getTotal());
        assertTrue(locked.getLocked());
        assertEquals(reason, locked.getLockReason());
        assertTrue(sameCourseLocked.getLocked());
        assertEquals(reason, sameCourseLocked.getLockReason());
        assertFalse(unlocked.getLocked());
        assertEquals(null, unlocked.getLockReason());
        assertFalse(otherCourse.getLocked());
        assertEquals(null, otherCourse.getLockReason());
        verify(studentCourseService, times(1)).getUnlockStatus(10L, 20L);
        verify(studentCourseService, times(1)).getUnlockStatus(10L, 21L);
    }

    @Test
    void getHomeworkListUsesUnlockStatusForHistoryTasksWithOrWithoutRecords() {
        AppStudentHomeworkRespVO started = listHomework(1L, 20L, 2);
        started.setRecordId(5L);
        started.setStatus(3);
        started.setHistoryOnly(true);
        AppStudentHomeworkRespVO history = listHomework(2L, 20L, 2);
        history.setHistoryOnly(true);
        Page<AppStudentHomeworkRespVO> mapperPage = new Page<>(1, 20, 2);
        mapperPage.setRecords(java.util.List.of(started, history));
        when(studentHomeworkMapper.selectHomeworkPage(any(), eq(10L), isNull(), isNull(), eq(true)))
                .thenReturn(mapperPage);
        String reason = "初级还有 1 个任务在等教师批改";
        when(studentCourseService.getUnlockStatus(10L, 20L)).thenReturn(unlockStatus(
                unlockLevel(2, false, reason)));

        AppStudentHomeworkPageReqVO request = listRequest();
        request.setScope(AppStudentHomeworkPageReqVO.SCOPE_HISTORY);
        homeworkService.getHomeworkList(10L, request);

        assertTrue(started.getLocked());
        assertEquals(reason, started.getLockReason());
        assertTrue(history.getLocked());
        assertEquals(reason, history.getLockReason());
        verify(studentCourseService, times(1)).getUnlockStatus(10L, 20L);
    }

    @Test
    void getHomeworkListDoesNotLockUnrestrictedLevelTasks() {
        AppStudentHomeworkRespVO professional = listHomework(3L, 20L, 0);
        AppStudentHomeworkRespVO beginner = listHomework(4L, 20L, 1);
        AppStudentHomeworkRespVO unspecified = listHomework(5L, 20L, null);
        AppStudentHomeworkRespVO invalid = listHomework(6L, 20L, StudentLevelUnlockCalculator.MAX_LEVEL + 1);
        var items = java.util.List.of(professional, beginner, unspecified, invalid);
        Page<AppStudentHomeworkRespVO> mapperPage = new Page<>(1, 20, items.size());
        mapperPage.setRecords(items);
        when(studentHomeworkMapper.selectHomeworkPage(any(), eq(10L), isNull(), isNull(), eq(false)))
                .thenReturn(mapperPage);

        homeworkService.getHomeworkList(10L, listRequest());

        for (AppStudentHomeworkRespVO item : items) {
            assertFalse(item.getLocked());
            assertEquals(null, item.getLockReason());
        }
        verifyNoInteractions(studentCourseService);
    }

    private static AppStudentHomeworkRespVO listHomework(Long id, Long courseId, Integer level) {
        AppStudentHomeworkRespVO item = new AppStudentHomeworkRespVO();
        item.setHomeworkId(id);
        item.setCourseId(courseId);
        item.setHomeworkLevel(level);
        item.setStatus(0);
        return item;
    }

    private static AppStudentHomeworkPageReqVO listRequest() {
        AppStudentHomeworkPageReqVO request = new AppStudentHomeworkPageReqVO();
        request.setPageNo(1);
        request.setPageSize(20);
        return request;
    }

    private static AppStudentUnlockStatusRespVO unlockStatus(AppStudentUnlockStatusRespVO.Level... levels) {
        AppStudentUnlockStatusRespVO status = new AppStudentUnlockStatusRespVO();
        status.setLevels(java.util.List.of(levels));
        return status;
    }

    private static AppStudentUnlockStatusRespVO.Level unlockLevel(int level, boolean unlocked, String reason) {
        AppStudentUnlockStatusRespVO.Level item = new AppStudentUnlockStatusRespVO.Level();
        item.setLevel(level);
        item.setUnlocked(unlocked);
        item.setReasonText(reason);
        return item;
    }

    private static EduHomeworkDO publishedHomework(LocalDateTime deadline) {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(1L);
        homework.setStatus(1);
        homework.setDeadline(deadline);
        return homework;
    }

    private static EduHomeworkRecordDO record(int status, LocalDateTime redoDeadline) {
        EduHomeworkRecordDO record = new EduHomeworkRecordDO();
        record.setId(5L);
        record.setHomeworkId(1L);
        record.setStudentId(10L);
        record.setStatus(status);
        record.setRedoDeadline(redoDeadline);
        return record;
    }
}
