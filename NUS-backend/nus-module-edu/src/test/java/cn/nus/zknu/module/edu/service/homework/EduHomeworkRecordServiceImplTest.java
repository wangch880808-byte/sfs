package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.module.edu.service.aireview.EduAiBusinessAccessService;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordCorrectReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordGradeRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordQueueRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordSubmitReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordUpdateReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAssigneeDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkGradingActionDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.EduHomeworkAiReviewMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.service.aireview.EduHomeworkAiReviewService;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_REDO_SCOPE_ARCHIVED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_ARCHIVED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_RECORD_DEADLINE_EXCEEDED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_RECORD_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_RECORD_REDO_DEADLINE_INVALID;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_RECORD_REDO_DEADLINE_REQUIRED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_RECORD_UPDATE_FORBIDDEN;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_RECORD_CORRECT_STATUS_INVALID;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_RECORD_REDO_STATUS_INVALID;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_RECORD_STATE_CHANGED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_RECORD_CONFLICT;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_RECORD_ALREADY_SUBMITTED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_GRADING_UNDO_CHANGED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_GRADING_UNDO_EXPIRED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_GRADING_UNDO_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_HOMEWORK_NOT_ASSIGNED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

class EduHomeworkRecordServiceImplTest extends BaseMockitoUnitTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                EduHomeworkRecordDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                EduHomeworkAiReviewDO.class);
    }

    @InjectMocks
    private EduHomeworkRecordServiceImpl recordService;
    @Mock
    private EduHomeworkRecordMapper recordMapper;
    @Mock
    private EduHomeworkMapper homeworkMapper;
    @Mock
    private EduHomeworkAiReviewService aiReviewService;
    @Mock
    private EduHomeworkRecordHistoryService historyService;
    @Mock
    private EduAiBusinessAccessService businessAccessService;
    @Mock
    private EduHomeworkAssigneeMapper assigneeMapper;
    @Mock
    private HomeworkAttachmentService homeworkAttachmentService;
    @Mock
    private EduBusinessNotifyService businessNotifyService;
    @Mock
    private EduHomeworkArchiveAccessService archiveAccessService;
    @Mock
    private EduHomeworkGradingActionService gradingActionService;
    @Mock
    private EduHomeworkAiReviewMapper aiReviewMapper;
    @Mock
    private AdminUserService adminUserService;

    @BeforeEach
    void allowActiveHomework() {
        lenient().when(archiveAccessService.canView(any())).thenReturn(true);
        lenient().when(archiveAccessService.canSubmit(any())).thenReturn(true);
        lenient().when(archiveAccessService.canViewHistory(any(), anyBoolean())).thenReturn(true);
    }

    @Test
    void submitRecord_unassignedStudentIsRejected() {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(100L);
        homework.setDeadline(LocalDateTime.now().plusDays(1));
        when(homeworkMapper.selectById(100L)).thenReturn(homework);
        EduHomeworkRecordSubmitReqVO request = new EduHomeworkRecordSubmitReqVO();
        request.setHomeworkId(100L);
        request.setContent("answer");

        assertServiceException(() -> recordService.submitRecord(request, 10L),
                STUDENT_HOMEWORK_NOT_ASSIGNED);

        verify(recordMapper, never()).insert(any(EduHomeworkRecordDO.class));
    }

    @Test
    void updateRecord_otherStudentIsRejected() {
        EduHomeworkRecordDO record = createRecord(1, 20L);
        when(recordMapper.selectById(1L)).thenReturn(record);

        EduHomeworkRecordUpdateReqVO request = createUpdateRequest();
        assertServiceException(() -> recordService.updateRecord(request, 10L), HOMEWORK_RECORD_NOT_EXISTS);

        verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateRecord_nonRedoRecordIsRejected() {
        EduHomeworkRecordUpdateReqVO request = createUpdateRequest();
        for (int status : List.of(0, 1, 2, 4)) {
            when(recordMapper.selectById(1L)).thenReturn(createRecord(status, 10L));
            assertServiceException(() -> recordService.updateRecord(request, 10L), HOMEWORK_RECORD_UPDATE_FORBIDDEN);
        }

        verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateRecord_redoRecordBecomesResubmitted() {
        EduHomeworkRecordDO record = createRecord(3, 10L);
        when(recordMapper.selectById(1L)).thenReturn(record);
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(100L);
        homework.setDeadline(LocalDateTime.now().plusDays(1));
        when(homeworkMapper.selectById(100L)).thenReturn(homework);
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class))).thenReturn(1);

        recordService.updateRecord(createUpdateRequest(), 10L);

        ArgumentCaptor<EduHomeworkRecordDO> captor = ArgumentCaptor.forClass(EduHomeworkRecordDO.class);
        ArgumentCaptor<LambdaUpdateWrapper<EduHomeworkRecordDO>> where = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(recordMapper).update(captor.capture(), where.capture());
        assertEquals(4, captor.getValue().getStatus());
        // 只从“已打回”改为“已重新提交”
        assertTrue(where.getValue().getSqlSegment().contains("status"));
        assertTrue(where.getValue().getParamNameValuePairs().containsValue(3));
        verify(historyService).append(eq(record), eq("RESUBMIT"), eq(4),
                nullable(Integer.class), nullable(String.class), nullable(LocalDateTime.class), nullable(Long.class));
    }

    @Test
    void correctRecord_queuesStudentNotificationUntilUndoWindowEnds() {
        EduHomeworkRecordDO record = createRecord(1, 10L);
        EduHomeworkDO homework = createTeacherHomework();
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(homeworkMapper.selectById(100L)).thenReturn(homework);
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(historyService.append(any(), any(), any(), any(), any(), any(), any())).thenReturn(31L);
        EduHomeworkGradingActionDO action = new EduHomeworkGradingActionDO();
        action.setId(55L);
        action.setUndoDeadline(LocalDateTime.now().plusSeconds(10));
        when(gradingActionService.record(any(), any(), anyInt(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(action);
        when(gradingActionService.remainingSeconds(eq(action), any())).thenReturn(10);
        EduHomeworkRecordCorrectReqVO request = new EduHomeworkRecordCorrectReqVO();
        request.setId(1L);
        request.setScore(86);
        request.setComment("完成良好");

        EduHomeworkRecordGradeRespVO resp;
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            resp = recordService.correctRecord(request);
        }

        // 不直接发通知，交给撤销期过后的发送任务
        verify(businessNotifyService, never()).sendToUser(any(), any(), any(), any());
        verify(gradingActionService).record(eq(record), eq("CORRECT"), eq(2), any(LocalDateTime.class), eq(31L),
                eq(7L), isNull(), eq(EduBusinessNotifyService.HOMEWORK_CORRECTED),
                argThat(params -> params.get("score").equals(86) && params.get("comment").equals("完成良好")),
                eq("/student/homework/feedback/100"));
        verify(historyService).append(eq(record), eq("CORRECT"), eq(2), eq(86), eq("完成良好"),
                nullable(LocalDateTime.class), nullable(Long.class));
        assertEquals(2, resp.getStatus());
        assertEquals(86, resp.getScore());
        assertEquals(55L, resp.getUndoToken());
        assertEquals(10, resp.getUndoSeconds());
        assertNotNull(resp.getUpdateTime());
    }

    @Test
    void archivedHomeworkAllowsTeacherHistoryButRejectsCorrection() {
        EduHomeworkRecordDO record = createRecord(1, 10L);
        EduHomeworkDO homework = createTeacherHomework();
        homework.setStatus(3);
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(homeworkMapper.selectById(100L)).thenReturn(homework);
        EduHomeworkRecordCorrectReqVO request = new EduHomeworkRecordCorrectReqVO();
        request.setId(1L);
        request.setScore(80);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            recordService.getHistory(1L);
            assertServiceException(() -> recordService.correctRecord(request), HOMEWORK_ARCHIVED);
        }

        verify(historyService).listByRecordId(1L);
        verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void archivedHomeworkRejectsRedoAndDeadlineExtension() {
        EduHomeworkDO homework = createTeacherHomework();
        homework.setStatus(3);
        when(recordMapper.selectById(1L)).thenReturn(createRecord(3, 10L));
        when(homeworkMapper.selectById(100L)).thenReturn(homework);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertServiceException(() -> recordService.redoRecord(1L, LocalDateTime.now().plusDays(1), null),
                    HOMEWORK_ARCHIVED);
            assertServiceException(() -> recordService.redoRecordBatch(List.of(1L), LocalDateTime.now().plusDays(1)),
                    HOMEWORK_ARCHIVED);
        }

        verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void correctRecord_statusMatrix() {
        EduHomeworkRecordCorrectReqVO request = new EduHomeworkRecordCorrectReqVO();
        request.setId(1L);
        request.setScore(80);
        for (int status : List.of(0, 3)) {
            when(recordMapper.selectById(1L)).thenReturn(createRecord(status, 10L));
            when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());
            try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
                security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
                assertServiceException(() -> recordService.correctRecord(request), HOMEWORK_RECORD_CORRECT_STATUS_INVALID);
            }
        }
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        for (int status : List.of(1, 2, 4)) {
            EduHomeworkRecordDO record = createRecord(status, 10L);
            when(recordMapper.selectById(1L)).thenReturn(record);
            when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());
            try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
                security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
                recordService.correctRecord(request);
            }
            verify(historyService).append(eq(record), eq("CORRECT"), eq(2), eq(80),
                    nullable(String.class), nullable(LocalDateTime.class), nullable(Long.class));
        }
    }

    @Test
    void correctRecord_linksReadyAiDraft() {
        EduHomeworkRecordDO record = createRecord(1, 10L);
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());
        EduHomeworkAiReviewDO draft = new EduHomeworkAiReviewDO();
        draft.setId(9L);
        draft.setStatus(2);
        when(aiReviewService.getLatestForCorrection(1L)).thenReturn(draft);
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        EduHomeworkRecordCorrectReqVO request = new EduHomeworkRecordCorrectReqVO();
        request.setId(1L);
        request.setScore(90);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            recordService.correctRecord(request);
        }
        verify(historyService).append(record, "CORRECT", 2, 90, null, null, 9L);
    }

    @Test
    void redoRecord_keepsPreviousGradeAndRejectsUnsubmitted() {
        EduHomeworkRecordDO record = createRecord(2, 10L);
        record.setScore(77);
        record.setComment("原评语");
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            recordService.redoRecord(1L, null, null);
        }
        verify(historyService).append(record, "REDO", 3, 77, "原评语", null, null);
        assertEquals(77, record.getScore());
        assertEquals("原评语", record.getComment());
        ArgumentCaptor<LambdaUpdateWrapper<EduHomeworkRecordDO>> redoUpdate = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(recordMapper).update(any(EduHomeworkRecordDO.class), redoUpdate.capture());
        assertFalse(redoUpdate.getValue().getSqlSet().contains("score"));
        assertFalse(redoUpdate.getValue().getSqlSet().contains("comment"));

        when(recordMapper.selectById(1L)).thenReturn(createRecord(0, 10L));
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertServiceException(() -> recordService.redoRecord(1L, null, null), HOMEWORK_RECORD_REDO_STATUS_INVALID);
        }
    }

    @Test
    void redoRecord_archivedClassOrClassCourseIsRejectedBeforeAnyUpdate() {
        EduHomeworkRecordDO record = createRecord(2, 10L);
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());
        doThrow(cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception(
                HOMEWORK_REDO_SCOPE_ARCHIVED, "班级", "班级"))
                .when(archiveAccessService).validateRedoScopeActive(100L);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertServiceException(() -> recordService.redoRecord(1L, null, null), HOMEWORK_REDO_SCOPE_ARCHIVED,
                    "班级", "班级");
        }
        verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class));
        verifyNoInteractions(historyService);
    }

    @Test
    void redoRecord_acceptsResubmittedStatus() {
        EduHomeworkRecordDO record = createRecord(4, 10L);
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            recordService.redoRecord(1L, null, null);
        }
        verify(historyService).append(record, "REDO", 3, null, null, null, null);
    }

    @Test
    void historyUsesViewingAccessAndStudentHistoryStillChecksAssignment() {
        EduHomeworkRecordDO record = createRecord(2, 10L);
        when(recordMapper.selectById(1L)).thenReturn(record);
        EduHomeworkDO homework = createTeacherHomework();
        when(homeworkMapper.selectById(100L)).thenReturn(homework);
        recordService.getHistory(1L);
        verify(businessAccessService).requireHomeworkViewAccess(100L);
        doThrow(cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception(
                cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN))
                .when(businessAccessService).requireHomeworkViewAccess(100L);
        assertServiceException(() -> recordService.getHistory(1L),
                cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN);
        verify(historyService).listByRecordId(1L);

        assertServiceException(() -> recordService.getStudentHistory(100L, 11L), STUDENT_HOMEWORK_NOT_ASSIGNED);
        when(assigneeMapper.selectByHomeworkIdAndStudentId(100L, 10L)).thenReturn(new EduHomeworkAssigneeDO());
        when(recordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(record);
        recordService.getStudentHistory(100L, 10L);
        verify(historyService, times(2)).listByRecordId(1L);
    }

    @Test
    void redoRecord_queuesNotificationOnlyWhenStatusActuallyChanges() {
        EduHomeworkRecordDO record = createRecord(2, 10L);
        EduHomeworkDO homework = createTeacherHomework();
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(homeworkMapper.selectById(100L)).thenReturn(homework);
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class)))
                .thenReturn(1, 0);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            recordService.redoRecord(1L, null, null);
            // 第二次条件更新没有命中：状态已被他人改变，明确报错而不是悄悄成功
            assertServiceException(() -> recordService.redoRecord(1L, null, null), HOMEWORK_RECORD_STATE_CHANGED);
        }

        verify(aiReviewService, times(1)).rejectLatestReview(1L);
        verify(historyService, times(1)).append(any(), eq("REDO"), any(), any(), any(), any(), any());
        verify(gradingActionService, times(1)).record(eq(record), eq("REDO"), eq(3), any(LocalDateTime.class),
                any(), eq(7L), any(), eq(EduBusinessNotifyService.HOMEWORK_REDO), any(),
                eq("/student/homework/submit/100"));
        verify(businessNotifyService, never()).sendToUser(any(), any(), any(), any());
    }

    @Test
    void updateRecord_afterTaskDeadlineWithinRedoDeadlineIsAccepted() {
        EduHomeworkRecordDO record = createRecord(3, 10L);
        record.setRedoDeadline(LocalDateTime.now().plusDays(3));
        when(recordMapper.selectById(1L)).thenReturn(record);
        EduHomeworkDO homework = createTeacherHomework();
        homework.setDeadline(LocalDateTime.now().minusDays(1));
        when(homeworkMapper.selectById(100L)).thenReturn(homework);

        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class))).thenReturn(1);

        recordService.updateRecord(createUpdateRequest(), 10L);

        ArgumentCaptor<EduHomeworkRecordDO> captor = ArgumentCaptor.forClass(EduHomeworkRecordDO.class);
        verify(recordMapper).update(captor.capture(), any(LambdaUpdateWrapper.class));
        assertEquals(4, captor.getValue().getStatus());
    }

    @Test
    void updateRecord_afterRedoDeadlineIsRejected() {
        EduHomeworkRecordDO record = createRecord(3, 10L);
        record.setRedoDeadline(LocalDateTime.now().minusMinutes(1));
        when(recordMapper.selectById(1L)).thenReturn(record);
        EduHomeworkDO homework = createTeacherHomework();
        homework.setDeadline(LocalDateTime.now().plusDays(1));
        when(homeworkMapper.selectById(100L)).thenReturn(homework);

        assertServiceException(() -> recordService.updateRecord(createUpdateRequest(), 10L),
                HOMEWORK_RECORD_DEADLINE_EXCEEDED);

        verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void redoRecord_afterTaskDeadlineWithoutRedoDeadlineIsRejected() {
        EduHomeworkDO homework = createTeacherHomework();
        homework.setDeadline(LocalDateTime.now().minusDays(1));
        when(recordMapper.selectById(1L)).thenReturn(createRecord(2, 10L));
        when(homeworkMapper.selectById(100L)).thenReturn(homework);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertServiceException(() -> recordService.redoRecord(1L, null, null),
                    HOMEWORK_RECORD_REDO_DEADLINE_REQUIRED);
        }

        verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void redoRecord_pastRedoDeadlineIsRejected() {
        when(recordMapper.selectById(1L)).thenReturn(createRecord(2, 10L));
        when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertServiceException(() -> recordService.redoRecord(1L, LocalDateTime.now().minusHours(1), null),
                    HOMEWORK_RECORD_REDO_DEADLINE_INVALID);
        }

        verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void redoRecord_writesRedoDeadline() {
        EduHomeworkDO homework = createTeacherHomework();
        homework.setDeadline(LocalDateTime.now().minusDays(1));
        LocalDateTime redoDeadline = LocalDateTime.now().plusDays(7).withNano(0);
        when(recordMapper.selectById(1L)).thenReturn(createRecord(2, 10L));
        when(homeworkMapper.selectById(100L)).thenReturn(homework);
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class))).thenReturn(1);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            recordService.redoRecord(1L, redoDeadline, null);
        }

        ArgumentCaptor<LambdaUpdateWrapper<EduHomeworkRecordDO>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(recordMapper).update(any(EduHomeworkRecordDO.class), captor.capture());
        assertTrue(captor.getValue().getSqlSet().contains("redo_deadline"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(redoDeadline));
        verify(aiReviewService).rejectLatestReview(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void redoRecord_alreadyReturnedOnlyExtendsRedoDeadline() {
        EduHomeworkDO homework = createTeacherHomework();
        homework.setDeadline(LocalDateTime.now().minusDays(10));
        EduHomeworkRecordDO record = createRecord(3, 10L);
        record.setRedoDeadline(LocalDateTime.now().minusDays(1));
        LocalDateTime extended = LocalDateTime.now().plusDays(3).withNano(0);
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(homeworkMapper.selectById(100L)).thenReturn(homework);
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class)))
                .thenReturn(1);

        EduHomeworkRecordGradeRespVO resp;
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            resp = recordService.redoRecord(1L, extended, null);
        }

        ArgumentCaptor<LambdaUpdateWrapper<EduHomeworkRecordDO>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(recordMapper, times(1)).update(any(EduHomeworkRecordDO.class), captor.capture());
        LambdaUpdateWrapper<EduHomeworkRecordDO> extension = captor.getValue();
        assertTrue(extension.getSqlSet().contains("redo_deadline"));
        assertFalse(extension.getSqlSet().contains("redo_count"));
        assertTrue(extension.getParamNameValuePairs().containsValue(extended));
        verify(aiReviewService, never()).rejectLatestReview(any());
        verify(businessNotifyService, never()).sendToUser(any(), any(), any(), any());
        verify(historyService).append(record, "REDO_DEADLINE", 3, null, null, extended, null);
        verify(gradingActionService, never()).record(any(), any(), anyInt(), any(), any(), any(), any(), any(),
                any(), any());
        assertEquals(extended, resp.getRedoDeadline());
        assertNull(resp.getUndoToken());
    }

    @Test
    void redoRecordBatch_deduplicatesIds() {
        EduHomeworkRecordDO first = createRecord(2, 10L);
        EduHomeworkRecordDO second = createRecord(1, 11L);
        second.setId(2L);
        when(recordMapper.selectById(1L)).thenReturn(first);
        when(recordMapper.selectById(2L)).thenReturn(second);
        when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class))).thenReturn(1);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            recordService.redoRecordBatch(List.of(1L, 1L, 2L), null);
        }

        verify(recordMapper, times(2)).update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class));
        verify(aiReviewService).rejectLatestReview(1L);
        verify(aiReviewService).rejectLatestReview(2L);
    }

    @Test
    void redoRecordBatch_unauthorizedRecordRejectsWholeBatchBeforeAnyUpdate() {
        EduHomeworkRecordDO own = createRecord(2, 10L);
        EduHomeworkRecordDO other = createRecord(2, 11L);
        other.setId(2L);
        other.setHomeworkId(200L);
        EduHomeworkDO otherHomework = createTeacherHomework();
        otherHomework.setId(200L);
        otherHomework.setTeacherId(8L);
        when(recordMapper.selectById(1L)).thenReturn(own);
        when(recordMapper.selectById(2L)).thenReturn(other);
        when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());
        when(homeworkMapper.selectById(200L)).thenReturn(otherHomework);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertServiceException(() -> recordService.redoRecordBatch(List.of(1L, 2L), null),
                    HOMEWORK_NOT_EXISTS);
        }

        verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class));
        verify(aiReviewService, never()).rejectLatestReview(any());
        verify(businessNotifyService, never()).sendToUser(any(), any(), any(), any());
    }

    @Test
    void redoMethodsRollbackOnAnyException() throws Exception {
        for (Method method : List.of(
                EduHomeworkRecordServiceImpl.class.getMethod("correctRecord", EduHomeworkRecordCorrectReqVO.class),
                EduHomeworkRecordServiceImpl.class.getMethod("redoRecord", Long.class, LocalDateTime.class,
                        LocalDateTime.class),
                EduHomeworkRecordServiceImpl.class.getMethod("redoRecordBatch", Collection.class, LocalDateTime.class),
                EduHomeworkRecordServiceImpl.class.getMethod("undoGrading", Long.class))) {
            Transactional transactional = method.getAnnotation(Transactional.class);
            assertNotNull(transactional, method.getName());
            assertArrayEquals(new Class<?>[]{Exception.class}, transactional.rollbackFor());
        }
    }

    // ===== 批改可靠性：重复提交、条件更新、乐观校验、连点、撤销与批改队列 =====

    @Test
    void submitRecord_duplicateKeyBecomesAlreadySubmitted() {
        EduHomeworkDO homework = createTeacherHomework();
        homework.setDeadline(LocalDateTime.now().plusDays(1));
        when(homeworkMapper.selectById(100L)).thenReturn(homework);
        when(assigneeMapper.selectByHomeworkIdAndStudentId(100L, 10L)).thenReturn(new EduHomeworkAssigneeDO());
        when(recordMapper.insert(any(EduHomeworkRecordDO.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry for key uk_hw_stu"));
        EduHomeworkRecordSubmitReqVO request = new EduHomeworkRecordSubmitReqVO();
        request.setHomeworkId(100L);
        request.setContent("answer");

        assertServiceException(() -> recordService.submitRecord(request, 10L), HOMEWORK_RECORD_ALREADY_SUBMITTED);

        verify(historyService, never()).append(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateRecord_concurrentResubmitIsRejected() {
        when(recordMapper.selectById(1L)).thenReturn(createRecord(3, 10L));
        EduHomeworkDO homework = createTeacherHomework();
        homework.setDeadline(LocalDateTime.now().plusDays(1));
        when(homeworkMapper.selectById(100L)).thenReturn(homework);
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertServiceException(() -> recordService.updateRecord(createUpdateRequest(), 10L),
                HOMEWORK_RECORD_STATE_CHANGED);

        verify(historyService, never()).append(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void correctRecord_conditionalUpdateChecksStatusAndReadUpdateTime() {
        EduHomeworkRecordDO record = createRecord(1, 10L);
        LocalDateTime readAt = LocalDateTime.of(2026, 10, 7, 9, 30, 15, 123_000_000);
        record.setUpdateTime(readAt);
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class))).thenReturn(1);

        LocalDateTime clockTime = readAt.withNano(456_789_000);
        EduHomeworkRecordGradeRespVO response;
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class);
             MockedStatic<LocalDateTime> clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            clock.when(LocalDateTime::now).thenReturn(clockTime);
            response = recordService.correctRecord(correctRequest(80, "可以"));
        }

        ArgumentCaptor<EduHomeworkRecordDO> entity = ArgumentCaptor.forClass(EduHomeworkRecordDO.class);
        ArgumentCaptor<LambdaUpdateWrapper<EduHomeworkRecordDO>> wrapper = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(recordMapper).update(entity.capture(), wrapper.capture());
        String where = wrapper.getValue().getSqlSegment();
        assertTrue(where.contains("status IN"), where);
        assertTrue(where.contains("update_time"), where);
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(readAt));
        // 条件保留读到的毫秒，写入与响应使用同一个毫秒版本
        assertEquals(readAt.withNano(456_000_000), entity.getValue().getUpdateTime());
        assertEquals(entity.getValue().getUpdateTime(), response.getUpdateTime());
        verify(gradingActionService).record(eq(record), eq("CORRECT"), eq(EduHomeworkRecordStatusEnum.CORRECTED),
                eq(response.getUpdateTime()), any(), eq(7L), any(), any(), any(), any());
    }

    @Test
    void correctRecord_writeWithinSameMillisecondAdvancesVersion() {
        LocalDateTime readAt = LocalDateTime.of(2026, 10, 7, 9, 30, 15, 123_000_000);
        EduHomeworkRecordDO record = createRecord(EduHomeworkRecordStatusEnum.SUBMITTED, 10L);
        record.setUpdateTime(readAt);
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class))).thenReturn(1);

        LocalDateTime clockTime = readAt.plusNanos(456_789);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class);
             MockedStatic<LocalDateTime> clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            clock.when(LocalDateTime::now).thenReturn(clockTime);
            EduHomeworkRecordGradeRespVO response = recordService.correctRecord(correctRequest(80, "可以"));
            assertEquals(readAt.plusNanos(1_000_000), response.getUpdateTime());
        }
    }

    @Test
    void correctRecord_concurrentChangeIsRejectedWithoutSideEffects() {
        when(recordMapper.selectById(1L)).thenReturn(createRecord(1, 10L));
        when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class))).thenReturn(0);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertServiceException(() -> recordService.correctRecord(correctRequest(80, "可以")),
                    HOMEWORK_RECORD_STATE_CHANGED);
        }

        verify(aiReviewService, never()).markTeacherReviewed(any(), any(), any());
        verify(historyService, never()).append(any(), any(), any(), any(), any(), any(), any());
        verify(gradingActionService, never()).record(any(), any(), anyInt(), any(), any(), any(), any(), any(),
                any(), any());
    }

    @Test
    void correctRecord_staleExpectedUpdateTimeIsRejectedAndMatchingOneAccepted() {
        EduHomeworkRecordDO record = createRecord(2, 10L);
        record.setScore(70);
        record.setUpdateTime(LocalDateTime.of(2026, 10, 7, 9, 30, 15, 123_000_000));
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());

        EduHomeworkRecordCorrectReqVO stale = correctRequest(80, "可以");
        stale.setExpectedUpdateTime(LocalDateTime.of(2026, 10, 7, 9, 30, 15, 122_000_000));
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertServiceException(() -> recordService.correctRecord(stale), HOMEWORK_RECORD_CONFLICT);
        }
        verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class));

        // 同一秒内相差一毫秒也是旧版本；完全一致才允许写入
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        EduHomeworkRecordCorrectReqVO fresh = correctRequest(80, "可以");
        fresh.setExpectedUpdateTime(record.getUpdateTime());
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            recordService.correctRecord(fresh);
        }
        verify(recordMapper).update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void correctRecord_doubleClickReturnsPreviousResultWithoutSecondHistoryOrNotification() {
        EduHomeworkRecordDO record = createRecord(2, 10L);
        record.setScore(80);
        record.setComment("可以");
        record.setUpdateTime(LocalDateTime.of(2026, 10, 7, 9, 30, 15));
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());
        EduHomeworkGradingActionDO previous = new EduHomeworkGradingActionDO();
        previous.setId(55L);
        previous.setUndoDeadline(LocalDateTime.now().plusSeconds(8));
        when(gradingActionService.findRepeat(record, "CORRECT", 7L)).thenReturn(previous);
        when(gradingActionService.remainingSeconds(eq(previous), any())).thenReturn(8);
        // 第二次点击仍带着第一次读到的更新时间，也不能报“已被他人更新”
        EduHomeworkRecordCorrectReqVO request = correctRequest(80, " 可以 ");
        request.setExpectedUpdateTime(LocalDateTime.of(2026, 10, 7, 9, 0, 0));

        EduHomeworkRecordGradeRespVO resp;
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            resp = recordService.correctRecord(request);
        }

        assertEquals(55L, resp.getUndoToken());
        assertEquals(8, resp.getUndoSeconds());
        verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class));
        verify(historyService, never()).append(any(), any(), any(), any(), any(), any(), any());
        verify(gradingActionService, never()).record(any(), any(), anyInt(), any(), any(), any(), any(), any(),
                any(), any());
    }

    @Test
    void redoRecord_repeatedClickOnReturnedRecordIsNoop() {
        LocalDateTime deadline = LocalDateTime.now().plusDays(3).withNano(0);
        EduHomeworkRecordDO record = createRecord(3, 10L);
        record.setRedoDeadline(deadline);
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            recordService.redoRecord(1L, deadline, null);
        }

        verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class));
        verify(historyService, never()).append(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void redoRecord_staleExpectedUpdateTimeIsRejected() {
        EduHomeworkRecordDO record = createRecord(1, 10L);
        record.setUpdateTime(LocalDateTime.of(2026, 10, 7, 9, 30, 15, 123_000_000));
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertServiceException(() -> recordService.redoRecord(1L, null,
                    record.getUpdateTime().minusNanos(1_000_000)), HOMEWORK_RECORD_CONFLICT);
        }
        verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void undoGrading_restoresPreviousStateCancelsNotificationAndWritesHistory() {
        EduHomeworkRecordDO record = createRecord(2, 10L);
        record.setScore(90);
        record.setComment("新评语");
        when(recordMapper.selectById(1L)).thenReturn(record);
        when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());
        LocalDateTime after = LocalDateTime.of(2026, 10, 7, 9, 30, 15);
        EduHomeworkGradingActionDO action = createAction(7L, after);
        when(gradingActionService.getAction(55L)).thenReturn(action);
        when(gradingActionService.cancelIfUndoable(eq(action), any())).thenReturn(true);
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class))).thenReturn(1);

        EduHomeworkRecordGradeRespVO resp;
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            resp = recordService.undoGrading(55L);
        }

        ArgumentCaptor<LambdaUpdateWrapper<EduHomeworkRecordDO>> wrapper = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(recordMapper).update(any(EduHomeworkRecordDO.class), wrapper.capture());
        String set = wrapper.getValue().getSqlSet();
        assertTrue(set.contains("status") && set.contains("score") && set.contains("comment")
                && set.contains("redo_deadline"), set);
        assertTrue(wrapper.getValue().getSqlSegment().contains("update_time"));
        assertTrue(wrapper.getValue().getParamNameValuePairs().containsValue(after));
        verify(gradingActionService).restoreAiReview(action);
        verify(historyService).append(record, "UNDO", 1, 75, "原评语", null, null);
        assertEquals(1, resp.getStatus());
        assertEquals(75, resp.getScore());
        assertEquals("原评语", resp.getComment());
        assertNull(resp.getUndoToken());
    }

    @Test
    void undoGrading_rejectsOthersExpiredAndChangedRecords() {
        EduHomeworkRecordDO record = createRecord(2, 10L);
        when(homeworkMapper.selectById(100L)).thenReturn(createTeacherHomework());
        LocalDateTime after = LocalDateTime.of(2026, 10, 7, 9, 30, 15);
        EduHomeworkGradingActionDO others = createAction(8L, after);
        EduHomeworkGradingActionDO mine = createAction(7L, after);
        when(gradingActionService.getAction(54L)).thenReturn(others);
        when(gradingActionService.getAction(55L)).thenReturn(mine);
        when(gradingActionService.getAction(99L)).thenReturn(null);
        when(recordMapper.selectById(1L)).thenReturn(record);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertServiceException(() -> recordService.undoGrading(54L), HOMEWORK_GRADING_UNDO_NOT_EXISTS);
            assertServiceException(() -> recordService.undoGrading(99L), HOMEWORK_GRADING_UNDO_NOT_EXISTS);

            when(gradingActionService.cancelIfUndoable(eq(mine), any())).thenReturn(false);
            assertServiceException(() -> recordService.undoGrading(55L), HOMEWORK_GRADING_UNDO_EXPIRED);
            verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class));

            // 撤销期内学生已重交或他人已改：条件更新不命中，不恢复
            when(gradingActionService.cancelIfUndoable(eq(mine), any())).thenReturn(true);
            when(recordMapper.update(any(EduHomeworkRecordDO.class), any(LambdaUpdateWrapper.class))).thenReturn(0);
            assertServiceException(() -> recordService.undoGrading(55L), HOMEWORK_GRADING_UNDO_CHANGED);
        }
        verify(gradingActionService, never()).restoreAiReview(any());
        verify(historyService, never()).append(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getGradingQueue_returnsEveryRecordWithServerTotalAndLatestAiStatus() {
        when(businessAccessService.requireHomeworkViewAccess(100L)).thenReturn(createTeacherHomework());
        List<EduHomeworkRecordDO> records = new java.util.ArrayList<>();
        for (long i = 1; i <= 650; i++) {
            EduHomeworkRecordDO record = createRecord(i % 2 == 0 ? 2 : 1, 1000 + i);
            record.setId(i);
            records.add(record);
        }
        when(recordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(records);
        AdminUserDO student = new AdminUserDO();
        student.setId(1001L);
        student.setNickname("张三");
        student.setUsername("2026001");
        when(adminUserService.getUserMap(any())).thenReturn(Map.of(1001L, student));
        EduHomeworkAiReviewDO failed = aiReview(3L, 1L, 3);
        EduHomeworkAiReviewDO ready = aiReview(9L, 1L, 2);
        when(aiReviewMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(ready, failed), List.of(aiReview(12L, 600L, 0)));

        EduHomeworkRecordQueueRespVO queue;
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            queue = recordService.getGradingQueue(100L);
        }

        assertEquals(650, queue.getTotal());
        assertEquals(650, queue.getList().size());
        assertEquals("张三", queue.getList().get(0).getStudentName());
        assertEquals("2026001", queue.getList().get(0).getStudentNo());
        assertEquals(2, queue.getList().get(0).getAiReviewStatus());
        assertEquals(0, queue.getList().get(599).getAiReviewStatus());
        assertNull(queue.getList().get(1).getAiReviewStatus());
        // AI 初评状态分批查询，单次 IN 不超过 500
        verify(aiReviewMapper, times(2)).selectList(any(LambdaQueryWrapper.class));

        ArgumentCaptor<LambdaQueryWrapper<EduHomeworkRecordDO>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(recordMapper).selectList(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertFalse(sql.contains("LIMIT"), sql);
        assertFalse(query.getValue().getSqlSelect().contains("content"));
    }

    @Test
    void getGradingQueue_unauthorizedViewerIsRejected() {
        doThrow(cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception(
                cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN))
                .when(businessAccessService).requireHomeworkViewAccess(100L);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(8L);
            assertServiceException(() -> recordService.getGradingQueue(100L), cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN);
        }
        verify(recordMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    private static EduHomeworkRecordCorrectReqVO correctRequest(int score, String comment) {
        EduHomeworkRecordCorrectReqVO request = new EduHomeworkRecordCorrectReqVO();
        request.setId(1L);
        request.setScore(score);
        request.setComment(comment);
        return request;
    }

    private static EduHomeworkGradingActionDO createAction(Long operatorId, LocalDateTime afterUpdateTime) {
        EduHomeworkGradingActionDO action = new EduHomeworkGradingActionDO();
        action.setId(55L);
        action.setRecordId(1L);
        action.setOperatorId(operatorId);
        action.setActionType("CORRECT");
        action.setPrevStatus(1);
        action.setPrevScore(75);
        action.setPrevComment("原评语");
        action.setPrevRedoCount(0);
        action.setAfterStatus(2);
        action.setAfterUpdateTime(afterUpdateTime);
        action.setUndoDeadline(LocalDateTime.now().plusSeconds(5));
        action.setNotifyStatus(0);
        return action;
    }

    private static EduHomeworkAiReviewDO aiReview(Long id, Long recordId, int status) {
        EduHomeworkAiReviewDO review = new EduHomeworkAiReviewDO();
        review.setId(id);
        review.setHomeworkRecordId(recordId);
        review.setStatus(status);
        return review;
    }

    private static EduHomeworkRecordDO createRecord(int status, long studentId) {
        EduHomeworkRecordDO record = new EduHomeworkRecordDO();
        record.setId(1L);
        record.setHomeworkId(100L);
        record.setStudentId(studentId);
        record.setStatus(status);
        return record;
    }

    private static EduHomeworkRecordUpdateReqVO createUpdateRequest() {
        EduHomeworkRecordUpdateReqVO request = new EduHomeworkRecordUpdateReqVO();
        request.setId(1L);
        request.setContent("answer");
        return request;
    }

    private static EduHomeworkDO createTeacherHomework() {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(100L);
        homework.setTeacherId(7L);
        homework.setHomeworkName("口语训练");
        return homework;
    }
}
