package cn.nus.zknu.module.edu.service.dataexchange.gradebook;

import cn.hutool.json.JSONUtil;
import cn.nus.zknu.framework.security.core.service.SecurityFrameworkService;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAssigneeDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchDO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchItemDO;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.service.aireview.EduAiBusinessAccessService;
import cn.nus.zknu.module.edu.service.aireview.EduHomeworkAiReviewService;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.edu.service.clazz.EduClassService;
import cn.nus.zknu.module.edu.service.importbatch.EduImportCommitResult;
import cn.nus.zknu.module.edu.service.importbatch.EduImportPreviewRow;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.GRADEBOOK_HEADER_INVALID;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.GRADEBOOK_RECORD_CHANGED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GradebookDataExchangeHandlerTest extends BaseMockitoUnitTest {

    private static final int PRE_REDO_COLUMN = GradebookDataExchangeHandler.HEADERS.indexOf("打回前成绩");
    private static final int SCORE_COLUMN = GradebookDataExchangeHandler.HEADERS.indexOf("成绩*");
    private static final int COMMENT_COLUMN = GradebookDataExchangeHandler.HEADERS.indexOf("教师评语");
    private static final int RECORD_ID_COLUMN = GradebookDataExchangeHandler.HEADERS.indexOf("记录 ID");
    private static final int TOKEN_COLUMN = GradebookDataExchangeHandler.HEADERS.indexOf("行令牌");
    private static final int CHECKSUM_COLUMN = GradebookDataExchangeHandler.HEADERS.indexOf("模板校验值");
    private static final String OPTIONS = "{\"homeworkId\":100}";

    @Mock
    private SecurityFrameworkService securityFrameworkService;
    @Mock
    private EduAiBusinessAccessService aiBusinessAccessService;
    @Mock
    private EduHomeworkAssigneeMapper assigneeMapper;
    @Mock
    private EduHomeworkRecordMapper recordMapper;
    @Mock
    private EduClassService classService;
    @Mock
    private CapacityCourseApi courseMapper;
    @Mock
    private AdminUserService adminUserService;
    @Mock
    private EduHomeworkAiReviewService aiReviewService;
    @Mock
    private cn.nus.zknu.module.edu.service.homework.EduHomeworkRecordHistoryService historyService;
    @Mock
    private EduBusinessNotifyService businessNotifyService;

    private GradebookDataExchangeHandler handler;
    private EduHomeworkDO homework;
    private EduHomeworkRecordDO record;

    @BeforeAll
    static void initializeTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                EduHomeworkRecordDO.class);
    }

    @BeforeEach
    void setUp() {
        handler = new GradebookDataExchangeHandler();
        ReflectionTestUtils.setField(handler, "securityFrameworkService", securityFrameworkService);
        ReflectionTestUtils.setField(handler, "aiBusinessAccessService", aiBusinessAccessService);
        ReflectionTestUtils.setField(handler, "assigneeMapper", assigneeMapper);
        ReflectionTestUtils.setField(handler, "recordMapper", recordMapper);
        ReflectionTestUtils.setField(handler, "classService", classService);
        ReflectionTestUtils.setField(handler, "capacityCourseApi", courseMapper);
        ReflectionTestUtils.setField(handler, "adminUserService", adminUserService);
        ReflectionTestUtils.setField(handler, "aiReviewService", aiReviewService);
        ReflectionTestUtils.setField(handler, "historyService", historyService);
        ReflectionTestUtils.setField(handler, "businessNotifyService", businessNotifyService);
        ReflectionTestUtils.setField(handler, "metricPolicy", new cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy());

        homework = new EduHomeworkDO();
        homework.setId(100L);
        homework.setHomeworkName("任务一");
        homework.setClassId(3L);
        homework.setCourseId(5L);
        homework.setScore(80);
        homework.setPassScore(48);
        EduClassDO clazz = new EduClassDO();
        clazz.setId(3L);
        clazz.setClassName("软件 2601");
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(5L);
        course.setCourseName("Java 实训");
        EduHomeworkAssigneeDO assignee = new EduHomeworkAssigneeDO();
        assignee.setHomeworkId(100L);
        assignee.setStudentId(7L);
        AdminUserDO student = new AdminUserDO();
        student.setId(7L);
        student.setUsername("00001234");
        student.setNickname("张三");
        record = new EduHomeworkRecordDO();
        record.setId(11L);
        record.setHomeworkId(100L);
        record.setStudentId(7L);
        record.setStatus(EduHomeworkRecordStatusEnum.SUBMITTED);
        record.setSubmitTime(LocalDateTime.of(2026, 9, 1, 10, 0));

        lenient().when(securityFrameworkService.hasPermission(any())).thenReturn(true);
        lenient().when(aiBusinessAccessService.requireHomeworkAccess(100L)).thenReturn(homework);
        lenient().when(aiBusinessAccessService.requireHomeworkViewAccess(100L)).thenReturn(homework);
        lenient().when(classService.getClass(3L)).thenReturn(clazz);
        lenient().when(courseMapper.getCourse(5L)).thenReturn(course);
        lenient().when(assigneeMapper.selectListByHomeworkId(100L)).thenReturn(List.of(assignee));
        lenient().when(adminUserService.getUserList(org.mockito.ArgumentMatchers.<Long>anyCollection())).thenReturn(List.of(student));
        lenient().when(adminUserService.getUser(7L)).thenReturn(student);
        lenient().when(recordMapper.selectList(any())).thenReturn(List.of(record));
        lenient().when(recordMapper.selectByIds(anyCollection())).thenReturn(List.of(record));
        lenient().when(recordMapper.update(any(EduHomeworkRecordDO.class), any(Wrapper.class))).thenReturn(1);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    // ========== 导出 ==========

    @Test
    void export_preservesStudentNumberAndHidesIntegrityColumns() throws Exception {
        byte[] content = handler.export(100L);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheet(GradebookDataExchangeHandler.SHEET_NAME);
            assertEquals("00001234", sheet.getRow(1).getCell(3).getStringCellValue());
            assertEquals("11", sheet.getRow(1).getCell(RECORD_ID_COLUMN).getStringCellValue());
            assertTrue(sheet.isColumnHidden(RECORD_ID_COLUMN));
            assertTrue(sheet.isColumnHidden(TOKEN_COLUMN));
            assertTrue(sheet.isColumnHidden(CHECKSUM_COLUMN));
            assertFalse(sheet.isColumnHidden(PRE_REDO_COLUMN));
            assertFalse(sheet.getColumnStyle(SCORE_COLUMN).getLocked());
            assertEquals(FillPatternType.NO_FILL, sheet.getColumnStyle(SCORE_COLUMN).getFillPattern());
            assertEquals(IndexedColors.LIGHT_YELLOW.getIndex(),
                    sheet.getRow(1).getCell(SCORE_COLUMN).getCellStyle().getFillForegroundColor());
            assertEquals(IndexedColors.GREY_25_PERCENT.getIndex(),
                    sheet.getRow(1).getCell(0).getCellStyle().getFillForegroundColor());
            // 「打回前成绩」只读
            assertTrue(sheet.getRow(1).getCell(PRE_REDO_COLUMN).getCellStyle().getLocked());
            assertEquals(IndexedColors.GREY_25_PERCENT.getIndex(),
                    sheet.getRow(1).getCell(PRE_REDO_COLUMN).getCellStyle().getFillForegroundColor());
        }
    }

    @Test
    void export_resubmittedRecordMovesOldScoreToReadonlyColumn() throws Exception {
        record.setStatus(EduHomeworkRecordStatusEnum.RESUBMITTED);
        record.setScore(55);
        record.setComment("第 2 题再改改");

        List<String> row = exportedRow();

        assertEquals("", row.get(SCORE_COLUMN));
        assertEquals("55", row.get(PRE_REDO_COLUMN));
        assertEquals("第 2 题再改改", row.get(COMMENT_COLUMN));
        assertEquals("已重新提交", row.get(5));
    }

    @Test
    void export_scoreColumnOnlyCarriesFinalScores() throws Exception {
        record.setStatus(EduHomeworkRecordStatusEnum.REDO);
        record.setScore(40);
        List<String> redo = exportedRow();
        assertEquals("", redo.get(SCORE_COLUMN));
        assertEquals("40", redo.get(PRE_REDO_COLUMN));

        record.setStatus(EduHomeworkRecordStatusEnum.CORRECTED);
        record.setScore(66);
        List<String> corrected = exportedRow();
        assertEquals("66", corrected.get(SCORE_COLUMN));
        assertEquals("", corrected.get(PRE_REDO_COLUMN));

        // 待批改记录即使带着残留成绩也不导出到「成绩*」，否则原样导回就会定稿
        record.setStatus(EduHomeworkRecordStatusEnum.SUBMITTED);
        record.setScore(30);
        List<String> submitted = exportedRow();
        assertEquals("", submitted.get(SCORE_COLUMN));
        assertEquals("", submitted.get(PRE_REDO_COLUMN));
    }

    // ========== 预检：改动判断 ==========

    @Test
    void preview_submittedRecordCreatesPersistedCorrectionDraft() throws Exception {
        byte[] content = editExportedScore("72", "完成良好", false);

        List<EduImportPreviewRow> previews = handler.preview(content, OPTIONS);

        verify(recordMapper, never()).selectById(anyLong());
        assertEquals(1, previews.size());
        assertTrue(previews.get(0).getErrors().isEmpty());
        assertEquals("UPDATE", previews.get(0).getAction().name());
        GradebookImportRow normalized = normalized(previews.get(0));
        assertEquals(72, normalized.getScore());
        assertEquals("完成良好", normalized.getComment());
        assertEquals("00001234", normalized.getUsername());
        assertEquals("张三", normalized.getNickname());
        assertNull(normalized.getSkipReason());
    }

    @Test
    void fiftyRowsReusePreviewRecordsAndOneBatchLookupDuringCommit() throws Exception {
        List<EduHomeworkRecordDO> records = new java.util.ArrayList<>();
        List<EduHomeworkAssigneeDO> assignees = new java.util.ArrayList<>();
        List<AdminUserDO> users = new java.util.ArrayList<>();
        for (long id = 1; id <= 50; id++) {
            EduHomeworkRecordDO submitted = new EduHomeworkRecordDO();
            submitted.setId(id + 1000); submitted.setHomeworkId(100L); submitted.setStudentId(id);
            submitted.setStatus(EduHomeworkRecordStatusEnum.SUBMITTED); submitted.setSubmitTime(record.getSubmitTime());
            records.add(submitted);
            EduHomeworkAssigneeDO assigned = new EduHomeworkAssigneeDO();
            assigned.setHomeworkId(100L); assigned.setStudentId(id); assignees.add(assigned);
            AdminUserDO user = new AdminUserDO(); user.setId(id); user.setUsername("学号" + id); user.setNickname("学生" + id);
            users.add(user);
        }
        when(recordMapper.selectList(any())).thenReturn(records);
        when(recordMapper.selectByIds(anyCollection())).thenReturn(records);
        when(assigneeMapper.selectListByHomeworkId(100L)).thenReturn(assignees);
        when(adminUserService.getUserList(org.mockito.ArgumentMatchers.<Long>anyCollection())).thenReturn(users);
        byte[] content;
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(handler.export(100L)))) {
            Sheet sheet = workbook.getSheet(GradebookDataExchangeHandler.SHEET_NAME);
            for (int index = 1; index <= 50; index++) sheet.getRow(index).getCell(SCORE_COLUMN).setCellValue("72");
            content = cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils.writeWorkbook(workbook);
        }
        clearInvocations(recordMapper);
        List<EduImportPreviewRow> preview = handler.preview(content, OPTIONS);
        assertEquals(50, preview.size());
        assertTrue(preview.stream().allMatch(row -> row.getErrors().isEmpty()));
        verify(recordMapper).selectList(any());
        verify(recordMapper, never()).selectByIds(anyCollection());
        verify(recordMapper, never()).selectById(anyLong());
        clearInvocations(recordMapper);
        EduImportCommitResult result = handler.commit(batch(), preview.stream().map(GradebookDataExchangeHandlerTest::item).toList(), null);
        verify(recordMapper).selectByIds(anyCollection());
        verify(recordMapper, never()).selectById(anyLong());
        verify(recordMapper, times(50)).update(any(EduHomeworkRecordDO.class), any(Wrapper.class));
        assertEquals(50, result.updatedCount());
    }

    @Test
    void previewTamperedForeignRecordIdKeepsReadonlyMismatchErrors() throws Exception {
        byte[] content;
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(editExportedScore("72", "", false)))) {
            workbook.getSheet(GradebookDataExchangeHandler.SHEET_NAME).getRow(1)
                    .getCell(GradebookDataExchangeHandler.HEADERS.indexOf("记录 ID")).setCellValue("999");
            content = cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils.writeWorkbook(workbook);
        }
        EduHomeworkRecordDO foreign = new EduHomeworkRecordDO();
        foreign.setId(999L); foreign.setHomeworkId(200L); foreign.setStudentId(8L);
        foreign.setStatus(EduHomeworkRecordStatusEnum.SUBMITTED);
        when(recordMapper.selectByIds(anyCollection())).thenReturn(List.of(foreign));
        EduImportPreviewRow preview = handler.preview(content, OPTIONS).get(0);
        assertTrue(preview.getErrors().contains("记录不属于当前任务"));
        assertTrue(preview.getErrors().contains("记录与学号不匹配"));
        assertTrue(preview.getErrors().contains("行令牌无效或提交记录已变化"));
        verify(recordMapper).selectByIds(java.util.Set.of(999L));
        verify(recordMapper, never()).selectById(anyLong());
    }

    @Test
    void preview_rejectsTamperedTokenAndScoreAboveFullMark() throws Exception {
        byte[] content = editExportedScore("90", "", true);

        List<EduImportPreviewRow> previews = handler.preview(content, OPTIONS);

        assertTrue(previews.get(0).getErrors().stream().anyMatch(value -> value.contains("行令牌")));
        assertTrue(previews.get(0).getErrors().stream().anyMatch(value -> value.contains("任务满分")));
    }

    @Test
    void preview_reimportingUnchangedCorrectedSheetSkipsEveryRow() throws Exception {
        record.setStatus(EduHomeworkRecordStatusEnum.CORRECTED);
        record.setScore(66);
        record.setComment("思路清楚\r\n格式再规范些");

        EduImportPreviewRow preview = handler.preview(handler.export(100L), OPTIONS).get(0);

        assertTrue(preview.getErrors().isEmpty());
        assertEquals("SKIP", preview.getAction().name());
        assertEquals(GradebookImportRow.SKIP_UNCHANGED, normalized(preview).getSkipReason());
    }

    @Test
    void preview_correctedRecordWithBlankCommentKeepsCommentAndSkips() throws Exception {
        record.setStatus(EduHomeworkRecordStatusEnum.CORRECTED);
        record.setScore(66);
        record.setComment("原评语");

        EduImportPreviewRow preview = handler.preview(editExportedScore("66", "", false), OPTIONS).get(0);

        assertEquals("SKIP", preview.getAction().name());
        assertEquals(GradebookImportRow.SKIP_UNCHANGED, normalized(preview).getSkipReason());
    }

    @Test
    void preview_correctedRecordWithNewCommentOrScoreUpdates() throws Exception {
        record.setStatus(EduHomeworkRecordStatusEnum.CORRECTED);
        record.setScore(66);
        record.setComment("原评语");

        assertEquals("UPDATE", handler.preview(editExportedScore("66", "补充评语", false), OPTIONS)
                .get(0).getAction().name());
        assertEquals("UPDATE", handler.preview(editExportedScore("70", "", false), OPTIONS)
                .get(0).getAction().name());
    }

    @Test
    void preview_correctedRecordCannotBeCleared() throws Exception {
        record.setStatus(EduHomeworkRecordStatusEnum.CORRECTED);
        record.setScore(66);

        EduImportPreviewRow preview = handler.preview(editExportedScore("", "", false), OPTIONS).get(0);

        assertTrue(preview.getErrors().stream().anyMatch(value -> value.contains("不能清空")));
    }

    @Test
    void preview_pendingRecordsWithoutScoreAreSkippedAsEmpty() throws Exception {
        // 已提交待批改：导出原样导回
        EduImportPreviewRow submitted = handler.preview(handler.export(100L), OPTIONS).get(0);
        assertTrue(submitted.getErrors().isEmpty());
        assertEquals("SKIP", submitted.getAction().name());
        assertEquals(GradebookImportRow.SKIP_EMPTY, normalized(submitted).getSkipReason());

        // 已重交：新成绩册「成绩*」为空，原样导回同样跳过，不会把打回前成绩定稿
        record.setStatus(EduHomeworkRecordStatusEnum.RESUBMITTED);
        record.setScore(55);
        record.setComment("旧评语");
        EduImportPreviewRow resubmitted = handler.preview(handler.export(100L), OPTIONS).get(0);
        assertTrue(resubmitted.getErrors().isEmpty());
        assertEquals("SKIP", resubmitted.getAction().name());
        assertEquals(GradebookImportRow.SKIP_EMPTY, normalized(resubmitted).getSkipReason());
    }

    @Test
    void preview_pendingRecordWithOnlyCommentIsRejected() throws Exception {
        EduImportPreviewRow preview = handler.preview(editExportedScore("", "只写了评语", false), OPTIONS).get(0);

        assertTrue(preview.getErrors().stream().anyMatch(value -> value.contains("没有填成绩")));
    }

    @Test
    void preview_resubmittedRecordWithTypedScoreUpdatesEvenIfEqualToOldScore() throws Exception {
        record.setStatus(EduHomeworkRecordStatusEnum.RESUBMITTED);
        record.setScore(55);

        EduImportPreviewRow preview = handler.preview(editExportedScore("55", "", false), OPTIONS).get(0);

        assertTrue(preview.getErrors().isEmpty());
        assertEquals("UPDATE", preview.getAction().name());
    }

    @Test
    void preview_ignoresPreRedoScoreColumnEvenIfEdited() throws Exception {
        record.setStatus(EduHomeworkRecordStatusEnum.RESUBMITTED);
        record.setScore(55);

        byte[] content;
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(handler.export(100L)))) {
            workbook.getSheet(GradebookDataExchangeHandler.SHEET_NAME).getRow(1)
                    .getCell(PRE_REDO_COLUMN).setCellValue("99");
            content = cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils.writeWorkbook(workbook);
        }
        EduImportPreviewRow preview = handler.preview(content, OPTIONS).get(0);

        assertTrue(preview.getErrors().isEmpty());
        assertEquals("SKIP", preview.getAction().name());
        assertEquals(GradebookImportRow.SKIP_EMPTY, normalized(preview).getSkipReason());
    }

    @Test
    void preview_redoRecordWaitingForStudentIsSkipped() throws Exception {
        record.setStatus(EduHomeworkRecordStatusEnum.REDO);
        record.setScore(40);

        EduImportPreviewRow untouched = handler.preview(handler.export(100L), OPTIONS).get(0);
        assertTrue(untouched.getErrors().isEmpty());
        assertEquals(GradebookImportRow.SKIP_AWAITING_RESUBMIT, normalized(untouched).getSkipReason());

        EduImportPreviewRow typed = handler.preview(editExportedScore("70", "", false), OPTIONS).get(0);
        assertTrue(typed.getErrors().stream().anyMatch(value -> value.contains("已打回")));
    }

    // ========== 预检：旧成绩册兼容 ==========

    @Test
    void preview_legacySheetWithoutPreRedoColumnStillImports() throws Exception {
        byte[] legacy = legacySheet("72", "完成良好");

        EduImportPreviewRow preview = handler.preview(legacy, OPTIONS).get(0);

        assertTrue(preview.getErrors().isEmpty(), preview.getErrors().toString());
        assertEquals("UPDATE", preview.getAction().name());
        assertEquals(72, normalized(preview).getScore());
    }

    @Test
    void preview_legacySheetCarryingOldScoreOfResubmittedRecordDoesNotFinalize() throws Exception {
        record.setStatus(EduHomeworkRecordStatusEnum.RESUBMITTED);
        record.setScore(55);
        record.setComment("旧评语");

        EduImportPreviewRow unchanged = handler.preview(legacySheet("55", "旧评语"), OPTIONS).get(0);
        assertTrue(unchanged.getErrors().isEmpty());
        assertEquals("SKIP", unchanged.getAction().name());
        assertEquals(GradebookImportRow.SKIP_PRE_REDO_SCORE, normalized(unchanged).getSkipReason());

        EduImportPreviewRow regraded = handler.preview(legacySheet("70", "旧评语"), OPTIONS).get(0);
        assertEquals("UPDATE", regraded.getAction().name());
    }

    @Test
    void preview_sheetMissingRequiredHeaderIsRejectedWithHeaderName() throws Exception {
        byte[] content;
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(handler.export(100L)))) {
            workbook.getSheet(GradebookDataExchangeHandler.SHEET_NAME).getRow(0)
                    .getCell(TOKEN_COLUMN).setCellValue("令牌");
            content = cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils.writeWorkbook(workbook);
        }

        assertServiceException(() -> handler.preview(content, OPTIONS), GRADEBOOK_HEADER_INVALID, "行令牌");
    }

    // ========== 提交 ==========

    @Test
    void commit_updatesScoreAndNotifiesStudent() throws Exception {
        EduImportBatchItemDO item = item(handler.preview(editExportedScore("72", "完成良好", false), OPTIONS).get(0));

        EduImportCommitResult result = handler.commit(batch(), List.of(item), null);
        verify(recordMapper).selectByIds(anyCollection());
        verify(recordMapper, never()).selectById(anyLong());

        assertEquals(1, result.updatedCount());
        verify(recordMapper).update(argThat((EduHomeworkRecordDO update) ->
                update.getScore().equals(72) && update.getStatus().equals(EduHomeworkRecordStatusEnum.CORRECTED)
                        && update.getCorrectType().equals(1)), any(Wrapper.class));
        verify(historyService).append(eq(record), eq("GRADEBOOK_IMPORT"), eq(2), eq(72), eq("完成良好"),
                org.mockito.ArgumentMatchers.nullable(LocalDateTime.class), org.mockito.ArgumentMatchers.nullable(Long.class));
        verify(aiReviewService).markTeacherReviewed(11L, 72, "完成良好");
        verify(businessNotifyService).sendToUser(eq(7L), eq(EduBusinessNotifyService.HOMEWORK_CORRECTED),
                any(), eq("/student/homework/feedback/100"));
    }

    @Test
    void commit_allowsRegradingCorrectedRecord() throws Exception {
        record.setStatus(EduHomeworkRecordStatusEnum.CORRECTED);
        record.setScore(60);
        EduImportBatchItemDO item = item(handler.preview(editExportedScore("72", "复评", false), OPTIONS).get(0));

        handler.commit(batch(), List.of(item), null);

        verify(historyService).append(eq(record), eq("GRADEBOOK_IMPORT"), eq(2), eq(72), eq("复评"),
                org.mockito.ArgumentMatchers.nullable(LocalDateTime.class), org.mockito.ArgumentMatchers.nullable(Long.class));
    }

    @Test
    void commit_skippedRowsWriteNothingAndSendNothing() throws Exception {
        record.setStatus(EduHomeworkRecordStatusEnum.CORRECTED);
        record.setScore(66);
        EduImportBatchItemDO item = item(handler.preview(handler.export(100L), OPTIONS).get(0));
        assertEquals("SKIP", item.getAction());
        // 预检后记录又有变化：跳过的行不受影响
        record.setComment("另一位老师刚改过");

        EduImportCommitResult result = handler.commit(batch(), List.of(item), null);

        assertEquals(0, result.updatedCount());
        assertEquals(1, result.skippedCount());
        verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(Wrapper.class));
        verify(recordMapper, never()).updateById(any(EduHomeworkRecordDO.class));
        verifyNoInteractions(historyService, aiReviewService, businessNotifyService);
    }

    @Test
    void commit_notifiesOnlyAfterTransactionCommits() throws Exception {
        EduImportBatchItemDO item = item(handler.preview(editExportedScore("72", "完成良好", false), OPTIONS).get(0));
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        handler.commit(batch(), List.of(item), null);

        verify(recordMapper).update(any(EduHomeworkRecordDO.class), any(Wrapper.class));
        verifyNoInteractions(businessNotifyService);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());

        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(businessNotifyService).sendToUser(eq(7L), eq(EduBusinessNotifyService.HOMEWORK_CORRECTED),
                argThat(params -> Integer.valueOf(72).equals(params.get("score"))
                        && "完成良好".equals(params.get("comment"))), eq("/student/homework/feedback/100"));
    }

    @Test
    void commit_rolledBackImportSendsNoNotification() throws Exception {
        EduImportBatchItemDO item = item(handler.preview(editExportedScore("72", "完成良好", false), OPTIONS).get(0));
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        handler.commit(batch(), List.of(item), null);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(businessNotifyService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void commit_commentEditBetweenSnapshotCheckAndUpdateFailsVersionCondition() throws Exception {
        LocalDateTime previewVersion = LocalDateTime.of(2026, 10, 7, 9, 30, 15, 123_000_000);
        record.setUpdateTime(previewVersion);
        record.setComment("原评语");
        EduImportBatchItemDO item = item(handler.preview(editExportedScore("72", "导入评语", false), OPTIONS).get(0));
        // 模拟快照校验通过后，另一事务只改评语（成绩、状态与提交时间均未变）。
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(Wrapper.class))).thenAnswer(invocation -> {
            LambdaUpdateWrapper<EduHomeworkRecordDO> condition = invocation.getArgument(1);
            assertTrue(condition.getSqlSegment().contains("update_time ="));
            assertTrue(condition.getParamNameValuePairs().containsValue(previewVersion));
            // 在线修改会写入新版本，导入的旧版本条件无法匹配。
            LocalDateTime editedVersion = previewVersion.plusNanos(1_000_000);
            return condition.getParamNameValuePairs().containsValue(editedVersion) ? 1 : 0;
        });

        assertServiceException(() -> handler.commit(batch(), List.of(item), null), GRADEBOOK_RECORD_CHANGED, 2);

        verifyNoInteractions(historyService, aiReviewService, businessNotifyService);
    }

    @Test
    void commit_versionChangeAfterPreviewIsRejectedEvenWithSameVisibleFields() throws Exception {
        LocalDateTime previewVersion = LocalDateTime.of(2026, 10, 7, 9, 30, 15, 123_000_000);
        record.setUpdateTime(previewVersion);
        EduImportBatchItemDO item = item(handler.preview(editExportedScore("72", "完成良好", false), OPTIONS).get(0));
        record.setUpdateTime(previewVersion.plusNanos(1_000_000));

        assertServiceException(() -> handler.commit(batch(), List.of(item), null), GRADEBOOK_RECORD_CHANGED, 2);

        verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(Wrapper.class));
        verifyNoInteractions(historyService, aiReviewService, businessNotifyService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void commit_nullPreviewVersionUsesNullSafeCondition() throws Exception {
        EduImportBatchItemDO item = item(handler.preview(editExportedScore("72", "完成良好", false), OPTIONS).get(0));

        handler.commit(batch(), List.of(item), null);

        org.mockito.ArgumentCaptor<LambdaUpdateWrapper<EduHomeworkRecordDO>> condition =
                org.mockito.ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(recordMapper).update(any(EduHomeworkRecordDO.class), condition.capture());
        assertTrue(condition.getValue().getSqlSegment().contains("update_time IS NULL"));
    }

    @Test
    void commit_concurrentChangeAbortsWholeBatchWithoutNotifying() throws Exception {
        EduImportBatchItemDO item = item(handler.preview(editExportedScore("72", "完成良好", false), OPTIONS).get(0));
        when(recordMapper.update(any(EduHomeworkRecordDO.class), any(Wrapper.class))).thenReturn(0);

        assertServiceException(() -> handler.commit(batch(), List.of(item), null), GRADEBOOK_RECORD_CHANGED, 2);

        verifyNoInteractions(historyService, aiReviewService, businessNotifyService);
    }

    @Test
    void commit_recordChangedAfterPreviewIsRejectedForUpdatedRow() throws Exception {
        EduImportBatchItemDO item = item(handler.preview(editExportedScore("72", "完成良好", false), OPTIONS).get(0));
        record.setStatus(EduHomeworkRecordStatusEnum.CORRECTED);
        record.setScore(80);

        assertServiceException(() -> handler.commit(batch(), List.of(item), null), GRADEBOOK_RECORD_CHANGED, 2);

        verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(Wrapper.class));
        verifyNoInteractions(businessNotifyService);
    }

    @Test
    void crossCourseTeacherIsRejectedForExportPreviewAndCommit() {
        when(aiBusinessAccessService.requireHomeworkAccess(100L)).thenThrow(exception(FORBIDDEN));
        when(aiBusinessAccessService.requireHomeworkViewAccess(100L)).thenThrow(exception(FORBIDDEN));

        assertServiceException(() -> handler.export(100L), FORBIDDEN);
        assertServiceException(() -> handler.preview(new byte[0], OPTIONS), FORBIDDEN);
        assertServiceException(() -> handler.commit(batch(), List.of(new EduImportBatchItemDO()), null), FORBIDDEN);

        verify(recordMapper, never()).selectList(any());
        verify(recordMapper, never()).updateById(any(EduHomeworkRecordDO.class));
        verify(recordMapper, never()).update(any(EduHomeworkRecordDO.class), any(Wrapper.class));
        verify(businessNotifyService, never()).sendToUser(any(), any(), any(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"owner,0", "owner,1", "admin,0", "admin,1", "leader,0", "leader,1",
            "member,0", "member,1", "unrelated,0", "unrelated,1", "student,0", "student,1", "anonymous,0", "anonymous,1"})
    void exportUsesRealViewAccessForRolesAndArchivedBindings(String role, int bindingStatus) throws Exception {
        Long userId = switch (role) { case "owner" -> 7L; case "leader" -> 8L; case "anonymous" -> null; default -> 9L; };
        var homeworkMapper = mock(cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper.class);
        var bindings = mock(cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper.class);
        var roles = mock(cn.nus.zknu.module.system.api.permission.EduRoleApi.class);
        var scope = new cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService();
        ReflectionTestUtils.setField(scope, "capacityCourseApi", courseMapper);
        ReflectionTestUtils.setField(scope, "eduRoleApi", roles);
        var access = new EduAiBusinessAccessService();
        ReflectionTestUtils.setField(access, "dataScopeService", scope);
        ReflectionTestUtils.setField(access, "homeworkMapper", homeworkMapper);
        ReflectionTestUtils.setField(access, "classCourseMapper", bindings);
        ReflectionTestUtils.setField(handler, "aiBusinessAccessService", access);
        homework.setTeacherId(7L);
        when(homeworkMapper.selectById(100L)).thenReturn(homework);
        var course = new CapacityCourseRespDTO(); course.setId(5L); course.setTeacherId(8L);
        course.setCourseName("Java 实训"); course.setStatus(0);
        if (userId != null) when(courseMapper.getCourse(5L)).thenReturn(course);
        lenient().when(roles.isCurrentUserAdmin()).thenReturn(role.equals("admin"));
        lenient().when(courseMapper.isCourseMember(5L, userId)).thenReturn(role.equals("member"));
        var binding = new cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO();
        binding.setStatus(bindingStatus);
        lenient().when(bindings.selectOne(any())).thenReturn(bindingStatus == 0 ? binding : null);
        try (var security = mockStatic(cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.class)) {
            security.when(cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils::getLoginUserId).thenReturn(userId);
            if (role.equals("unrelated") || role.equals("student") || role.equals("anonymous")) {
                assertServiceException(() -> handler.export(100L), FORBIDDEN);
                verifyNoInteractions(assigneeMapper, recordMapper, adminUserService, classService);
            } else {
                try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(handler.export(100L)))) {
                    var sheet = workbook.getSheet(GradebookDataExchangeHandler.SHEET_NAME);
                    assertEquals(GradebookDataExchangeHandler.HEADERS.size(), sheet.getRow(0).getLastCellNum());
                    assertEquals("00001234", sheet.getRow(1).getCell(3).getStringCellValue());
                    assertEquals("张三", sheet.getRow(1).getCell(4).getStringCellValue());
                }
                verify(assigneeMapper).selectListByHomeworkId(100L);
                verify(recordMapper).selectList(any());
            }
            verify(homeworkMapper).selectById(100L);
            verifyNoMoreInteractions(homeworkMapper);
            verifyNoInteractions(bindings);
            verify(recordMapper, never()).update(any(), any());
        }
    }

    @Test
    void publicReadAllUserCanExportButCannotPreviewOrCommitGrades() throws Exception {
        var mapper = mock(cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper.class);
        var scope = mock(cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService.class);
        var access = new EduAiBusinessAccessService();
        ReflectionTestUtils.setField(access, "dataScopeService", scope);
        ReflectionTestUtils.setField(access, "homeworkMapper", mapper);
        ReflectionTestUtils.setField(handler, "aiBusinessAccessService", access);
        homework.setTeacherId(8L);
        when(mapper.selectById(100L)).thenReturn(homework);
        when(scope.isPublicCourse(5L)).thenReturn(true);
        when(scope.canViewAllPublic(5L)).thenReturn(true);
        try (var login = mockStatic(cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.class)) {
            login.when(cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            assertTrue(handler.export(100L).length > 0);
            clearInvocations(recordMapper, assigneeMapper, businessNotifyService);
            assertServiceException(() -> handler.preview(new byte[0], OPTIONS), FORBIDDEN);
            assertServiceException(() -> handler.commit(batch(), List.of(), null), FORBIDDEN);
            verifyNoInteractions(recordMapper, assigneeMapper, businessNotifyService);
        }
    }

    @Test
    void archivedExportDoesNotAuthorizeImportPreviewOrCommit() {
        when(aiBusinessAccessService.requireHomeworkAccess(100L)).thenThrow(exception(
                cn.nus.zknu.module.edu.enums.ErrorCodeConstants.CLASS_COURSE_NOT_EXISTS));
        assertTrue(handler.export(100L).length > 0);
        clearInvocations(assigneeMapper, recordMapper, classService, adminUserService);
        assertServiceException(() -> handler.preview(new byte[0], OPTIONS),
                cn.nus.zknu.module.edu.enums.ErrorCodeConstants.CLASS_COURSE_NOT_EXISTS);
        assertServiceException(() -> handler.commit(batch(), List.of(), null),
                cn.nus.zknu.module.edu.enums.ErrorCodeConstants.CLASS_COURSE_NOT_EXISTS);
        verify(aiBusinessAccessService).requireHomeworkViewAccess(100L);
        verify(aiBusinessAccessService, times(2)).requireHomeworkAccess(100L);
        verify(securityFrameworkService).hasPermission("edu:homework-record:query");
        verify(securityFrameworkService, times(2)).hasPermission("edu:homework-record:correct");
        verifyNoInteractions(assigneeMapper, recordMapper, classService, adminUserService, businessNotifyService);
    }

    @Test
    void readOnlyFunctionPermissionAllowsExportButCannotPreviewOrCommit() {
        when(securityFrameworkService.hasPermission("edu:homework-record:correct")).thenReturn(false);
        assertTrue(handler.export(100L).length > 0);
        assertServiceException(() -> handler.preview(new byte[0], OPTIONS), FORBIDDEN);
        assertServiceException(() -> handler.commit(batch(), List.of(), null), FORBIDDEN);
        verify(aiBusinessAccessService).requireHomeworkViewAccess(100L);
        verify(aiBusinessAccessService, never()).requireHomeworkAccess(any());
        verify(recordMapper, never()).update(any(), any());
    }

    @Test
    void missingQueryFunctionPermissionRejectsExportBeforeAccessOrReads() {
        when(securityFrameworkService.hasPermission("edu:homework-record:query")).thenReturn(false);
        assertServiceException(() -> handler.export(100L), FORBIDDEN);
        verifyNoInteractions(aiBusinessAccessService, recordMapper, assigneeMapper, classService, adminUserService);
    }

    // ========== 工具 ==========

    /** 导出后第一行学生数据的各列文字。 */
    private List<String> exportedRow() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(handler.export(100L)))) {
            Row row = workbook.getSheet(GradebookDataExchangeHandler.SHEET_NAME).getRow(1);
            List<String> values = new java.util.ArrayList<>();
            for (int column = 0; column < GradebookDataExchangeHandler.HEADERS.size(); column++) {
                values.add(row.getCell(column).getStringCellValue());
            }
            return values;
        }
    }

    private byte[] editExportedScore(String score, String comment, boolean tamperToken) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new java.io.ByteArrayInputStream(handler.export(100L)))) {
            Row row = workbook.getSheet(GradebookDataExchangeHandler.SHEET_NAME).getRow(1);
            row.getCell(SCORE_COLUMN).setCellValue(score);
            row.getCell(COMMENT_COLUMN).setCellValue(comment);
            if (tamperToken) row.getCell(TOKEN_COLUMN).setCellValue("tampered");
            return cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils.writeWorkbook(workbook);
        }
    }

    /**
     * 模拟 V1.0.1.6 第二轮之前导出的成绩册：没有「打回前成绩」列，「成绩*」里是记录当前的成绩。
     * comment 为 null 时沿用导出的评语。
     */
    private byte[] legacySheet(String score, String comment) throws Exception {
        try (XSSFWorkbook source = new XSSFWorkbook(new java.io.ByteArrayInputStream(handler.export(100L)));
             XSSFWorkbook legacy = new XSSFWorkbook()) {
            Sheet from = source.getSheet(GradebookDataExchangeHandler.SHEET_NAME);
            Sheet to = legacy.createSheet(GradebookDataExchangeHandler.SHEET_NAME);
            for (int rowIndex = 0; rowIndex <= 1; rowIndex++) {
                Row fromRow = from.getRow(rowIndex);
                Row toRow = to.createRow(rowIndex);
                int target = 0;
                for (int column = 0; column < GradebookDataExchangeHandler.HEADERS.size(); column++) {
                    if (column == PRE_REDO_COLUMN) continue;
                    String value = fromRow.getCell(column).getStringCellValue();
                    if (rowIndex == 1 && column == SCORE_COLUMN) value = score;
                    if (rowIndex == 1 && column == COMMENT_COLUMN && comment != null) value = comment;
                    toRow.createCell(target++).setCellValue(value);
                }
            }
            assertEquals(GradebookDataExchangeHandler.LEGACY_HEADERS.size(), to.getRow(0).getLastCellNum());
            return cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils.writeWorkbook(legacy);
        }
    }

    private static GradebookImportRow normalized(EduImportPreviewRow preview) {
        return JSONUtil.toBean(preview.getNormalizedData(), GradebookImportRow.class);
    }

    private static EduImportBatchDO batch() {
        EduImportBatchDO batch = new EduImportBatchDO();
        batch.setOptionsJson(OPTIONS);
        return batch;
    }

    private static EduImportBatchItemDO item(EduImportPreviewRow preview) {
        EduImportBatchItemDO item = new EduImportBatchItemDO();
        item.setRowNumber(preview.getRowNumber());
        item.setNormalizedData(preview.getNormalizedData());
        item.setBeforeData(preview.getBeforeData());
        item.setAction(preview.getAction().name());
        return item;
    }

}
