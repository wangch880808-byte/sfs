package cn.nus.zknu.module.edu.service.dataexchange.organization;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.security.core.service.SecurityFrameworkService;
import cn.nus.zknu.module.edu.controller.admin.clazz.vo.EduClassPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.clazz.vo.EduClassSaveReqVO;
import cn.nus.zknu.module.edu.controller.admin.dataexchange.vo.EduMasterDataExportReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchDO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchItemDO;
import cn.nus.zknu.module.edu.enums.importbatch.EduImportRowActionEnum;
import cn.nus.zknu.module.edu.service.clazz.EduClassService;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataExchangeTemplate;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.edu.service.dataexchange.EduImportPreviewUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduImportSnapshotUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils;
import cn.nus.zknu.module.edu.service.importbatch.EduImportCommitResult;
import cn.nus.zknu.module.edu.service.importbatch.EduImportHandler;
import cn.nus.zknu.module.edu.service.importbatch.EduImportPreviewRow;
import cn.nus.zknu.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
import cn.nus.zknu.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.service.dept.DeptService;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import jakarta.annotation.Resource;
import org.apache.poi.ss.usermodel.Cell;
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

@Component
public class OrganizationDataExchangeHandler implements EduImportHandler, EduDataExchangeTemplate {

    public static final String BIZ_TYPE = "ORGANIZATION";
    public static final String VERSION = "V1";
    public static final List<String> ORGANIZATION_HEADERS = List.of(
            "组织编码*", "组织名称*", "组织类型*", "上级组织编码*", "排序", "状态", "负责人账号", "电话", "邮箱");
    public static final List<String> CLASS_HEADERS = List.of(
            "班级编号", "班级名称*", "专业编码*", "入学年份*", "培养层次*", "学制*", "状态", "备注");

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String DEPT_TYPE_SCHOOL = "SCHOOL";
    private static final String DEPT_TYPE_COLLEGE = "COLLEGE";
    private static final String DEPT_TYPE_MAJOR = "MAJOR";
    private static final Set<String> DEPT_TYPES = Set.of(DEPT_TYPE_SCHOOL, DEPT_TYPE_COLLEGE, DEPT_TYPE_MAJOR);

    @Resource
    private EduDeptScopeService deptScopeService;

    @Resource
    private SecurityFrameworkService securityFrameworkService;
    @Resource
    private DeptService deptService;
    @Resource
    private AdminUserService adminUserService;
    @Resource
    private EduClassService classService;

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
        return "组织班级导入模板_V1.xlsx";
    }

    @Override
    public void validatePermission() {
        if (!securityFrameworkService.hasPermission("edu:class:create")
                || !securityFrameworkService.hasPermission("edu:class:update")
                || (!canWriteOrganizations() && !deptScopeService.isDeptScopedAdmin())) {
            throw exception(FORBIDDEN);
        }
    }

    private boolean canWriteOrganizations() {
        return securityFrameworkService.hasPermission("system:dept:create")
                && securityFrameworkService.hasPermission("system:dept:update");
    }

    @Override
    public byte[] generate() {
        return EduXlsxWorkbookUtils.createTemplate(BIZ_TYPE, VERSION, List.of(
                        "带 * 的列为必填项；组织编码会自动去除首尾空格并转为大写。",
                        "更新时空单元格不会覆盖原值；导入不会删除组织或班级。",
                        "班级编号仅用于更新，新建班级时请留空；导出的班级数据会自动带出编号。",
                        "学校节点的上级组织编码填写 ROOT，学院隶属学校，专业隶属学院。",
                        "请勿修改工作表名称、表头和模板版本。"),
                List.of(
                        new EduXlsxWorkbookUtils.SheetSpec("学院专业", ORGANIZATION_HEADERS, Set.of(0, 3, 6, 7),
                                List.of("ZKNU", "周口师范学院", "学校", "ROOT", "1", "正常", "admin", "13800138000", "school@example.edu")),
                        new EduXlsxWorkbookUtils.SheetSpec("班级", CLASS_HEADERS, Set.of(0, 2),
                                List.of("", "计算机科学 1 班", "CS-SE", "2026", "本科", "4", "正常", "示例数据"))),
                List.of(
                        new EduXlsxWorkbookUtils.DictionaryEntry("组织类型", "SCHOOL / COLLEGE / MAJOR",
                                "学校 / 学院 / 专业"),
                        new EduXlsxWorkbookUtils.DictionaryEntry("状态", "0 / 1", "0 正常，1 停用"),
                        new EduXlsxWorkbookUtils.DictionaryEntry("培养层次", "1 / 2 / 3 / 4",
                                "1 专科，2 本科，3 硕士，4 博士"),
                        new EduXlsxWorkbookUtils.DictionaryEntry("学制", "2 / 3 / 4 / 5", "学制年数")));
    }

    @Override
    public List<EduImportPreviewRow> preview(byte[] fileContent, String optionsJson) {
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(fileContent)) {
            List<EduXlsxWorkbookUtils.RowData> organizationRows = EduXlsxWorkbookUtils.readSheet(
                    workbook, "学院专业", ORGANIZATION_HEADERS);
            List<EduXlsxWorkbookUtils.RowData> classRows = EduXlsxWorkbookUtils.readSheet(
                    workbook, "班级", CLASS_HEADERS);
            Map<String, DeptDO> existingDepts = deptService.getDeptList(new DeptListReqVO()).stream()
                    .collect(Collectors.toMap(DeptDO::getDeptCode, Function.identity(), (left, right) -> left));
            Map<Long, DeptDO> deptsById = existingDepts.values().stream()
                    .collect(Collectors.toMap(DeptDO::getId, Function.identity()));
            Map<String, OrganizationCandidate> candidates = parseOrganizations(organizationRows, existingDepts, deptsById);
            validateOrganizationRelations(candidates, existingDepts);
            List<EduImportPreviewRow> result = candidates.values().stream()
                    .map(OrganizationCandidate::previewRow).collect(Collectors.toCollection(ArrayList::new));
            result.addAll(parseClasses(classRows, existingDepts, candidates, deptsById));
            for (EduImportPreviewRow preview : result) {
                if ("学院专业".equals(preview.getSheetName())) {
                    OrganizationImportRow row = JSONUtil.toBean(preview.getNormalizedData(), OrganizationImportRow.class);
                    OrganizationCandidate current = candidates.get(row.getDeptCode());
                    String targetCode = current != null && current.current() != null
                            ? row.getDeptCode() : row.getParentDeptCode();
                    if (!deptScopeService.isExistingDeptCodeInScope(row.getDeptCode())
                            || !deptScopeService.isDeptCodeInScope(targetCode)
                            || (current != null && current.current() != null
                            && !deptScopeService.isDeptCodeInScope(row.getParentDeptCode()))) {
                        deptScopeService.markOutOfScope(preview);
                    } else if (!canWriteOrganizations()) {
                        deptScopeService.markSkipped(preview, "无组织维护权限");
                    }
                } else {
                    ClassImportRow row = JSONUtil.toBean(preview.getNormalizedData(), ClassImportRow.class);
                    if (!deptScopeService.isDeptCodeInScope(row.getMajorCode())
                            || (row.getClassId() != null && !deptScopeService.isClassInScope(row.getClassId()))) {
                        deptScopeService.markOutOfScope(preview);
                    }
                }
            }
            return result;
        } catch (cn.nus.zknu.framework.common.exception.ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw exception(cn.nus.zknu.module.edu.enums.ErrorCodeConstants.IMPORT_PREVIEW_INVALID);
        }
    }

    @Override
    public EduImportCommitResult commit(EduImportBatchDO batch, List<EduImportBatchItemDO> validItems,
                                        String initialPassword) {
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        List<EduImportBatchItemDO> organizationItems = validItems.stream()
                .filter(item -> "学院专业".equals(item.getSheetName()))
                .sorted(Comparator.comparingInt(this::organizationOrder))
                .toList();
        for (EduImportBatchItemDO item : organizationItems) {
            OrganizationImportRow row = JSONUtil.toBean(item.getNormalizedData(), OrganizationImportRow.class);
            EduImportRowActionEnum action = EduImportRowActionEnum.valueOf(item.getAction());
            if (action == EduImportRowActionEnum.SKIP) {
                skipped++;
                continue;
            }
            if (!canWriteOrganizations()) {
                throw exception(FORBIDDEN);
            }
            if (!deptScopeService.isExistingDeptCodeInScope(row.getDeptCode())) {
                throw exception(FORBIDDEN);
            }
            DeptDO current = deptService.getDeptByCode(row.getDeptCode());
            if (current != null) {
                deptScopeService.validateDeptInScope(current.getId());
            }
            validateCurrentSnapshot(item, current == null ? null : organizationSnapshot(current));
            DeptSaveReqVO reqVO = toDeptSaveReqVO(row, current);
            deptScopeService.validateDeptInScope(reqVO.getParentId());
            if (action == EduImportRowActionEnum.INSERT) {
                deptService.createDept(reqVO);
                inserted++;
            } else {
                deptService.updateDept(reqVO);
                updated++;
            }
        }
        for (EduImportBatchItemDO item : validItems) {
            if (!"班级".equals(item.getSheetName())) {
                continue;
            }
            ClassImportRow row = JSONUtil.toBean(item.getNormalizedData(), ClassImportRow.class);
            EduImportRowActionEnum action = EduImportRowActionEnum.valueOf(item.getAction());
            if (action == EduImportRowActionEnum.SKIP) {
                skipped++;
                continue;
            }
            if (row.getClassId() != null) {
                deptScopeService.validateClassInScope(row.getClassId());
            }
            EduClassDO current = row.getClassId() == null ? null : classService.getClass(row.getClassId());
            validateCurrentSnapshot(item, current == null ? null : classSnapshot(current));
            EduClassSaveReqVO reqVO = toClassSaveReqVO(row, current);
            if (action == EduImportRowActionEnum.INSERT) {
                classService.createClass(reqVO);
                inserted++;
            } else {
                classService.updateClass(reqVO);
                updated++;
            }
        }
        return new EduImportCommitResult(inserted, updated, skipped);
    }

    public byte[] export(EduMasterDataExportReqVO reqVO) {
        DeptListReqVO deptReq = new DeptListReqVO();
        deptReq.setDeptCode(reqVO.getDeptCode());
        deptReq.setDeptType(reqVO.getDeptType());
        deptReq.setName(reqVO.getDeptName());
        deptReq.setStatus(reqVO.getDeptStatus());
        List<DeptDO> depts = deptService.getDeptList(deptReq);

        EduClassPageReqVO classReq = new EduClassPageReqVO();
        classReq.setClassName(reqVO.getClassName());
        classReq.setGradeYear(reqVO.getGradeYear());
        classReq.setDeptId(reqVO.getDeptId());
        classReq.setEducationLevel(reqVO.getEducationLevel());
        classReq.setSchoolingYear(reqVO.getSchoolingYear());
        classReq.setStatus(reqVO.getClassStatus());
        List<EduClassDO> classes = classService.getClassList(classReq);

        List<DeptDO> allDepts = deptService.getDeptList(new DeptListReqVO());
        Map<Long, DeptDO> deptsById = allDepts.stream().collect(Collectors.toMap(DeptDO::getId, Function.identity()));
        Set<Long> leaderIds = depts.stream().map(DeptDO::getLeaderUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserDO> leaders = adminUserService.getUserMap(leaderIds);
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(generate())) {
            writeOrganizationExport(workbook.getSheet("学院专业"), depts, deptsById, leaders);
            writeClassExport(workbook.getSheet("班级"), classes, deptsById);
            return EduXlsxWorkbookUtils.writeWorkbook(workbook);
        } catch (Exception ex) {
            if (ex instanceof cn.nus.zknu.framework.common.exception.ServiceException serviceException) {
                throw serviceException;
            }
            throw exception(cn.nus.zknu.module.edu.enums.ErrorCodeConstants.IMPORT_PREVIEW_INVALID);
        }
    }

    private Map<String, OrganizationCandidate> parseOrganizations(List<EduXlsxWorkbookUtils.RowData> rows,
                                                                   Map<String, DeptDO> existing,
                                                                   Map<Long, DeptDO> byId) {
        Map<String, OrganizationCandidate> result = new LinkedHashMap<>();
        for (EduXlsxWorkbookUtils.RowData data : rows) {
            List<String> errors = new ArrayList<>();
            OrganizationImportRow row = new OrganizationImportRow();
            row.setDeptCode(code(data.get("组织编码*")));
            row.setName(required(data.get("组织名称*"), "组织名称", errors));
            row.setDeptType(parseDeptType(data.get("组织类型*"), errors));
            row.setParentDeptCode(code(data.get("上级组织编码*")));
            row.setSort(parseInteger(data.get("排序"), "排序", errors));
            row.setStatus(parseStatus(data.get("状态"), errors));
            row.setLeaderUsername(nullable(data.get("负责人账号")));
            row.setPhone(nullable(data.get("电话")));
            row.setEmail(nullable(data.get("邮箱")));
            if (row.getDeptCode() == null) errors.add("组织编码不能为空");
            if (row.getDeptCode() != null && row.getDeptCode().length() > 64) errors.add("组织编码不能超过 64 个字符");
            if (row.getName() != null && row.getName().length() > 30) errors.add("组织名称不能超过 30 个字符");
            if (row.getParentDeptCode() == null) errors.add("上级组织编码不能为空");
            if (row.getPhone() != null && row.getPhone().length() > 11) errors.add("电话不能超过 11 个字符");
            if (row.getEmail() != null && !EMAIL_PATTERN.matcher(row.getEmail()).matches()) errors.add("邮箱格式不正确");
            if (row.getLeaderUsername() != null) {
                AdminUserDO leader = adminUserService.getUserByUsername(row.getLeaderUsername());
                if (leader == null) errors.add("负责人账号不存在");
                else row.setLeaderUserId(leader.getId());
            }
            DeptDO current = row.getDeptCode() == null ? null : existing.get(row.getDeptCode());
            mergeOrganizationDefaults(row, current);
            Map<String, Object> before = current == null ? null : organizationSnapshot(current, byId);
            Map<String, Object> after = organizationSnapshot(row);
            EduImportRowActionEnum action = current == null ? EduImportRowActionEnum.INSERT
                    : Objects.equals(before, after) ? EduImportRowActionEnum.SKIP : EduImportRowActionEnum.UPDATE;
            OrganizationCandidate candidate = new OrganizationCandidate(row, current, errors,
                    preview("学院专业", data.rowNumber(), "ORG:" + StrUtil.blankToDefault(row.getDeptCode(), ""),
                            row, action, errors, before, after));
            if (row.getDeptCode() != null && !result.containsKey(row.getDeptCode())) result.put(row.getDeptCode(), candidate);
            else result.put("#ROW-" + data.rowNumber(), candidate);
        }
        EduImportPreviewUtils.markDuplicates(result.values().stream().map(OrganizationCandidate::previewRow).toList(),
                item -> JSONUtil.toBean(item.getNormalizedData(), OrganizationImportRow.class).getDeptCode(),
                "文件内组织编码重复");
        return result;
    }

    private void validateOrganizationRelations(Map<String, OrganizationCandidate> candidates,
                                               Map<String, DeptDO> existing) {
        for (OrganizationCandidate candidate : candidates.values()) {
            OrganizationImportRow row = candidate.row();
            if (row.getDeptType() == null || row.getParentDeptCode() == null) continue;
            if (DEPT_TYPE_SCHOOL.equals(row.getDeptType())) {
                if (!"ROOT".equals(row.getParentDeptCode())) candidate.errors().add("学校节点的上级组织编码必须为 ROOT");
                continue;
            }
            if (Objects.equals(row.getDeptCode(), row.getParentDeptCode())) {
                candidate.errors().add("组织不能以自身作为上级组织");
                continue;
            }
            String parentType = null;
            OrganizationCandidate parentCandidate = candidates.get(row.getParentDeptCode());
            if (parentCandidate != null) parentType = parentCandidate.row().getDeptType();
            else if (existing.containsKey(row.getParentDeptCode())) parentType = existing.get(row.getParentDeptCode()).getDeptType();
            if (parentType == null) candidate.errors().add("上级组织编码不存在");
            else if (DEPT_TYPE_COLLEGE.equals(row.getDeptType())
                    && !DEPT_TYPE_SCHOOL.equals(parentType)) candidate.errors().add("学院的上级组织必须是学校");
            else if (DEPT_TYPE_MAJOR.equals(row.getDeptType())
                    && !DEPT_TYPE_COLLEGE.equals(parentType)) candidate.errors().add("专业的上级组织必须是学院");
        }
    }

    private List<EduImportPreviewRow> parseClasses(List<EduXlsxWorkbookUtils.RowData> rows,
                                                    Map<String, DeptDO> existingDepts,
                                                    Map<String, OrganizationCandidate> organizationCandidates,
                                                    Map<Long, DeptDO> deptsById) {
        List<EduImportPreviewRow> result = new ArrayList<>();
        for (EduXlsxWorkbookUtils.RowData data : rows) {
            List<String> errors = new ArrayList<>();
            ClassImportRow row = new ClassImportRow();
            row.setClassId(parseOptionalLong(data.get("班级编号"), "班级编号", errors));
            row.setClassName(required(data.get("班级名称*"), "班级名称", errors));
            row.setMajorCode(code(data.get("专业编码*")));
            row.setGradeYear(parseInteger(data.get("入学年份*"), "入学年份", errors));
            row.setEducationLevel(parseEducationLevel(data.get("培养层次*"), errors));
            row.setSchoolingYear(parseLong(data.get("学制*"), "学制", errors));
            row.setStatus(parseStatus(data.get("状态"), errors));
            row.setRemark(nullable(data.get("备注")));
            if (row.getMajorCode() == null) errors.add("专业编码不能为空");
            if (row.getGradeYear() == null) errors.add("入学年份不能为空");
            if (row.getGradeYear() != null && (row.getGradeYear() < 1900 || row.getGradeYear() > 2100)) errors.add("入学年份不合法");
            if (row.getSchoolingYear() != null && !Set.of(2L, 3L, 4L, 5L).contains(row.getSchoolingYear())) errors.add("学制只支持 2/3/4/5 年");
            String majorType = null;
            OrganizationCandidate majorCandidate = organizationCandidates.get(row.getMajorCode());
            if (majorCandidate != null) majorType = majorCandidate.row().getDeptType();
            else if (existingDepts.containsKey(row.getMajorCode())) majorType = existingDepts.get(row.getMajorCode()).getDeptType();
            if (majorType == null) errors.add("专业编码不存在");
            else if (!DEPT_TYPE_MAJOR.equals(majorType)) errors.add("班级归属编码必须是专业节点");
            EduClassDO current = row.getClassId() == null ? null : classService.getClass(row.getClassId());
            if (row.getClassId() != null && current == null) errors.add("班级编号不存在");
            mergeClassDefaults(row, current, deptsById);
            Map<String, Object> before = current == null ? null : classSnapshot(current, deptsById);
            Map<String, Object> after = classSnapshot(row);
            EduImportRowActionEnum action = current == null ? EduImportRowActionEnum.INSERT
                    : Objects.equals(before, after) ? EduImportRowActionEnum.SKIP : EduImportRowActionEnum.UPDATE;
            String classKey = row.getClassId() == null
                    ? safe(row.getMajorCode()) + ":" + safe(row.getGradeYear()) + ":" + safe(row.getClassName())
                    : String.valueOf(row.getClassId());
            result.add(preview("班级", data.rowNumber(), "CLASS:" + classKey,
                    row, action, errors, before, after));
        }
        EduImportPreviewUtils.markDuplicates(result,
                item -> {
                    ClassImportRow row = JSONUtil.toBean(item.getNormalizedData(), ClassImportRow.class);
                    return row.getClassId() == null
                            ? safe(row.getMajorCode()) + ":" + safe(row.getGradeYear()) + ":" + safe(row.getClassName())
                            : "ID:" + row.getClassId();
                }, "文件内班级重复");
        return result;
    }

    private DeptSaveReqVO toDeptSaveReqVO(OrganizationImportRow row, DeptDO current) {
        DeptSaveReqVO req = new DeptSaveReqVO();
        req.setId(current == null ? null : current.getId());
        req.setDeptCode(row.getDeptCode());
        req.setName(row.getName());
        req.setDeptType(row.getDeptType());
        if ("ROOT".equals(row.getParentDeptCode())) req.setParentId(DeptDO.PARENT_ID_ROOT);
        else {
            DeptDO parent = deptService.getDeptByCode(row.getParentDeptCode());
            if (parent == null) throw exception(IMPORT_DATA_CHANGED);
            req.setParentId(parent.getId());
        }
        req.setSort(row.getSort());
        req.setStatus(row.getStatus());
        req.setLeaderUserId(row.getLeaderUserId());
        req.setPhone(row.getPhone());
        req.setEmail(row.getEmail());
        return req;
    }

    private EduClassSaveReqVO toClassSaveReqVO(ClassImportRow row, EduClassDO current) {
        DeptDO major = deptService.getDeptByCode(row.getMajorCode());
        if (major == null) throw exception(IMPORT_DATA_CHANGED);
        EduClassSaveReqVO req = new EduClassSaveReqVO();
        req.setId(current == null ? null : current.getId());
        req.setClassName(row.getClassName());
        req.setDeptId(major.getId());
        req.setGradeYear(row.getGradeYear());
        req.setEducationLevel(row.getEducationLevel());
        req.setSchoolingYear(row.getSchoolingYear());
        req.setStatus(row.getStatus());
        req.setRemark(row.getRemark());
        return req;
    }

    private void mergeOrganizationDefaults(OrganizationImportRow row, DeptDO current) {
        row.setSort(row.getSort() != null ? row.getSort() : current == null ? 0 : current.getSort());
        row.setStatus(row.getStatus() != null ? row.getStatus() : current == null
                ? CommonStatusEnum.ENABLE.getStatus() : current.getStatus());
        if (row.getLeaderUsername() == null && current != null) row.setLeaderUserId(current.getLeaderUserId());
        if (row.getPhone() == null && current != null) row.setPhone(current.getPhone());
        if (row.getEmail() == null && current != null) row.setEmail(current.getEmail());
    }

    private void mergeClassDefaults(ClassImportRow row, EduClassDO current, Map<Long, DeptDO> deptsById) {
        row.setStatus(row.getStatus() != null ? row.getStatus() : current == null
                ? CommonStatusEnum.ENABLE.getStatus() : current.getStatus());
        if (row.getRemark() == null && current != null) row.setRemark(current.getRemark());
        if (row.getMajorCode() == null && current != null && deptsById.get(current.getDeptId()) != null) {
            row.setMajorCode(deptsById.get(current.getDeptId()).getDeptCode());
        }
    }

    private void validateCurrentSnapshot(EduImportBatchItemDO item, Map<String, Object> current) {
        if (!EduImportSnapshotUtils.matches(item.getBeforeData(), current)) throw exception(IMPORT_DATA_CHANGED);
    }

    private int organizationOrder(EduImportBatchItemDO item) {
        OrganizationImportRow row = JSONUtil.toBean(item.getNormalizedData(), OrganizationImportRow.class);
        return switch (row.getDeptType()) {
            case "SCHOOL" -> 1;
            case "COLLEGE" -> 2;
            default -> 3;
        };
    }

    private Map<String, Object> organizationSnapshot(DeptDO dept) {
        DeptDO parent = DeptDO.PARENT_ID_ROOT.equals(dept.getParentId()) ? null : deptService.getDept(dept.getParentId());
        Map<Long, DeptDO> byId = parent == null ? Collections.emptyMap() : Map.of(parent.getId(), parent);
        return organizationSnapshot(dept, byId);
    }

    private Map<String, Object> organizationSnapshot(DeptDO dept, Map<Long, DeptDO> deptsById) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("deptCode", dept.getDeptCode());
        map.put("name", dept.getName());
        map.put("deptType", dept.getDeptType());
        DeptDO parent = deptsById.get(dept.getParentId());
        map.put("parentDeptCode", DeptDO.PARENT_ID_ROOT.equals(dept.getParentId()) ? "ROOT"
                : parent == null ? null : parent.getDeptCode());
        map.put("sort", dept.getSort());
        map.put("status", dept.getStatus());
        map.put("leaderUserId", dept.getLeaderUserId());
        map.put("phone", dept.getPhone());
        map.put("email", dept.getEmail());
        return map;
    }

    private Map<String, Object> organizationSnapshot(OrganizationImportRow row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("deptCode", row.getDeptCode());
        map.put("name", row.getName());
        map.put("deptType", row.getDeptType());
        map.put("parentDeptCode", row.getParentDeptCode());
        map.put("sort", row.getSort());
        map.put("status", row.getStatus());
        map.put("leaderUserId", row.getLeaderUserId());
        map.put("phone", row.getPhone());
        map.put("email", row.getEmail());
        return map;
    }

    private Map<String, Object> classSnapshot(EduClassDO clazz) {
        DeptDO dept = deptService.getDept(clazz.getDeptId());
        Map<Long, DeptDO> map = dept == null ? Collections.emptyMap() : Map.of(dept.getId(), dept);
        return classSnapshot(clazz, map);
    }

    private Map<String, Object> classSnapshot(EduClassDO clazz, Map<Long, DeptDO> deptsById) {
        ClassImportRow row = new ClassImportRow();
        row.setClassId(clazz.getId());
        row.setClassName(clazz.getClassName());
        DeptDO dept = deptsById.get(clazz.getDeptId());
        row.setMajorCode(dept == null ? null : dept.getDeptCode());
        row.setGradeYear(clazz.getGradeYear());
        row.setEducationLevel(clazz.getEducationLevel());
        row.setSchoolingYear(clazz.getSchoolingYear());
        row.setStatus(clazz.getStatus());
        row.setRemark(clazz.getRemark());
        return classSnapshot(row);
    }

    private Map<String, Object> classSnapshot(ClassImportRow row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("classId", row.getClassId());
        map.put("className", row.getClassName());
        map.put("majorCode", row.getMajorCode());
        map.put("gradeYear", row.getGradeYear());
        map.put("educationLevel", row.getEducationLevel());
        map.put("schoolingYear", row.getSchoolingYear());
        map.put("status", row.getStatus());
        map.put("remark", row.getRemark());
        return map;
    }

    private EduImportPreviewRow preview(String sheet, int rowNumber, String key, Object normalized,
                                        EduImportRowActionEnum action, List<String> errors,
                                        Map<String, Object> before, Map<String, Object> after) {
        return EduImportPreviewRow.builder().sheetName(sheet).rowNumber(rowNumber).businessKey(key)
                .normalizedData(JSONUtil.toJsonStr(normalized)).action(action).errors(errors)
                .beforeData(before == null ? null : JSONUtil.toJsonStr(before))
                .afterData(JSONUtil.toJsonStr(after)).build();
    }

    private String required(String value, String label, List<String> errors) {
        String normalized = nullable(value);
        if (normalized == null) errors.add(label + "不能为空");
        return normalized;
    }

    private String code(String value) {
        String normalized = nullable(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String nullable(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private String parseDeptType(String value, List<String> errors) {
        String normalized = code(value);
        if (normalized == null) {
            errors.add("组织类型不能为空");
            return null;
        }
        normalized = switch (normalized) {
            case "学校" -> "SCHOOL";
            case "学院" -> "COLLEGE";
            case "专业" -> "MAJOR";
            default -> normalized;
        };
        if (!DEPT_TYPES.contains(normalized)) errors.add("组织类型只支持学校、学院、专业");
        return normalized;
    }

    private Integer parseStatus(String value, List<String> errors) {
        String normalized = nullable(value);
        if (normalized == null) return null;
        return switch (normalized.toUpperCase(Locale.ROOT)) {
            case "0", "正常", "ENABLE" -> 0;
            case "1", "停用", "DISABLE" -> 1;
            default -> {
                errors.add("状态只支持 0（正常）或 1（停用）");
                yield null;
            }
        };
    }

    private Long parseEducationLevel(String value, List<String> errors) {
        String normalized = nullable(value);
        if (normalized == null) {
            errors.add("培养层次不能为空");
            return null;
        }
        return switch (normalized) {
            case "1", "专科" -> 1L;
            case "2", "本科" -> 2L;
            case "3", "硕士" -> 3L;
            case "4", "博士" -> 4L;
            default -> {
                errors.add("培养层次只支持专科、本科、硕士、博士或 1 至 4");
                yield null;
            }
        };
    }

    private Integer parseInteger(String value, String label, List<String> errors) {
        String normalized = nullable(value);
        if (normalized == null) return null;
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException ex) {
            errors.add(label + "必须是整数");
            return null;
        }
    }

    private Long parseLong(String value, String label, List<String> errors) {
        String normalized = nullable(value);
        if (normalized == null) {
            errors.add(label + "不能为空");
            return null;
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ex) {
            errors.add(label + "必须是整数");
            return null;
        }
    }

    private Long parseOptionalLong(String value, String label, List<String> errors) {
        String normalized = nullable(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ex) {
            errors.add(label + "必须是整数");
            return null;
        }
    }

    private void writeOrganizationExport(Sheet sheet, List<DeptDO> depts, Map<Long, DeptDO> byId,
                                         Map<Long, AdminUserDO> leaders) {
        for (int index = 0; index < depts.size(); index++) {
            DeptDO dept = depts.get(index);
            DeptDO parent = byId.get(dept.getParentId());
            AdminUserDO leader = leaders.get(dept.getLeaderUserId());
            writeValues(sheet.createRow(index + 1), List.of(
                    safe(dept.getDeptCode()), safe(dept.getName()), safe(dept.getDeptType()),
                    DeptDO.PARENT_ID_ROOT.equals(dept.getParentId()) ? "ROOT" : parent == null ? "" : safe(parent.getDeptCode()),
                    String.valueOf(dept.getSort()), String.valueOf(dept.getStatus()),
                    leader == null ? "" : safe(leader.getUsername()), safe(dept.getPhone()), safe(dept.getEmail())));
        }
    }

    private void writeClassExport(Sheet sheet, List<EduClassDO> classes, Map<Long, DeptDO> byId) {
        for (int index = 0; index < classes.size(); index++) {
            EduClassDO clazz = classes.get(index);
            DeptDO dept = byId.get(clazz.getDeptId());
            writeValues(sheet.createRow(index + 1), List.of(
                    safe(clazz.getId()), safe(clazz.getClassName()), dept == null ? "" : safe(dept.getDeptCode()),
                    String.valueOf(clazz.getGradeYear()), String.valueOf(clazz.getEducationLevel()),
                    String.valueOf(clazz.getSchoolingYear()), String.valueOf(clazz.getStatus()), safe(clazz.getRemark())));
        }
    }

    private void writeValues(Row row, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            Cell cell = row.createCell(index, CellType.STRING);
            cell.setCellValue(values.get(index));
        }
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record OrganizationCandidate(OrganizationImportRow row, DeptDO current, List<String> errors,
                                         EduImportPreviewRow previewRow) {
    }

}
