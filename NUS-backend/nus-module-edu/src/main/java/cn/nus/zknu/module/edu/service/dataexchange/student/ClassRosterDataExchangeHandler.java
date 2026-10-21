package cn.nus.zknu.module.edu.service.dataexchange.student;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.common.util.validation.ValidationUtils;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchDO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchItemDO;
import cn.nus.zknu.module.edu.enums.importbatch.EduImportRowActionEnum;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataExchangeTemplate;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.edu.service.dataexchange.EduImportPreviewUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduImportSnapshotUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils;
import cn.nus.zknu.module.edu.service.importbatch.EduImportCommitResult;
import cn.nus.zknu.module.edu.service.importbatch.EduImportHandler;
import cn.nus.zknu.module.edu.service.importbatch.EduImportPreviewRow;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.dataobject.user.UserProfileDO;
import jakarta.annotation.Resource;
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
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;

@Component
public class ClassRosterDataExchangeHandler implements EduImportHandler, EduDataExchangeTemplate {

    public static final String BIZ_TYPE = "CLASS_ROSTER";
    public static final String VERSION = "V1";
    public static final List<String> HEADERS = List.of(
            "学号*", "姓名*", "性别", "手机号", "邮箱", "跨专业或年级加入备注");

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
        return "班级花名册导入模板_V1.xlsx";
    }

    @Override
    public void validatePermission() {
        support.validateRosterBasePermission();
    }

    @Override
    public String getInitialPasswordMode() {
        return INITIAL_PASSWORD_MODE_USERNAME;
    }

    @Override
    public byte[] generate() {
        return EduXlsxWorkbookUtils.createTemplate(BIZ_TYPE, VERSION, List.of(
                        "班级由上传入口确定，工作簿内不能修改目标班级。",
                        "必填项：学号、姓名（表头带 *）；其余字段可留空。学号和手机号必须保持文本格式。",
                        "新建学生默认以学号作为初始密码，确认导入前可统一覆写；已有账号不会重置密码。",
                        "更新时空单元格不覆盖原值，导入只添加或恢复班级关系。",
                        "跨专业或跨年级加入必须填写备注。"),
                List.of(new EduXlsxWorkbookUtils.SheetSpec("班级花名册", HEADERS, Set.of(0, 3),
                        List.of("20260001", "张三", "男", "13800138000", "student@example.edu", "跨专业或年级时填写"))),
                List.of(new EduXlsxWorkbookUtils.DictionaryEntry("性别", "0 / 1 / 2", "0 未知，1 男，2 女")));
    }

    @Override
    public List<EduImportPreviewRow> preview(byte[] fileContent, String optionsJson) {
        Long classId = parseClassId(optionsJson);
        support.validateRosterPermission(classId);
        EduClassDO clazz = support.getClass(classId);
        if (clazz == null) throw exception(CLASS_NOT_EXISTS);
        DeptDO classMajor = support.getDept(clazz.getDeptId());
        if (classMajor == null) throw exception(IMPORT_DATA_CHANGED);
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(fileContent)) {
            List<EduXlsxWorkbookUtils.RowData> rows = EduXlsxWorkbookUtils.readSheet(
                    workbook, "班级花名册", HEADERS);
            List<EduImportPreviewRow> result = new ArrayList<>();
            for (EduXlsxWorkbookUtils.RowData data : rows) {
                List<String> errors = new ArrayList<>();
                ClassRosterImportRow row = new ClassRosterImportRow();
                row.setUsername(nullable(data.get("学号*")));
                row.setNickname(nullable(data.get("姓名*")));
                row.setSex(parseSex(data.get("性别"), errors));
                row.setMobile(nullable(data.get("手机号")));
                row.setEmail(nullable(data.get("邮箱")));
                row.setRemark(nullable(data.get("跨专业或年级加入备注")));
                AdminUserDO current = row.getUsername() == null ? null : support.getUser(row.getUsername());
                support.validateNoIdentityConflict(current, errors);
                mergeAccount(row, current, classMajor, clazz);
                validateFields(row, errors);
                support.validateContactUniqueness(row.getUsername(), row.getMobile(), row.getEmail(), errors);
                if (!Objects.equals(row.getStatus(), CommonStatusEnum.ENABLE.getStatus())) {
                    errors.add("停用学生不能加入班级");
                }
                if (support.isRemarkRequired(clazz, row.getMajorCode(), row.getGradeYear()) && row.getRemark() == null) {
                    errors.add("跨专业或跨年级加入班级必须填写备注");
                }

                EduClassStudentDO relation = current == null ? null : support.getRelation(classId, current.getId());
                Map<String, Object> before = combinedSnapshot(current, relation);
                StudentAccountImportRow account = toAccount(row);
                Map<String, Object> studentAfter = support.studentSnapshot(account);
                Map<String, Object> studentBefore = current == null ? null : support.studentSnapshot(current);
                EduImportRowActionEnum accountAction = current == null ? EduImportRowActionEnum.INSERT
                        : Objects.equals(studentBefore, studentAfter) ? EduImportRowActionEnum.SKIP : EduImportRowActionEnum.UPDATE;
                EduImportRowActionEnum relationAction = relation == null ? EduImportRowActionEnum.INSERT
                        : Boolean.TRUE.equals(relation.getDeleted()) ? EduImportRowActionEnum.UPDATE : EduImportRowActionEnum.SKIP;
                row.setAccountAction(accountAction.name());
                row.setRelationAction(relationAction.name());
                EduImportRowActionEnum action = accountAction == EduImportRowActionEnum.INSERT
                        || relationAction == EduImportRowActionEnum.INSERT ? EduImportRowActionEnum.INSERT
                        : accountAction == EduImportRowActionEnum.UPDATE || relationAction == EduImportRowActionEnum.UPDATE
                        ? EduImportRowActionEnum.UPDATE : EduImportRowActionEnum.SKIP;
                Map<String, Object> after = new LinkedHashMap<>();
                after.put("student", studentAfter);
                Map<String, Object> relationAfter = new LinkedHashMap<>();
                relationAfter.put("state", "ACTIVE");
                relationAfter.put("remark", relationAction == EduImportRowActionEnum.SKIP ? relation.getRemark() : row.getRemark());
                after.put("relation", relationAfter);
                result.add(EduImportPreviewRow.builder().sheetName("班级花名册").rowNumber(data.rowNumber())
                        .businessKey("ROSTER:" + safe(row.getUsername())).normalizedData(JSONUtil.toJsonStr(row))
                        .action(action).errors(errors).beforeData(JSONUtil.toJsonStr(before))
                        .afterData(JSONUtil.toJsonStr(after)).build());
            }
            EduImportPreviewUtils.markDuplicates(result,
                    item -> JSONUtil.toBean(item.getNormalizedData(), ClassRosterImportRow.class).getUsername(),
                    "文件内学号重复");
            EduImportPreviewUtils.markDuplicates(result,
                    item -> JSONUtil.toBean(item.getNormalizedData(), ClassRosterImportRow.class).getMobile(),
                    "文件内手机号重复");
            EduImportPreviewUtils.markDuplicates(result, item -> {
                String email = JSONUtil.toBean(item.getNormalizedData(), ClassRosterImportRow.class).getEmail();
                return email == null ? null : email.toLowerCase(Locale.ROOT);
            }, "文件内邮箱重复");
            for (EduImportPreviewRow preview : result) {
                ClassRosterImportRow row = JSONUtil.toBean(preview.getNormalizedData(), ClassRosterImportRow.class);
                if (!deptScopeService.isDeptCodeInScope(row.getMajorCode())
                        || !deptScopeService.isUsernameInScope(row.getUsername())) {
                    deptScopeService.markOutOfScope(preview);
                }
            }
            return result;
        } catch (cn.nus.zknu.framework.common.exception.ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw exception(IMPORT_PREVIEW_INVALID);
        }
    }

    @Override
    public EduImportCommitResult commit(EduImportBatchDO batch, List<EduImportBatchItemDO> validItems,
                                        String initialPassword) {
        Long classId = parseClassId(batch.getOptionsJson());
        support.validateRosterPermission(classId);
        if (support.getClass(classId) == null) throw exception(IMPORT_DATA_CHANGED);
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        for (EduImportBatchItemDO item : validItems) {
            if (EduImportRowActionEnum.SKIP.name().equals(item.getAction())) {
                skipped++;
                continue;
            }
            ClassRosterImportRow row = JSONUtil.toBean(item.getNormalizedData(), ClassRosterImportRow.class);
            if (!deptScopeService.isUsernameInScope(row.getUsername())) {
                throw exception(FORBIDDEN);
            }
            AdminUserDO current = support.getUser(row.getUsername());
            EduClassStudentDO relation = current == null ? null : support.getRelation(classId, current.getId());
            if (!EduImportSnapshotUtils.matches(item.getBeforeData(), combinedSnapshot(current, relation))) {
                throw exception(IMPORT_DATA_CHANGED);
            }
            if (!EduImportRowActionEnum.SKIP.name().equals(row.getAccountAction())) {
                support.saveStudent(toAccount(row), initialPassword);
                current = support.getUser(row.getUsername());
            }
            if (!EduImportRowActionEnum.SKIP.name().equals(row.getRelationAction())) {
                support.addOrRestoreRelation(classId, current.getId(), row.getRemark());
            }
            EduImportRowActionEnum action = EduImportRowActionEnum.valueOf(item.getAction());
            if (action == EduImportRowActionEnum.INSERT) inserted++;
            else if (action == EduImportRowActionEnum.UPDATE) updated++;
            else skipped++;
        }
        return new EduImportCommitResult(inserted, updated, skipped);
    }

    public byte[] export(Long classId) {
        support.validateRosterReadPermission(classId);
        EduClassDO clazz = support.getClass(classId);
        if (clazz == null) throw exception(CLASS_NOT_EXISTS);
        List<EduClassStudentDO> relations = support.getActiveRelationsByClass(classId);
        Set<Long> userIds = relations.stream().map(EduClassStudentDO::getStudentId).collect(Collectors.toSet());
        Map<Long, AdminUserDO> users = support.getUsers(userIds).stream()
                .collect(Collectors.toMap(AdminUserDO::getId, Function.identity()));
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(generate())) {
            Sheet sheet = workbook.getSheet("班级花名册");
            int rowIndex = 1;
            for (EduClassStudentDO relation : relations) {
                AdminUserDO user = users.get(relation.getStudentId());
                if (user == null) continue;
                write(sheet.createRow(rowIndex++), user.getUsername(), user.getNickname(), sexLabel(user.getSex()),
                        user.getMobile(), user.getEmail(), relation.getRemark());
            }
            return EduXlsxWorkbookUtils.writeWorkbook(workbook);
        } catch (Exception ex) {
            if (ex instanceof cn.nus.zknu.framework.common.exception.ServiceException serviceException) throw serviceException;
            throw exception(IMPORT_PREVIEW_INVALID);
        }
    }

    private Long parseClassId(String optionsJson) {
        try {
            JSONObject options = JSONUtil.parseObj(optionsJson);
            Long classId = options.getLong("classId");
            if (classId == null) throw exception(IMPORT_OPTIONS_INVALID);
            return classId;
        } catch (cn.nus.zknu.framework.common.exception.ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw exception(IMPORT_OPTIONS_INVALID);
        }
    }

    private void mergeAccount(ClassRosterImportRow row, AdminUserDO current, DeptDO classMajor, EduClassDO clazz) {
        if (current == null) {
            if (row.getSex() == null) row.setSex(0);
            row.setMajorCode(classMajor.getDeptCode());
            row.setGradeYear(clazz.getGradeYear());
            row.setStatus(CommonStatusEnum.ENABLE.getStatus());
            return;
        }
        UserProfileDO profile = support.getProfile(current.getId());
        DeptDO major = support.getDept(current.getDeptId());
        if (row.getNickname() == null) row.setNickname(current.getNickname());
        if (row.getSex() == null) row.setSex(current.getSex());
        if (row.getMobile() == null) row.setMobile(current.getMobile());
        if (row.getEmail() == null) row.setEmail(current.getEmail());
        row.setMajorCode(major == null ? null : major.getDeptCode());
        row.setGradeYear(profile == null ? null : profile.getGradeYear());
        row.setStatus(current.getStatus());
    }

    private Map<String, Object> combinedSnapshot(AdminUserDO user, EduClassStudentDO relation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("student", user == null ? null : support.studentSnapshot(user));
        map.put("relation", support.relationSnapshot(relation));
        return map;
    }

    private StudentAccountImportRow toAccount(ClassRosterImportRow row) {
        StudentAccountImportRow account = new StudentAccountImportRow();
        account.setUsername(row.getUsername());
        account.setNickname(row.getNickname());
        account.setSex(row.getSex());
        account.setMajorCode(row.getMajorCode());
        account.setGradeYear(row.getGradeYear());
        account.setMobile(row.getMobile());
        account.setEmail(row.getEmail());
        account.setStatus(row.getStatus());
        return account;
    }

    private void validateFields(ClassRosterImportRow row, List<String> errors) {
        if (row.getUsername() == null) errors.add("学号不能为空");
        else if (!USERNAME_PATTERN.matcher(row.getUsername()).matches()) errors.add("学号必须为 4 至 30 位字母或数字");
        if (row.getNickname() == null) errors.add("姓名不能为空");
        else if (row.getNickname().length() > 30) errors.add("姓名不能超过 30 个字符");
        if (row.getMobile() != null && !ValidationUtils.isMobile(row.getMobile())) errors.add("手机号格式不正确");
        if (row.getEmail() != null && (!EMAIL_PATTERN.matcher(row.getEmail()).matches() || row.getEmail().length() > 50)) {
            errors.add("邮箱格式不正确或超过 50 个字符");
        }
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

    private String nullable(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
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

}
