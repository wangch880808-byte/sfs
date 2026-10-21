package cn.nus.zknu.module.edu.service.dataexchange.student;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.common.util.validation.ValidationUtils;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchDO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchItemDO;
import cn.nus.zknu.module.edu.enums.importbatch.EduImportRowActionEnum;
import cn.nus.zknu.module.edu.service.dataexchange.EduImportPasswordUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataExchangeTemplate;
import cn.nus.zknu.module.edu.service.dataexchange.EduAccountPreviewData;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataExchangeOptionUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduImportPreviewUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduImportSnapshotUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils;
import cn.nus.zknu.module.edu.service.importbatch.EduImportCommitResult;
import cn.nus.zknu.module.edu.service.importbatch.EduImportHandler;
import cn.nus.zknu.module.edu.service.importbatch.EduImportPreviewRow;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserPageReqVO;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.dataobject.user.UserProfileDO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.IMPORT_DATA_CHANGED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.IMPORT_PREVIEW_INVALID;

@Slf4j
@Component
public class StudentMasterDataExchangeHandler implements EduImportHandler, EduDataExchangeTemplate {

    public static final String BIZ_TYPE = "STUDENT_MASTER";
    public static final String VERSION = "V1";
    public static final List<String> STUDENT_HEADERS = List.of(
            "学号*", "姓名*", "性别", "班级*", "手机号", "邮箱", "状态");
    /** 学生档案里按班级自动生成的入班行：批次要求「工作表 + 行号」唯一，与学生行区分开，行号仍对应学生行 */
    public static final String AUTO_RELATION_SHEET = "学生档案（入班）";
    public static final List<String> RELATION_HEADERS = List.of("学号*", "班级编号*", "备注");

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]{4,30}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Resource
    private EduDeptScopeService deptScopeService;

    @Resource
    private StudentDataExchangeSupport support;

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
        return "学生主数据导入模板_V1.xlsx";
    }

    @Override
    public void validatePermission() {
        support.validateMasterPermission();
    }

    @Override
    public String getInitialPasswordMode() {
        return INITIAL_PASSWORD_MODE_USERNAME;
    }

    @Override
    public byte[] generate() {
        List<EduClassDO> activeClasses = Optional.ofNullable(support.getActiveClasses())
                .orElseGet(Collections::emptyList);
        Map<String, List<String>> dropdownOptions = new LinkedHashMap<>();
        List<String> classOptions = activeClasses.stream().map(this::classLabel).toList();
        dropdownOptions.put("班级*", classOptions);
        dropdownOptions.put("班级编号*", classOptions);
        return EduXlsxWorkbookUtils.createTemplate(BIZ_TYPE, VERSION, List.of(
                        "必填项：学号、姓名、班级（表头带 *）；专业和年级按班级自动补全。学号和手机号必须保持文本格式。",
                        "多个班级或跨专业、跨年级的情况请在“班级关系”工作表中填写，并写明备注。",
                        "新建学生默认以学号作为初始密码，确认导入前可统一覆写；已有账号不会重置密码。",
                        "更新时选填字段的空单元格不覆盖原值；专业和年级以班级为准。班级关系只添加或恢复，不会隐式移出学生。",
                        "已存在的非学生账号不能通过本模板转换或关联为学生。"),
                List.of(
                        new EduXlsxWorkbookUtils.SheetSpec("学生档案", STUDENT_HEADERS, Set.of(0, 4),
                                List.of("20260001", "张三", "男", "软件工程 2026 级 1 班 [1]", "13800138000", "student@example.edu", "正常")),
                        new EduXlsxWorkbookUtils.SheetSpec("班级关系", RELATION_HEADERS, Set.of(0, 1),
                                List.of("20260001", "1", "同专业同年级可留空"))),
                List.of(
                        new EduXlsxWorkbookUtils.DictionaryEntry("性别", "0 / 1 / 2", "0 未知，1 男，2 女"),
                        new EduXlsxWorkbookUtils.DictionaryEntry("状态", "0 / 1", "0 正常，1 停用"),
                        new EduXlsxWorkbookUtils.DictionaryEntry("班级关系", "追加 / 恢复", "导入不会删除现有关系")),
                dropdownOptions);
    }

    @Override
    public List<EduImportPreviewRow> preview(byte[] fileContent, String optionsJson) {
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(fileContent)) {
            List<EduXlsxWorkbookUtils.RowData> studentRows = EduXlsxWorkbookUtils.readSheet(
                    workbook, "学生档案", STUDENT_HEADERS);
            List<EduXlsxWorkbookUtils.RowData> relationRows = EduXlsxWorkbookUtils.readSheet(
                    workbook, "班级关系", RELATION_HEADERS);
            List<ParsedStudent> parsedStudents = studentRows.stream().map(this::parseStudent).toList();
            PreviewData data = preload(parsedStudents, relationRows);
            Map<String, StudentCandidate> students = parseStudents(parsedStudents, data);
            List<EduImportPreviewRow> result = students.values().stream().map(StudentCandidate::preview)
                    .collect(Collectors.toCollection(ArrayList::new));
            List<EduImportPreviewRow> automaticRelations = students.values().stream()
                    .filter(student -> student.errors().isEmpty())
                    .map(student -> {
                        StudentRelationImportRow row = new StudentRelationImportRow();
                        row.setUsername(student.row().getUsername());
                        row.setClassId(student.row().getClassId());
                        return relationPreview(AUTO_RELATION_SHEET, student.preview().getRowNumber(), row,
                                student.row(), new ArrayList<>(), data);
                    }).toList();
            result.addAll(automaticRelations);
            Set<String> automaticKeys = automaticRelations.stream().map(EduImportPreviewRow::getBusinessKey)
                    .collect(Collectors.toSet());
            result.addAll(parseRelations(relationRows, students, automaticKeys, data));
            for (EduImportPreviewRow preview : result) {
                if (preview.getBusinessKey().startsWith("STUDENT:")) {
                    StudentAccountImportRow row = JSONUtil.toBean(preview.getNormalizedData(), StudentAccountImportRow.class);
                    if (!deptScopeService.isLoadedClassInScope(data.scopeClasses.get(row.getClassId()))
                            || !deptScopeService.isLoadedUsernameInScope(data.scopeUsers.get(row.getUsername()))) {
                        deptScopeService.markOutOfScope(preview);
                    }
                } else if (preview.getBusinessKey().startsWith("REL:")) {
                    StudentRelationImportRow row = JSONUtil.toBean(preview.getNormalizedData(), StudentRelationImportRow.class);
                    StudentCandidate student = students.get(row.getUsername());
                    if (!deptScopeService.isLoadedClassInScope(data.scopeClasses.get(row.getClassId()))
                            || !deptScopeService.isLoadedUsernameInScope(data.scopeUsers.get(row.getUsername()))
                            || (student != null && !deptScopeService.isLoadedClassInScope(data.scopeClasses.get(student.row().getClassId())))) {
                        deptScopeService.markOutOfScope(preview);
                    }
                }
            }
            return result;
        } catch (cn.nus.zknu.framework.common.exception.ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("[preview][学生主数据预检失败]", ex);
            throw exception(IMPORT_PREVIEW_INVALID);
        }
    }

    @Override
    public EduImportCommitResult commit(EduImportBatchDO batch, List<EduImportBatchItemDO> validItems,
                                        String initialPassword) {
        Map<String, String> passwords = newStudentPasswords(validItems, initialPassword);
        if (passwords.isEmpty()) {
            return commitRows(validItems, initialPassword);
        }
        // 先并行算好新学生的密码哈希，逐行创建账号时直接使用
        try (var ignored = support.preEncodePasswords(passwords)) {
            return commitRows(validItems, initialPassword);
        }
    }

    private Map<String, String> newStudentPasswords(List<EduImportBatchItemDO> validItems, String initialPassword) {
        Map<String, String> passwords = new HashMap<>();
        for (EduImportBatchItemDO item : validItems) {
            if (item.getBusinessKey().startsWith("STUDENT:")
                    && EduImportRowActionEnum.INSERT.name().equals(item.getAction())) {
                String username = JSONUtil.toBean(item.getNormalizedData(), StudentAccountImportRow.class).getUsername();
                passwords.put(username, EduImportPasswordUtils.resolve(initialPassword, username));
            }
        }
        return passwords;
    }

    private EduImportCommitResult commitRows(List<EduImportBatchItemDO> validItems, String initialPassword) {
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        for (EduImportBatchItemDO item : validItems) {
            if (!item.getBusinessKey().startsWith("STUDENT:")) continue;
            EduImportRowActionEnum action = EduImportRowActionEnum.valueOf(item.getAction());
            if (action == EduImportRowActionEnum.SKIP) {
                skipped++;
                continue;
            }
            StudentAccountImportRow row = JSONUtil.toBean(item.getNormalizedData(), StudentAccountImportRow.class);
            if (!deptScopeService.isClassInScope(row.getClassId())
                    || !deptScopeService.isUsernameInScope(row.getUsername())) {
                throw exception(FORBIDDEN);
            }
            AdminUserDO current = support.getUser(row.getUsername());
            validateSnapshot(item.getBeforeData(), current == null ? null : support.studentSnapshot(current));
            support.saveStudent(row, initialPassword);
            if (action == EduImportRowActionEnum.INSERT) inserted++;
            else updated++;
        }
        for (EduImportBatchItemDO item : validItems) {
            if (!item.getBusinessKey().startsWith("REL:")) continue;
            EduImportRowActionEnum action = EduImportRowActionEnum.valueOf(item.getAction());
            if (action == EduImportRowActionEnum.SKIP) {
                skipped++;
                continue;
            }
            StudentRelationImportRow row = JSONUtil.toBean(item.getNormalizedData(), StudentRelationImportRow.class);
            deptScopeService.validateClassInScope(row.getClassId());
            if (!deptScopeService.isUsernameInScope(row.getUsername())) {
                throw exception(FORBIDDEN);
            }
            AdminUserDO user = support.getUser(row.getUsername());
            EduClassDO clazz = support.getClass(row.getClassId());
            if (user == null || clazz == null) throw exception(IMPORT_DATA_CHANGED);
            EduClassStudentDO current = support.getRelation(clazz.getId(), user.getId());
            validateSnapshot(item.getBeforeData(), support.relationSnapshot(current));
            support.addOrRestoreRelation(clazz.getId(), user.getId(), row.getRemark());
            if (action == EduImportRowActionEnum.INSERT) inserted++;
            else updated++;
        }
        return new EduImportCommitResult(inserted, updated, skipped);
    }

    public byte[] export(UserPageReqVO reqVO) {
        support.validateMasterReadPermission();
        List<AdminUserDO> users = support.getStudentList(reqVO);
        Set<Long> userIds = users.stream().map(AdminUserDO::getId).collect(Collectors.toSet());
        Map<Long, UserProfileDO> profiles = support.getProfileMap(userIds);
        List<EduClassStudentDO> relations = support.getActiveRelationsByStudents(userIds).stream()
                .filter(relation -> !Boolean.TRUE.equals(relation.getDeleted())).toList();
        Set<Long> classIds = relations.stream().map(EduClassStudentDO::getClassId).collect(Collectors.toSet());
        Map<Long, EduClassDO> classes = support.getClasses(classIds).stream()
                .filter(clazz -> !Objects.equals(clazz.getStatus(), CommonStatusEnum.DISABLE.getStatus()))
                .collect(Collectors.toMap(EduClassDO::getId, Function.identity()));
        Map<Long, AdminUserDO> usersById = users.stream().collect(Collectors.toMap(AdminUserDO::getId, Function.identity()));
        Map<Long, List<EduClassDO>> classesByStudent = relations.stream()
                .filter(relation -> classes.containsKey(relation.getClassId()))
                .collect(Collectors.groupingBy(EduClassStudentDO::getStudentId,
                        Collectors.mapping(relation -> classes.get(relation.getClassId()), Collectors.toList())));
        Map<Long, EduClassDO> primaryClasses = new HashMap<>();
        for (AdminUserDO user : users) {
            UserProfileDO profile = profiles.get(user.getId());
            List<EduClassDO> studentClasses = classesByStudent.getOrDefault(user.getId(), Collections.emptyList());
            EduClassDO primary = studentClasses.stream()
                    .filter(clazz -> Objects.equals(clazz.getDeptId(), user.getDeptId())
                            && Objects.equals(clazz.getGradeYear(), profile == null ? null : profile.getGradeYear()))
                    .findFirst().orElse(studentClasses.isEmpty() ? null : studentClasses.get(0));
            if (primary != null) primaryClasses.put(user.getId(), primary);
        }
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(generate())) {
            Sheet studentSheet = workbook.getSheet("学生档案");
            for (int index = 0; index < users.size(); index++) {
                AdminUserDO user = users.get(index);
                EduClassDO primary = primaryClasses.get(user.getId());
                write(studentSheet.createRow(index + 1), user.getUsername(), user.getNickname(),
                        sexLabel(user.getSex()), primary == null ? "" : classLabel(primary), safe(user.getMobile()),
                        safe(user.getEmail()), safe(user.getStatus()));
            }
            Sheet relationSheet = workbook.getSheet("班级关系");
            int rowIndex = 1;
            for (EduClassStudentDO relation : relations) {
                AdminUserDO user = usersById.get(relation.getStudentId());
                EduClassDO clazz = classes.get(relation.getClassId());
                EduClassDO primary = user == null ? null : primaryClasses.get(user.getId());
                if (user != null && clazz != null && (primary == null || !Objects.equals(clazz.getId(), primary.getId()))) {
                    write(relationSheet.createRow(rowIndex++), user.getUsername(), clazz.getId(),
                            safe(relation.getRemark()));
                }
            }
            return EduXlsxWorkbookUtils.writeWorkbook(workbook);
        } catch (Exception ex) {
            if (ex instanceof cn.nus.zknu.framework.common.exception.ServiceException serviceException) throw serviceException;
            throw exception(IMPORT_PREVIEW_INVALID);
        }
    }

    private ParsedStudent parseStudent(EduXlsxWorkbookUtils.RowData data) {
        List<String> errors = new ArrayList<>();
        StudentAccountImportRow row = new StudentAccountImportRow();
        row.setUsername(nullable(data.get("学号*")));
        row.setNickname(nullable(data.get("姓名*")));
        row.setSex(parseSex(data.get("性别"), errors));
        row.setClassId(parseClassId(data.get("班级*"), errors));
        if (nullable(data.get("班级*")) == null) errors.add("班级不能为空");
        row.setMobile(nullable(data.get("手机号")));
        row.setEmail(nullable(data.get("邮箱")));
        row.setStatus(parseStatus(data.get("状态"), errors));
        return new ParsedStudent(data.rowNumber(), row, errors);
    }

    private Map<String, StudentCandidate> parseStudents(List<ParsedStudent> rows, PreviewData data) {
        Map<String, StudentCandidate> result = new LinkedHashMap<>();
        for (ParsedStudent parsed : rows) {
            StudentAccountImportRow row = parsed.row();
            List<String> errors = parsed.errors();
            AdminUserDO current = row.getUsername() == null ? null : data.users.get(row.getUsername());
            support.validateNoIdentityConflict(current, errors);
            mergeDefaults(row, current, data);
            resolveClass(row, errors, data);
            validateAccountFields(row, errors);
            validateMajor(row.getMajorCode(), errors, data);
            data.validateContactUniqueness(row.getUsername(), row.getMobile(), row.getEmail(), errors);
            Map<String, Object> before = current == null ? null : support.studentSnapshot(current,
                    data.profiles.get(current.getId()), data.depts.get(current.getDeptId()));
            Map<String, Object> after = support.studentSnapshot(row);
            EduImportRowActionEnum action = current == null ? EduImportRowActionEnum.INSERT
                    : Objects.equals(before, after) ? EduImportRowActionEnum.SKIP : EduImportRowActionEnum.UPDATE;
            EduImportPreviewRow preview = preview("学生档案", parsed.rowNumber(), "STUDENT:" + safe(row.getUsername()),
                    row, action, errors, before, after);
            StudentCandidate candidate = new StudentCandidate(row, errors, preview);
            if (row.getUsername() != null && !result.containsKey(row.getUsername())) result.put(row.getUsername(), candidate);
            else result.put("#ROW-" + parsed.rowNumber(), candidate);
        }
        List<EduImportPreviewRow> previews = result.values().stream().map(StudentCandidate::preview).toList();
        EduImportPreviewUtils.markDuplicates(previews,
                item -> JSONUtil.toBean(item.getNormalizedData(), StudentAccountImportRow.class).getUsername(),
                "文件内学号重复");
        EduImportPreviewUtils.markDuplicates(previews,
                item -> JSONUtil.toBean(item.getNormalizedData(), StudentAccountImportRow.class).getMobile(),
                "文件内手机号重复");
        EduImportPreviewUtils.markDuplicates(previews, item -> {
            String email = JSONUtil.toBean(item.getNormalizedData(), StudentAccountImportRow.class).getEmail();
            return email == null ? null : email.toLowerCase(Locale.ROOT);
        }, "文件内邮箱重复");
        return result;
    }

    private void resolveClass(StudentAccountImportRow row, List<String> errors, PreviewData data) {
        row.setMajorCode(null);
        row.setGradeYear(null);
        if (row.getClassId() == null) return;
        EduClassDO clazz = data.classes.get(row.getClassId());
        if (clazz == null || Objects.equals(clazz.getStatus(), CommonStatusEnum.DISABLE.getStatus())) {
            errors.add("班级不存在或已封存");
            return;
        }
        DeptDO major = data.depts.get(clazz.getDeptId());
        if (major == null || !"MAJOR".equals(major.getDeptType())) {
            errors.add("班级所属组织不是专业，请先在组织架构中调整");
            return;
        }
        row.setMajorCode(major.getDeptCode());
        row.setGradeYear(clazz.getGradeYear());
    }

    private List<EduImportPreviewRow> parseRelations(List<EduXlsxWorkbookUtils.RowData> rows,
                                                    Map<String, StudentCandidate> students,
                                                    Set<String> automaticKeys, PreviewData data) {
        List<EduImportPreviewRow> result = new ArrayList<>();
        for (EduXlsxWorkbookUtils.RowData source : rows) {
            List<String> errors = new ArrayList<>();
            StudentRelationImportRow row = new StudentRelationImportRow();
            row.setUsername(nullable(source.get("学号*")));
            row.setClassId(parseClassId(source.get("班级编号*"), errors));
            row.setRemark(nullable(source.get("备注")));
            if (automaticKeys.contains(relationKey(row))) continue;
            StudentAccountImportRow account = students.containsKey(row.getUsername())
                    ? students.get(row.getUsername()).row() : existingAccount(row.getUsername(), data);
            result.add(relationPreview("班级关系", source.rowNumber(), row, account, errors, data));
        }
        EduImportPreviewUtils.markDuplicates(result, item -> {
            StudentRelationImportRow row = JSONUtil.toBean(item.getNormalizedData(), StudentRelationImportRow.class);
            return row.getUsername() == null || row.getClassId() == null ? null : item.getBusinessKey();
        }, "文件内班级关系重复");
        return result;
    }

    private String relationKey(StudentRelationImportRow row) {
        return "REL:" + safe(row.getUsername()) + ":" + safe(row.getClassId());
    }

    private EduImportPreviewRow relationPreview(String sheet, int rowNumber, StudentRelationImportRow row,
                                                StudentAccountImportRow account, List<String> errors, PreviewData data) {
        if (row.getUsername() == null) errors.add("学号不能为空");
        if (row.getClassId() == null) errors.add("班级编号不能为空");
        if (account == null) errors.add("学号不存在且未在学生档案工作表中定义");
        EduClassDO clazz = row.getClassId() == null ? null : data.classes.get(row.getClassId());
        if (clazz == null || Objects.equals(clazz.getStatus(), CommonStatusEnum.DISABLE.getStatus())) {
            errors.add("班级不存在或已封存");
        }
        if (account != null && !Objects.equals(account.getStatus(), CommonStatusEnum.ENABLE.getStatus())) {
            errors.add("停用学生不能加入班级");
        }
        if (account != null && clazz != null
                && isRemarkRequired(clazz, account, data)
                && row.getRemark() == null) {
            errors.add("跨专业或跨年级加入班级必须填写备注");
        }
        AdminUserDO existingUser = row.getUsername() == null ? null : data.users.get(row.getUsername());
        EduClassStudentDO relation = existingUser == null || clazz == null ? null
                : data.relations.get(new RelationKey(clazz.getId(), existingUser.getId()));
        EduImportRowActionEnum action = relation == null ? EduImportRowActionEnum.INSERT
                : Boolean.TRUE.equals(relation.getDeleted()) ? EduImportRowActionEnum.UPDATE : EduImportRowActionEnum.SKIP;
        Map<String, Object> before = support.relationSnapshot(relation);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("state", "ACTIVE");
        after.put("remark", action == EduImportRowActionEnum.SKIP ? relation.getRemark() : row.getRemark());
        return preview(sheet, rowNumber, relationKey(row), row, action, errors, before, after);
    }

    private StudentAccountImportRow existingAccount(String username, PreviewData data) {
        if (username == null) return null;
        AdminUserDO user = data.users.get(username);
        if (user == null || !Objects.equals(user.getUserType(), StudentDataExchangeSupport.STUDENT_USER_TYPE)) return null;
        UserProfileDO profile = data.profiles.get(user.getId());
        DeptDO dept = data.depts.get(user.getDeptId());
        StudentAccountImportRow row = new StudentAccountImportRow();
        row.setUsername(user.getUsername());
        row.setNickname(user.getNickname());
        row.setSex(user.getSex());
        row.setMajorCode(dept == null ? null : dept.getDeptCode());
        row.setGradeYear(profile == null ? null : profile.getGradeYear());
        row.setMobile(user.getMobile());
        row.setEmail(user.getEmail());
        row.setStatus(user.getStatus());
        return row;
    }

    private void mergeDefaults(StudentAccountImportRow row, AdminUserDO current, PreviewData data) {
        if (current == null) {
            if (row.getSex() == null) row.setSex(0);
            if (row.getStatus() == null) row.setStatus(CommonStatusEnum.ENABLE.getStatus());
            return;
        }
        UserProfileDO profile = data.profiles.get(current.getId());
        DeptDO dept = data.depts.get(current.getDeptId());
        if (row.getNickname() == null) row.setNickname(current.getNickname());
        if (row.getSex() == null) row.setSex(current.getSex());
        if (row.getMajorCode() == null && dept != null) row.setMajorCode(dept.getDeptCode());
        if (row.getGradeYear() == null && profile != null) row.setGradeYear(profile.getGradeYear());
        if (row.getMobile() == null) row.setMobile(current.getMobile());
        if (row.getEmail() == null) row.setEmail(current.getEmail());
        if (row.getStatus() == null) row.setStatus(current.getStatus());
    }

    private void validateAccountFields(StudentAccountImportRow row, List<String> errors) {
        if (row.getUsername() == null) errors.add("学号不能为空");
        else if (!USERNAME_PATTERN.matcher(row.getUsername()).matches()) errors.add("学号必须为 4 至 30 位字母或数字");
        if (row.getNickname() == null) errors.add("姓名不能为空");
        else if (row.getNickname().length() > 30) errors.add("姓名不能超过 30 个字符");
        if (row.getMajorCode() == null) errors.add("主专业编码不能为空");
        if (row.getGradeYear() == null) errors.add("入学年份不能为空");
        if (row.getMobile() != null && !ValidationUtils.isMobile(row.getMobile())) errors.add("手机号格式不正确");
        if (row.getEmail() != null && (!EMAIL_PATTERN.matcher(row.getEmail()).matches() || row.getEmail().length() > 50)) {
            errors.add("邮箱格式不正确或超过 50 个字符");
        }
    }

    private void validateMajor(String majorCode, List<String> errors, PreviewData data) {
        if (majorCode == null) return;
        DeptDO major = data.deptCodes.get(majorCode);
        if (major == null) errors.add("主专业编码不存在");
        else if (!"MAJOR".equals(major.getDeptType())) errors.add("主专业编码必须指向专业节点");
    }

    private void validateSnapshot(String expectedJson, Map<String, Object> current) {
        if (!EduImportSnapshotUtils.matches(expectedJson, current)) throw exception(IMPORT_DATA_CHANGED);
    }

    private EduImportPreviewRow preview(String sheet, int rowNumber, String key, Object normalized,
                                        EduImportRowActionEnum action, List<String> errors,
                                        Map<String, Object> before, Map<String, Object> after) {
        return EduImportPreviewRow.builder().sheetName(sheet).rowNumber(rowNumber).businessKey(key)
                .normalizedData(JSONUtil.toJsonStr(normalized)).action(action).errors(errors)
                .beforeData(before == null ? null : JSONUtil.toJsonStr(before))
                .afterData(JSONUtil.toJsonStr(after)).build();
    }

    private String nullable(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private Integer parseSex(String value, List<String> errors) {
        String normalized = nullable(value);
        if (normalized == null) return null;
        return switch (normalized) {
            case "0", "未知" -> 0;
            case "1", "男" -> 1;
            case "2", "女" -> 2;
            default -> {
                errors.add("性别只支持 0/1/2 或未知/男/女");
                yield null;
            }
        };
    }

    private Integer parseStatus(String value, List<String> errors) {
        String normalized = nullable(value);
        if (normalized == null) return null;
        return switch (normalized) {
            case "0", "正常" -> 0;
            case "1", "停用" -> 1;
            default -> {
                errors.add("状态只支持 0（正常）或 1（停用）");
                yield null;
            }
        };
    }

    private Long parseClassId(String value, List<String> errors) {
        String normalized = EduDataExchangeOptionUtils.parseSelectedCode(value);
        if (normalized == null) return null;
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ex) {
            errors.add("班级编号必须是整数");
            return null;
        }
    }

    private String sexLabel(Integer value) {
        return Objects.equals(value, 1) ? "男" : Objects.equals(value, 2) ? "女" : "未知";
    }

    private void write(Row row, Object... values) {
        for (int index = 0; index < values.length; index++) {
            row.createCell(index, CellType.STRING).setCellValue(safe(values[index]));
        }
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String classLabel(EduClassDO clazz) {
        return clazz.getClassName() + " [" + clazz.getId() + "]";
    }

    private PreviewData preload(List<ParsedStudent> students, List<EduXlsxWorkbookUtils.RowData> relations) {
        PreviewData data = new PreviewData();
        Set<String> usernames = new LinkedHashSet<>();
        Set<Long> classIds = new LinkedHashSet<>();
        Set<String> mobiles = new LinkedHashSet<>();
        Set<String> emails = new LinkedHashSet<>();
        for (ParsedStudent student : students) {
            StudentAccountImportRow row = student.row();
            if (row.getUsername() != null) usernames.add(row.getUsername());
            if (row.getClassId() != null) classIds.add(row.getClassId());
            if (row.getMobile() != null) mobiles.add(row.getMobile());
            if (row.getEmail() != null) emails.add(row.getEmail());
        }
        for (EduXlsxWorkbookUtils.RowData row : relations) {
            String username = nullable(row.get("学号*"));
            Long classId = parseClassId(row.get("班级编号*"), new ArrayList<>());
            if (username != null) usernames.add(username);
            if (classId != null) classIds.add(classId);
        }
        data.users.putAll(support.getUsersByUsernames(usernames));
        data.scopeUsers.putAll(support.getScopeUsersByUsernames(usernames));
        support.getClasses(classIds).forEach(clazz -> data.classes.put(clazz.getId(), clazz));
        support.getScopeClasses(classIds).forEach(clazz -> data.scopeClasses.put(clazz.getId(), clazz));
        Set<Long> userIds = data.users.values().stream().map(AdminUserDO::getId).collect(Collectors.toSet());
        data.profiles.putAll(support.getProfileMap(userIds));
        Set<Long> deptIds = new LinkedHashSet<>();
        data.users.values().forEach(user -> { if (user.getDeptId() != null) deptIds.add(user.getDeptId()); });
        data.classes.values().forEach(clazz -> { if (clazz.getDeptId() != null) deptIds.add(clazz.getDeptId()); });
        support.getDepts(deptIds).forEach(dept -> data.depts.put(dept.getId(), dept));
        Set<String> codes = data.depts.values().stream().map(DeptDO::getDeptCode).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        data.deptCodes.putAll(support.getDeptsByCodes(codes));
        data.setContactOwners(support.getMobileOwners(mobiles), support.getEmailOwners(emails));
        support.getImportRelations(classIds, userIds).forEach(relation -> data.relations.putIfAbsent(
                new RelationKey(relation.getClassId(), relation.getStudentId()), relation));
        return data;
    }

    private boolean isRemarkRequired(EduClassDO clazz, StudentAccountImportRow account, PreviewData data) {
        DeptDO major = data.deptCodes.get(account.getMajorCode());
        return clazz != null && (major == null || !Objects.equals(clazz.getDeptId(), major.getId())
                || !Objects.equals(clazz.getGradeYear(), account.getGradeYear()));
    }

    private static class PreviewData extends EduAccountPreviewData {
        private final Map<Long, EduClassDO> classes = new HashMap<>();
        private final Map<Long, EduClassDO> scopeClasses = new HashMap<>();
        private final Map<RelationKey, EduClassStudentDO> relations = new HashMap<>();
    }

    private record ParsedStudent(int rowNumber, StudentAccountImportRow row, List<String> errors) { }
    private record RelationKey(Long classId, Long studentId) { }

    private record StudentCandidate(StudentAccountImportRow row, List<String> errors, EduImportPreviewRow preview) {
    }

}
