package cn.nus.zknu.module.edu.service.dataexchange.gradebook;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.nus.zknu.framework.security.core.service.SecurityFrameworkService;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAssigneeDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchDO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchItemDO;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.enums.importbatch.EduImportRowActionEnum;
import cn.nus.zknu.module.edu.service.aireview.EduAiBusinessAccessService;
import cn.nus.zknu.module.edu.service.aireview.EduHomeworkAiReviewService;
import cn.nus.zknu.module.edu.service.clazz.EduClassService;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataExchangeTemplate;
import cn.nus.zknu.module.edu.service.dataexchange.EduImportPreviewUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduImportSnapshotUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils;
import cn.nus.zknu.module.edu.service.importbatch.EduImportCommitResult;
import cn.nus.zknu.module.edu.service.importbatch.EduImportHandler;
import cn.nus.zknu.module.edu.service.importbatch.EduImportPreviewRow;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkRecordHistoryService;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;

@Component
@Slf4j
public class GradebookDataExchangeHandler implements EduImportHandler, EduDataExchangeTemplate {

    public static final String BIZ_TYPE = "HOMEWORK_GRADEBOOK";
    public static final String VERSION = "V1";
    public static final String SHEET_NAME = "成绩录入";
    /** 已打回、已重交记录的原成绩，只读参考，导入时不读取。 */
    public static final String PRE_REDO_SCORE_HEADER = "打回前成绩";
    public static final String SCORE_HEADER = "成绩*";
    public static final String COMMENT_HEADER = "教师评语";
    public static final List<String> HEADERS = List.of(
            "任务编号", "任务名称", "班级", "学号", "姓名", "提交状态", "满分", "达标线",
            PRE_REDO_SCORE_HEADER, SCORE_HEADER, COMMENT_HEADER, "记录 ID", "行令牌", "模板校验值");
    /** V1.0.1.6 第二轮之前导出的成绩册：没有「打回前成绩」列，重交记录的「成绩*」里是打回前的旧成绩。 */
    static final List<String> LEGACY_HEADERS = HEADERS.stream()
            .filter(header -> !PRE_REDO_SCORE_HEADER.equals(header)).toList();

    private static final int SCORE_COLUMN = HEADERS.indexOf(SCORE_HEADER);
    private static final int COMMENT_COLUMN = HEADERS.indexOf(COMMENT_HEADER);
    private static final Set<Integer> HIDDEN_COLUMNS = Set.of(HEADERS.indexOf("记录 ID"),
            HEADERS.indexOf("行令牌"), HEADERS.indexOf("模板校验值"));
    private static final Set<Integer> TEXT_COLUMNS = Set.of(HEADERS.indexOf("任务编号"), HEADERS.indexOf("学号"),
            HEADERS.indexOf("记录 ID"), HEADERS.indexOf("行令牌"), HEADERS.indexOf("模板校验值"));
    /** 成绩册可以写入成绩的状态：已提交待批改、已批改（复评）、已重交。 */
    private static final List<Integer> IMPORTABLE_STATUSES = EduHomeworkRecordStatusEnum.CORRECTABLE;

    @Resource
    private SecurityFrameworkService securityFrameworkService;
    @Resource
    private EduAiBusinessAccessService aiBusinessAccessService;
    @Resource
    private EduHomeworkAssigneeMapper assigneeMapper;
    @Resource
    private EduHomeworkRecordMapper recordMapper;
    @Resource
    private EduClassService classService;
    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private AdminUserService adminUserService;
    @Resource
    private EduHomeworkAiReviewService aiReviewService;
    @Resource
    private EduHomeworkRecordHistoryService historyService;
    @Resource
    private EduBusinessNotifyService businessNotifyService;
    @Resource
    private cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy metricPolicy;
    /** 事务提交后在新事务里发通知；单测里为空时直接发送。 */
    @Resource
    private PlatformTransactionManager transactionManager;

    @Override
    public String getBizType() {
        return BIZ_TYPE;
    }

    @Override
    public String getTemplateVersion() {
        return VERSION;
    }

    @Override
    public String getFileName() {
        return "任务成绩册导入模板_V1.xlsx";
    }

    @Override
    public void validatePermission() {
        if (!securityFrameworkService.hasPermission("edu:homework-record:correct")) {
            throw exception(FORBIDDEN);
        }
    }

    @Override
    public byte[] generate() {
        try (XSSFWorkbook workbook = baseWorkbook()) {
            configureProtectedSheet(workbook.getSheet(SHEET_NAME), workbook);
            return EduXlsxWorkbookUtils.writeWorkbook(workbook);
        } catch (Exception ex) {
            if (ex instanceof cn.nus.zknu.framework.common.exception.ServiceException serviceException) {
                throw serviceException;
            }
            throw exception(IMPORT_PREVIEW_INVALID);
        }
    }

    @Override
    public List<EduImportPreviewRow> preview(byte[] fileContent, String optionsJson) {
        Long homeworkId = parseHomeworkId(optionsJson);
        EduHomeworkDO homework = getAccessibleHomework(homeworkId, true);
        EduClassDO clazz = classService.getClass(homework.getClassId());
        if (clazz == null) throw exception(IMPORT_DATA_CHANGED);
        Set<Long> assignedStudentIds = assigneeMapper.selectListByHomeworkId(homeworkId).stream()
                .map(EduHomeworkAssigneeDO::getStudentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, AdminUserDO> assignedStudents = adminUserService.getUserList(assignedStudentIds).stream()
                .filter(user -> StrUtil.isNotBlank(user.getUsername()))
                .collect(Collectors.toMap(AdminUserDO::getUsername, Function.identity(), (left, right) -> left));
        List<EduHomeworkRecordDO> loadedRecords = listRecords(homeworkId);
        Map<Long, EduHomeworkRecordDO> recordsById = loadedRecords.stream()
                .collect(Collectors.toMap(EduHomeworkRecordDO::getId, Function.identity()));
        Map<Long, EduHomeworkRecordDO> recordsByStudent = loadedRecords.stream()
                .collect(Collectors.toMap(EduHomeworkRecordDO::getStudentId, Function.identity(),
                        (left, right) -> latest(left, right)));
        String checksum = templateChecksum(homework);
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(fileContent)) {
            GradebookSheet sheet = readSheet(workbook);
            // 正常文件复用任务快照；被篡改的外任务 ID 也批量读取，保留原只读字段错误提示。
            Set<Long> extraIds = sheet.rows().stream()
                    .map(data -> parseLong(data.get("记录 ID"), "记录 ID", new ArrayList<>(), false))
                    .filter(Objects::nonNull).filter(id -> !recordsById.containsKey(id)).collect(Collectors.toSet());
            if (!extraIds.isEmpty()) recordMapper.selectByIds(extraIds).forEach(record -> recordsById.put(record.getId(), record));
            List<EduImportPreviewRow> result = new ArrayList<>();
            for (EduXlsxWorkbookUtils.RowData data : sheet.rows()) {
                List<String> errors = new ArrayList<>();
                GradebookImportRow row = parseRow(data, errors);
                if (!Objects.equals(row.getHomeworkId(), homeworkId)) errors.add("任务编号与上传入口不一致");
                if (!Objects.equals(row.getTemplateChecksum(), checksum)) errors.add("模板校验值无效或任务配置已变化");

                AdminUserDO student = assignedStudents.get(data.get("学号"));
                if (student == null || !assignedStudentIds.contains(student.getId())) {
                    errors.add("学号不在当前任务学生快照中");
                } else {
                    row.setStudentId(student.getId());
                }
                EduHomeworkRecordDO expectedRecord = student == null ? null : recordsByStudent.get(student.getId());
                EduHomeworkRecordDO record = row.getRecordId() == null ? null : recordsById.get(row.getRecordId());
                if (row.getRecordId() == null && expectedRecord != null) errors.add("记录 ID 已被移除");
                validateReadonlyFields(data, homework, clazz, record, student, row, errors);

                Map<String, Object> before = recordSnapshot(record);
                EduImportRowActionEnum action = determineAction(row, record, homework, sheet.legacyLayout(), errors);
                Map<String, Object> after = action == EduImportRowActionEnum.UPDATE
                        ? correctedSnapshot(record, row) : before;
                result.add(EduImportPreviewRow.builder().sheetName(SHEET_NAME).rowNumber(data.rowNumber())
                        .businessKey("GRADE:" + safe(row.getRecordId())).normalizedData(JSONUtil.toJsonStr(row))
                        .action(action).errors(errors).beforeData(JSONUtil.toJsonStr(before))
                        .afterData(JSONUtil.toJsonStr(after)).build());
            }
            EduImportPreviewUtils.markDuplicates(result,
                    item -> JSONUtil.toBean(item.getNormalizedData(), GradebookImportRow.class).getRecordId(),
                    "文件内记录 ID 重复");
            return result;
        } catch (cn.nus.zknu.framework.common.exception.ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw exception(IMPORT_PREVIEW_INVALID);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EduImportCommitResult commit(EduImportBatchDO batch, List<EduImportBatchItemDO> validItems,
                                        String initialPassword) {
        Long homeworkId = parseHomeworkId(batch.getOptionsJson());
        EduHomeworkDO homework = getAccessibleHomework(homeworkId, true);
        String checksum = templateChecksum(homework);
        int updated = 0;
        int skipped = 0;
        List<CorrectedNotice> notices = new ArrayList<>();
        Set<Long> recordIds = validItems.stream().filter(item -> !EduImportRowActionEnum.SKIP.name().equals(item.getAction()))
                .map(item -> JSONUtil.toBean(item.getNormalizedData(), GradebookImportRow.class).getRecordId())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, EduHomeworkRecordDO> recordsById = recordIds.isEmpty() ? Map.of()
                : recordMapper.selectByIds(recordIds).stream()
                    .collect(Collectors.toMap(EduHomeworkRecordDO::getId, Function.identity()));
        for (EduImportBatchItemDO item : validItems) {
            GradebookImportRow row = JSONUtil.toBean(item.getNormalizedData(), GradebookImportRow.class);
            if (!Objects.equals(row.getHomeworkId(), homeworkId)
                    || !Objects.equals(row.getTemplateChecksum(), checksum)) {
                throw exception(IMPORT_DATA_CHANGED);
            }
            // 跳过的行不写任何数据，预检后记录有变化（例如学生刚重交）也不影响本次导入
            if (EduImportRowActionEnum.SKIP.name().equals(item.getAction())) {
                skipped++;
                continue;
            }
            EduHomeworkRecordDO record = row.getRecordId() == null ? null : recordsById.get(row.getRecordId());
            if (!EduImportSnapshotUtils.matches(item.getBeforeData(), recordSnapshot(record))) {
                throw exception(GRADEBOOK_RECORD_CHANGED, item.getRowNumber());
            }
            if (record == null || !Objects.equals(record.getHomeworkId(), homeworkId)
                    || !IMPORTABLE_STATUSES.contains(record.getStatus())
                    || !Objects.equals(row.getRowToken(), rowToken(record))
                    || row.getScore() == null || row.getScore() < 0
                    || row.getScore() > effectiveFullScore(homework)) {
                throw exception(IMPORT_DATA_CHANGED);
            }
            EduHomeworkRecordDO update = new EduHomeworkRecordDO();
            update.setScore(row.getScore());
            update.setComment(row.getComment());
            update.setCorrectType(1);
            update.setCorrectTime(LocalDateTime.now());
            update.setStatus(EduHomeworkRecordStatusEnum.CORRECTED);
            // 条件更新：毫秒级 update_time 与预检快照一致才写入，评语等字段的并发修改也会使版本失效。
            if (recordMapper.update(update, unchangedSincePreview(record)) != 1) {
                throw exception(GRADEBOOK_RECORD_CHANGED, item.getRowNumber());
            }
            historyService.append(record, "GRADEBOOK_IMPORT", EduHomeworkRecordStatusEnum.CORRECTED,
                    row.getScore(), row.getComment(), record.getRedoDeadline(), null);
            aiReviewService.markTeacherReviewed(record.getId(), row.getScore(), row.getComment());
            notices.add(new CorrectedNotice(record.getStudentId(), row.getScore(), row.getComment()));
            updated++;
        }
        notifyAfterCommit(homework, notices);
        return new EduImportCommitResult(0, updated, skipped);
    }

    public byte[] export(Long homeworkId) {
        EduHomeworkDO homework = getAccessibleHomework(homeworkId, false);
        EduClassDO clazz = classService.getClass(homework.getClassId());
        CapacityCourseRespDTO course = capacityCourseApi.getCourse(homework.getCourseId());
        if (clazz == null || course == null) throw exception(IMPORT_DATA_CHANGED);
        List<EduHomeworkAssigneeDO> assignees = assigneeMapper.selectListByHomeworkId(homeworkId);
        Set<Long> studentIds = assignees.stream().map(EduHomeworkAssigneeDO::getStudentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserDO> students = adminUserService.getUserList(studentIds).stream()
                .collect(Collectors.toMap(AdminUserDO::getId, Function.identity()));
        Map<Long, EduHomeworkRecordDO> records = listRecords(homeworkId).stream()
                .collect(Collectors.toMap(EduHomeworkRecordDO::getStudentId, Function.identity(), this::latest));
        String checksum = templateChecksum(homework);
        try (XSSFWorkbook workbook = baseWorkbook()) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            int rowIndex = 1;
            for (EduHomeworkAssigneeDO assignee : assignees) {
                AdminUserDO student = students.get(assignee.getStudentId());
                if (student == null) continue;
                EduHomeworkRecordDO record = records.get(student.getId());
                write(sheet.createRow(rowIndex++), homework.getId(), homework.getHomeworkName(), clazz.getClassName(),
                        student.getUsername(), student.getNickname(), statusLabel(record), effectiveFullScore(homework),
                        effectivePassScore(homework), exportedPreRedoScore(record), exportedScore(record),
                        record == null ? null : record.getComment(), record == null ? null : record.getId(),
                        record == null ? null : rowToken(record), checksum);
            }
            configureProtectedSheet(sheet, workbook);
            return EduXlsxWorkbookUtils.writeWorkbook(workbook);
        } catch (Exception ex) {
            if (ex instanceof cn.nus.zknu.framework.common.exception.ServiceException serviceException) {
                throw serviceException;
            }
            throw exception(IMPORT_PREVIEW_INVALID);
        }
    }

    /**
     * 「成绩*」列只放已定稿的成绩：已批改记录导出当前成绩；待批改、已打回、已重交一律留空，
     * 避免把打回前的旧成绩原样导回去，替教师给重交的提交定了稿。
     */
    static Integer exportedScore(EduHomeworkRecordDO record) {
        if (record == null || !Objects.equals(record.getStatus(), EduHomeworkRecordStatusEnum.CORRECTED)) {
            return null;
        }
        return record.getScore();
    }

    /** 已打回、已重交记录的原成绩放进只读的「打回前成绩」列，供教师参考。 */
    static Integer exportedPreRedoScore(EduHomeworkRecordDO record) {
        if (record == null) {
            return null;
        }
        return Objects.equals(record.getStatus(), EduHomeworkRecordStatusEnum.REDO)
                || Objects.equals(record.getStatus(), EduHomeworkRecordStatusEnum.RESUBMITTED)
                ? record.getScore() : null;
    }

    private XSSFWorkbook baseWorkbook() {
        return EduXlsxWorkbookUtils.open(EduXlsxWorkbookUtils.createTemplate(BIZ_TYPE, VERSION, List.of(
                        "请从具体任务的「导出成绩册」下载带学生名单的成绩册后填写。",
                        "只填写「成绩*」和「教师评语」，其他列只读，隐藏的校验列不要改动。",
                        "成绩和评语都没改的行、待批改但没填成绩的行会跳过，不写记录，也不通知学生。",
                        "已打回或已重新提交的记录，原成绩显示在「打回前成绩」，只供参考，导入时不读取；"
                                + "要给重新提交的记录定稿，请在「成绩*」里填写。",
                        "预检确认后才写入正式成绩，学生在导入完成后收到批改通知。"),
                List.of(new EduXlsxWorkbookUtils.SheetSpec(SHEET_NAME, HEADERS, TEXT_COLUMNS)),
                List.of(new EduXlsxWorkbookUtils.DictionaryEntry("提交状态", "未提交 / 待批改 / 已批改 / 已打回 / 已重新提交",
                        "待批改、已批改和已重新提交可录入成绩"))));
    }

    private void configureProtectedSheet(Sheet sheet, Workbook workbook) {
        CellStyle unlockedDefault = workbook.createCellStyle();
        unlockedDefault.setLocked(false);
        CellStyle unlocked = workbook.createCellStyle();
        unlocked.setLocked(false);
        unlocked.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        unlocked.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CellStyle locked = workbook.createCellStyle();
        locked.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        locked.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        sheet.setDefaultColumnStyle(SCORE_COLUMN, unlockedDefault);
        sheet.setDefaultColumnStyle(COMMENT_COLUMN, unlockedDefault);
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            for (int column = 0; column < HEADERS.size(); column++) {
                Cell cell = row.getCell(column);
                if (cell == null) cell = row.createCell(column, CellType.STRING);
                cell.setCellStyle(column == SCORE_COLUMN || column == COMMENT_COLUMN ? unlocked : locked);
            }
        }
        HIDDEN_COLUMNS.forEach(column -> sheet.setColumnHidden(column, true));
        sheet.protectSheet("nus-gradebook");
    }

    /** 按表头名读取，兼容没有「打回前成绩」列的旧成绩册；「打回前成绩」只读，不进入导入数据。 */
    static GradebookSheet readSheet(Workbook workbook) {
        Sheet sheet = workbook.getSheet(SHEET_NAME);
        Row headerRow = sheet == null ? null : sheet.getRow(0);
        if (headerRow == null) {
            throw exception(IMPORT_PREVIEW_INVALID);
        }
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int column = 0; column < headerRow.getLastCellNum(); column++) {
            String header = format(formatter, evaluator, headerRow.getCell(column)).trim();
            if (!header.isEmpty()) {
                columns.putIfAbsent(header, column);
            }
        }
        for (String required : LEGACY_HEADERS) {
            if (!columns.containsKey(required)) {
                throw exception(GRADEBOOK_HEADER_INVALID, required);
            }
        }
        boolean legacyLayout = !columns.containsKey(PRE_REDO_SCORE_HEADER);
        List<EduXlsxWorkbookUtils.RowData> rows = new ArrayList<>();
        for (int index = 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            boolean hasValue = false;
            for (String header : LEGACY_HEADERS) {
                String value = format(formatter, evaluator, row.getCell(columns.get(header))).trim();
                values.put(header, value);
                hasValue |= !value.isEmpty();
            }
            if (hasValue) {
                rows.add(new EduXlsxWorkbookUtils.RowData(index + 1, values));
            }
        }
        return new GradebookSheet(rows, legacyLayout);
    }

    record GradebookSheet(List<EduXlsxWorkbookUtils.RowData> rows, boolean legacyLayout) {
    }

    private static String format(DataFormatter formatter, FormulaEvaluator evaluator, Cell cell) {
        return cell == null ? "" : formatter.formatCellValue(cell, evaluator);
    }

    private GradebookImportRow parseRow(EduXlsxWorkbookUtils.RowData data, List<String> errors) {
        GradebookImportRow row = new GradebookImportRow();
        row.setUsername(nullable(data.get("学号")));
        row.setNickname(nullable(data.get("姓名")));
        row.setHomeworkId(parseLong(data.get("任务编号"), "任务编号", errors, true));
        row.setRecordId(parseLong(data.get("记录 ID"), "记录 ID", errors, false));
        row.setScore(parseInteger(data.get(SCORE_HEADER), errors));
        row.setComment(nullable(data.get(COMMENT_HEADER)));
        row.setRowToken(nullable(data.get("行令牌")));
        row.setTemplateChecksum(nullable(data.get("模板校验值")));
        return row;
    }

    private void validateReadonlyFields(EduXlsxWorkbookUtils.RowData data, EduHomeworkDO homework, EduClassDO clazz,
                                        EduHomeworkRecordDO record, AdminUserDO student, GradebookImportRow row,
                                        List<String> errors) {
        if (!Objects.equals(data.get("任务名称"), safe(homework.getHomeworkName()))) errors.add("任务名称已被修改");
        if (!Objects.equals(data.get("班级"), safe(clazz.getClassName()))) errors.add("班级已被修改");
        if (!Objects.equals(data.get("满分"), String.valueOf(effectiveFullScore(homework)))) errors.add("满分已被修改");
        if (!Objects.equals(data.get("达标线"), String.valueOf(effectivePassScore(homework)))) errors.add("达标线已被修改");
        if (record == null) {
            if (row.getRecordId() != null) errors.add("记录不存在");
            if (student != null) {
                if (!Objects.equals(data.get("姓名"), safe(student.getNickname()))) errors.add("姓名已被修改");
                if (!Objects.equals(data.get("提交状态"), "未提交")) errors.add("提交状态已被修改");
            }
            return;
        }
        if (!Objects.equals(record.getHomeworkId(), homework.getId())) errors.add("记录不属于当前任务");
        if (!Objects.equals(record.getStudentId(), row.getStudentId())) errors.add("记录与学号不匹配");
        if (!Objects.equals(row.getRowToken(), rowToken(record))) errors.add("行令牌无效或提交记录已变化");
        if (student == null) {
            errors.add("学生账号不存在");
        } else {
            if (!Objects.equals(data.get("学号"), safe(student.getUsername()))) errors.add("学号已被修改");
            if (!Objects.equals(data.get("姓名"), safe(student.getNickname()))) errors.add("姓名已被修改");
        }
        if (!Objects.equals(data.get("提交状态"), statusLabel(record))) errors.add("提交状态已被修改");
    }

    /**
     * 决定一行的动作。只有成绩或评语真的有改动、且记录处于可批改状态时才是 UPDATE；
     * 其余为 SKIP 并写明原因，或在 errors 里写明为什么不能导入。评语留空表示沿用原评语。
     */
    EduImportRowActionEnum determineAction(GradebookImportRow row, EduHomeworkRecordDO record,
                                           EduHomeworkDO homework, boolean legacyLayout, List<String> errors) {
        Integer status = record == null ? null : record.getStatus();
        boolean commentUnchanged = row.getComment() == null
                || Objects.equals(normalizeComment(row.getComment()), normalizeComment(record == null ? null : record.getComment()));
        if (record == null || status == null || status == EduHomeworkRecordStatusEnum.NOT_SUBMITTED
                || !IMPORTABLE_STATUSES.contains(status) && status != EduHomeworkRecordStatusEnum.REDO) {
            boolean untouched = row.getScore() == null && row.getComment() == null
                    || record != null && Objects.equals(row.getScore(), record.getScore()) && commentUnchanged;
            if (!untouched) errors.add("这名学生还没有提交，不能录入成绩：清空这一行的成绩和评语");
            return skip(row, record, GradebookImportRow.SKIP_NOT_SUBMITTED);
        }
        if (status == EduHomeworkRecordStatusEnum.REDO) {
            // 旧成绩册的「成绩*」里是打回前成绩；新成绩册里这一格为空。两种原样导回都算没改
            boolean untouched = (row.getScore() == null || Objects.equals(row.getScore(), record.getScore()))
                    && commentUnchanged;
            if (!untouched) errors.add("这份提交已打回，学生还没有重新提交，不能录入成绩：清空这一行的成绩，或等学生重新提交后再导入");
            return skip(row, record, GradebookImportRow.SKIP_AWAITING_RESUBMIT);
        }
        boolean pending = EduHomeworkRecordStatusEnum.PENDING_REVIEW.contains(status);
        if (row.getScore() == null) {
            if (pending && commentUnchanged) {
                return skip(row, record, GradebookImportRow.SKIP_EMPTY);
            }
            errors.add(pending ? "只填了评语没有填成绩：补上成绩，或清空评语后再导入"
                    : "已批改的成绩不能清空：保留原成绩或填写新成绩后再导入");
            return skip(row, record, null);
        }
        boolean scoreUnchanged = Objects.equals(row.getScore(), record.getScore());
        // 已批改记录：成绩和评语都没变就不重复写入。
        // 旧成绩册里待批改记录（主要是已重交）的「成绩*」带着打回前成绩，原样导回同样跳过，避免替重交定稿
        if (scoreUnchanged && commentUnchanged && (!pending || legacyLayout)) {
            return skip(row, record, pending ? GradebookImportRow.SKIP_PRE_REDO_SCORE : GradebookImportRow.SKIP_UNCHANGED);
        }
        if (row.getScore() > effectiveFullScore(homework)) errors.add("成绩不能超过任务满分");
        row.setComment(row.getComment() == null ? record.getComment() : row.getComment());
        row.setSkipReason(null);
        return EduImportRowActionEnum.UPDATE;
    }

    private EduImportRowActionEnum skip(GradebookImportRow row, EduHomeworkRecordDO record, String reason) {
        row.setSkipReason(reason);
        if (record != null && row.getComment() == null) {
            row.setComment(record.getComment());
        }
        return EduImportRowActionEnum.SKIP;
    }

    private static String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String normalized = comment.replace("\r\n", "\n").replace('\r', '\n').trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private LambdaUpdateWrapper<EduHomeworkRecordDO> unchangedSincePreview(EduHomeworkRecordDO record) {
        LambdaUpdateWrapper<EduHomeworkRecordDO> wrapper = new LambdaUpdateWrapper<EduHomeworkRecordDO>()
                .eq(EduHomeworkRecordDO::getId, record.getId());
        if (record.getUpdateTime() == null) {
            wrapper.isNull(EduHomeworkRecordDO::getUpdateTime);
        } else {
            wrapper.eq(EduHomeworkRecordDO::getUpdateTime, record.getUpdateTime());
        }
        return wrapper;
    }

    record CorrectedNotice(Long studentId, Integer score, String comment) {
    }

    /**
     * 批改通知只发给真正写入了成绩的学生，并且等导入事务提交后再发：事务回滚时学生不会收到通知。
     * 提交回调里原事务已经结束，通知在新事务里写入。
     */
    void notifyAfterCommit(EduHomeworkDO homework, List<CorrectedNotice> notices) {
        if (notices.isEmpty()) {
            return;
        }
        List<CorrectedNotice> snapshot = List.copyOf(notices);
        Runnable send = () -> runInNewTransaction(() -> snapshot.forEach(notice -> sendCorrected(homework, notice)));
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private void runInNewTransaction(Runnable action) {
        try {
            if (transactionManager == null) {
                action.run();
                return;
            }
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.executeWithoutResult(status -> action.run());
        } catch (Exception ex) {
            log.error("[notifyAfterCommit][成绩册导入后的批改通知发送失败]", ex);
        }
    }

    private void sendCorrected(EduHomeworkDO homework, CorrectedNotice notice) {
        Map<String, Object> notifyParams = new LinkedHashMap<>();
        notifyParams.put("homeworkName", homework.getHomeworkName());
        notifyParams.put("score", notice.score());
        notifyParams.put("comment", StrUtil.blankToDefault(notice.comment(), "无"));
        businessNotifyService.sendToUser(notice.studentId(), EduBusinessNotifyService.HOMEWORK_CORRECTED,
                notifyParams, "/student/homework/feedback/" + homework.getId());
    }

    private Map<String, Object> recordSnapshot(EduHomeworkRecordDO record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", record == null ? null : record.getId());
        map.put("homeworkId", record == null ? null : record.getHomeworkId());
        map.put("studentId", record == null ? null : record.getStudentId());
        map.put("status", record == null ? null : record.getStatus());
        map.put("score", record == null ? null : record.getScore());
        map.put("comment", record == null ? null : record.getComment());
        map.put("submitTime", record == null ? null : record.getSubmitTime());
        map.put("updateTime", record == null ? null : record.getUpdateTime());
        return map;
    }

    private Map<String, Object> correctedSnapshot(EduHomeworkRecordDO record, GradebookImportRow row) {
        Map<String, Object> map = recordSnapshot(record);
        map.put("status", EduHomeworkRecordStatusEnum.CORRECTED);
        map.put("score", row.getScore());
        map.put("comment", row.getComment());
        return map;
    }

    private EduHomeworkDO getAccessibleHomework(Long homeworkId, boolean requireCorrectPermission) {
        if (!requireCorrectPermission) {
            if (!securityFrameworkService.hasPermission("edu:homework-record:query")) throw exception(FORBIDDEN);
            // 历史成绩册只读，不应因班课归档而拒绝课程管理者查看。
            return aiBusinessAccessService.requireHomeworkViewAccess(homeworkId);
        }
        validatePermission();
        // 成绩写入按任务布置者或管理员授权，放弃认领不影响本人历史任务。
        return aiBusinessAccessService.requireHomeworkAccess(homeworkId);
    }

    private Long parseHomeworkId(String optionsJson) {
        try {
            JSONObject options = JSONUtil.parseObj(optionsJson);
            Long homeworkId = options.getLong("homeworkId");
            if (homeworkId == null) throw exception(IMPORT_OPTIONS_INVALID);
            return homeworkId;
        } catch (cn.nus.zknu.framework.common.exception.ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw exception(IMPORT_OPTIONS_INVALID);
        }
    }

    private Long parseLong(String value, String label, List<String> errors, boolean required) {
        String normalized = nullable(value);
        if (normalized == null) {
            if (required) errors.add(label + "不能为空");
            return null;
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ex) {
            errors.add(label + "格式不正确");
            return null;
        }
    }

    private Integer parseInteger(String value, List<String> errors) {
        String normalized = nullable(value);
        if (normalized == null) return null;
        try {
            int score = Integer.parseInt(normalized);
            if (score < 0 || score > 100) errors.add("成绩必须为 0 至 100 的整数");
            return score;
        } catch (NumberFormatException ex) {
            errors.add("成绩必须为整数");
            return null;
        }
    }

    private List<EduHomeworkRecordDO> listRecords(Long homeworkId) {
        return recordMapper.selectList(new LambdaQueryWrapper<EduHomeworkRecordDO>()
                .eq(EduHomeworkRecordDO::getHomeworkId, homeworkId));
    }

    private EduHomeworkRecordDO latest(EduHomeworkRecordDO left, EduHomeworkRecordDO right) {
        if (left.getId() == null) return right;
        if (right.getId() == null) return left;
        return left.getId() >= right.getId() ? left : right;
    }

    private String rowToken(EduHomeworkRecordDO record) {
        return DigestUtil.sha256Hex(record.getId() + "|" + record.getHomeworkId() + "|" + record.getStudentId()
                + "|" + safe(record.getSubmitTime()));
    }

    private String templateChecksum(EduHomeworkDO homework) {
        return DigestUtil.sha256Hex(homework.getId() + "|" + safe(homework.getHomeworkName()) + "|"
                + homework.getClassId() + "|" + homework.getCourseId() + "|" + effectiveFullScore(homework)
                + "|" + effectivePassScore(homework));
    }

    private int effectiveFullScore(EduHomeworkDO homework) {
        return homework.getScore() == null ? 100 : homework.getScore();
    }

    private int effectivePassScore(EduHomeworkDO homework) {
        return metricPolicy.passScore(homework);
    }

    private String statusLabel(EduHomeworkRecordDO record) {
        if (record == null || record.getStatus() == null
                || record.getStatus() == EduHomeworkRecordStatusEnum.NOT_SUBMITTED) return "未提交";
        return switch (record.getStatus()) {
            case EduHomeworkRecordStatusEnum.SUBMITTED -> "待批改";
            case EduHomeworkRecordStatusEnum.CORRECTED -> "已批改";
            case EduHomeworkRecordStatusEnum.REDO -> "已打回";
            case EduHomeworkRecordStatusEnum.RESUBMITTED -> "已重新提交";
            default -> "未知";
        };
    }

    private void write(Row row, Object... values) {
        for (int index = 0; index < values.length; index++) {
            row.createCell(index, CellType.STRING).setCellValue(safe(values[index]));
        }
    }

    private String nullable(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

}
