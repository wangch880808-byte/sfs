package cn.nus.zknu.module.edu.service.dataexchange.user;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.common.util.validation.ValidationUtils;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchDO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchItemDO;
import cn.nus.zknu.module.edu.enums.importbatch.EduImportRowActionEnum;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataExchangeTemplate;
import cn.nus.zknu.module.edu.service.dataexchange.EduAccountPreviewData;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataExchangeOptionUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduImportSnapshotUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduXlsxWorkbookUtils;
import cn.nus.zknu.module.edu.service.importbatch.EduImportCommitResult;
import cn.nus.zknu.module.edu.service.importbatch.EduImportHandler;
import cn.nus.zknu.module.edu.service.importbatch.EduImportPreviewRow;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.dataobject.user.UserProfileDO;
import jakarta.annotation.Resource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.IMPORT_DATA_CHANGED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.IMPORT_PREVIEW_INVALID;

abstract class AbstractUserAccountDataExchangeHandler implements EduImportHandler, EduDataExchangeTemplate {

    static final String VERSION = "V1";
    private static final String SHEET_NAME = "账号数据";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]{4,30}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Resource
    private EduDeptScopeService deptScopeService;

    @Resource
    protected UserAccountDataExchangeSupport support;

    protected abstract int userType();

    protected abstract String permission();

    protected abstract String identityRoleCode();

    protected abstract String accountLabel();

    protected abstract boolean includeProfessionalTitle();

    protected abstract List<String> rules();

    protected abstract List<String> exampleRow();

    @Override
    public String getTemplateVersion() {
        return VERSION;
    }

    @Override
    public void validatePermission() {
        support.validatePermission(permission());
    }

    @Override
    public String getInitialPasswordMode() {
        return INITIAL_PASSWORD_MODE_FIXED;
    }

    @Override
    public String getDefaultInitialPassword() {
        return support.getInitialPassword();
    }

    @Override
    public byte[] generate() {
        List<String> headers = headers();
        Set<Integer> textColumns = new LinkedHashSet<>(Set.of(0, 2, 3, 4));
        List<EduXlsxWorkbookUtils.DictionaryEntry> dictionaries = new ArrayList<>(List.of(
                new EduXlsxWorkbookUtils.DictionaryEntry("性别", "未知 / 男 / 女", "也支持 0 / 1 / 2"),
                new EduXlsxWorkbookUtils.DictionaryEntry("状态", "正常 / 停用", "也支持 0 / 1")));
        if (includeProfessionalTitle()) {
            dictionaries.add(new EduXlsxWorkbookUtils.DictionaryEntry(
                    "职称", "助教 / 讲师 / 副教授 / 教授", "也支持 1 / 2 / 3 / 4"));
        }
        Map<String, List<String>> dropdownOptions = new LinkedHashMap<>();
        dropdownOptions.put("部门编码", EduDataExchangeOptionUtils.departmentOptions(
                support.getActiveDepartments(), null));
        if (includeProfessionalTitle()) {
            dropdownOptions.put("职称", support.getTeacherTitleOptions());
        }
        return EduXlsxWorkbookUtils.createTemplate(getBizType(), VERSION, rules(),
                List.of(new EduXlsxWorkbookUtils.SheetSpec(SHEET_NAME, headers, textColumns, exampleRow())),
                dictionaries, dropdownOptions);
    }

    @Override
    public List<EduImportPreviewRow> preview(byte[] fileContent, String optionsJson) {
        boolean updateSupport = parseUpdateSupport(optionsJson);
        try (XSSFWorkbook workbook = EduXlsxWorkbookUtils.open(fileContent)) {
            List<EduXlsxWorkbookUtils.RowData> rows = EduXlsxWorkbookUtils.readSheet(
                    workbook, SHEET_NAME, headers());
            List<ParsedAccount> parsed = rows.stream().map(this::parseAccount).toList();
            EduAccountPreviewData data = preload(parsed);
            Map<String, Long> identities = parsed.isEmpty() ? Collections.emptyMap() : support.getIdentityRoleIds();
            String identityCode = parsed.isEmpty() ? null : identityRoleCode();
            Map<Long, Set<Long>> roles = new HashMap<>();
            List<Candidate> candidates = new ArrayList<>();
            for (ParsedAccount row : parsed) {
                candidates.add(parse(row, updateSupport, data, identities, identityCode, roles));
            }
            validateFileDuplicates(candidates);
            for (Candidate candidate : candidates) {
                if (!deptScopeService.isLoadedDeptCodeInScope(data.scopeDeptCodes.get(candidate.row().getDeptCode()))
                        || !deptScopeService.isLoadedUsernameInScope(data.scopeUsers.get(candidate.row().getUsername()))) {
                    deptScopeService.markOutOfScope(candidate.preview());
                }
            }
            return candidates.stream().map(Candidate::preview).toList();
        } catch (cn.nus.zknu.framework.common.exception.ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw exception(IMPORT_PREVIEW_INVALID);
        }
    }

    @Override
    public EduImportCommitResult commit(EduImportBatchDO batch, List<EduImportBatchItemDO> validItems,
                                        String initialPassword) {
        Map<String, String> passwords = newAccountPasswords(validItems, initialPassword);
        if (passwords.isEmpty()) {
            return commitRows(validItems, initialPassword);
        }
        // 先并行算好新账号的密码哈希，逐行创建账号时直接使用
        try (var ignored = support.preEncodePasswords(passwords)) {
            return commitRows(validItems, initialPassword);
        }
    }

    private Map<String, String> newAccountPasswords(List<EduImportBatchItemDO> validItems, String initialPassword) {
        Map<String, String> passwords = new HashMap<>();
        String password = null;
        for (EduImportBatchItemDO item : validItems) {
            if (EduImportRowActionEnum.INSERT.name().equals(item.getAction())) {
                if (password == null) {
                    password = support.resolveNewAccountPassword(initialPassword);
                }
                passwords.put(JSONUtil.toBean(item.getNormalizedData(), UserAccountImportRow.class).getUsername(), password);
            }
        }
        return passwords;
    }

    private EduImportCommitResult commitRows(List<EduImportBatchItemDO> validItems, String initialPassword) {
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        for (EduImportBatchItemDO item : validItems) {
            EduImportRowActionEnum action = EduImportRowActionEnum.valueOf(item.getAction());
            if (action == EduImportRowActionEnum.SKIP) {
                skipped++;
                continue;
            }
            UserAccountImportRow row = JSONUtil.toBean(item.getNormalizedData(), UserAccountImportRow.class);
            if (!deptScopeService.isDeptCodeInScope(row.getDeptCode())
                    || !deptScopeService.isUsernameInScope(row.getUsername())) {
                throw exception(FORBIDDEN);
            }
            AdminUserDO current = support.getUser(row.getUsername());
            if (!EduImportSnapshotUtils.matches(item.getBeforeData(), support.snapshot(current))) {
                throw exception(IMPORT_DATA_CHANGED);
            }
            support.save(row, userType(), identityRoleCode(), initialPassword);
            if (action == EduImportRowActionEnum.INSERT) inserted++;
            else updated++;
        }
        return new EduImportCommitResult(inserted, updated, skipped);
    }

    private ParsedAccount parseAccount(EduXlsxWorkbookUtils.RowData source) {
        List<String> errors = new ArrayList<>();
        UserAccountImportRow row = new UserAccountImportRow();
        row.setUsername(nullable(source.get(accountLabel() + "*")));
        row.setNickname(nullable(source.get("姓名*")));
        row.setDeptCode(EduDataExchangeOptionUtils.parseSelectedCode(source.get("部门编码")));
        row.setEmail(nullable(source.get("邮箱")));
        row.setMobile(nullable(source.get("手机号")));
        row.setSex(parseSex(source.get("性别"), errors));
        row.setStatus(parseStatus(source.get("状态"), errors));
        if (includeProfessionalTitle()) {
            row.setProfessionalTitle(parseProfessionalTitle(source.get("职称"), errors));
        }
        return new ParsedAccount(source.rowNumber(), row, errors);
    }

    private Candidate parse(ParsedAccount source, boolean updateSupport, EduAccountPreviewData data,
                            Map<String, Long> identities, String identityCode, Map<Long, Set<Long>> roles) {
        UserAccountImportRow row = source.row();
        List<String> errors = source.errors();
        AdminUserDO existing = data.users.get(row.getUsername());
        mergeExisting(row, existing, data);
        validateFields(row, errors);
        validateIdentity(existing, errors);
        DeptDO dept = row.getDeptCode() == null ? null : data.deptCodes.get(row.getDeptCode());
        if (row.getDeptCode() != null && dept == null) errors.add("部门编码不存在");
        AdminUserDO mobileOwner = data.mobileOwner(row.getMobile(), existing);
        if (mobileOwner != null) errors.add("手机号已被账号「" + mobileOwner.getUsername() + "」使用");
        AdminUserDO emailOwner = data.emailOwner(row.getEmail(), existing);
        if (emailOwner != null) errors.add("邮箱已被账号「" + emailOwner.getUsername() + "」使用");
        Set<Long> currentRoles = existing == null ? Collections.emptySet()
                : roles.computeIfAbsent(existing.getId(), support::getRoleIds);
        Set<Long> expectedRoles = support.expectedRoleIds(currentRoles, identityCode, identities);
        Map<String, Object> before = existing == null ? null : support.snapshot(existing,
                data.depts.get(existing.getDeptId()), data.profiles.get(existing.getId()), currentRoles);
        Map<String, Object> after = support.snapshot(row, userType(), expectedRoles);
        EduImportRowActionEnum action = existing == null ? EduImportRowActionEnum.INSERT
                : Objects.equals(before, after) ? EduImportRowActionEnum.SKIP : EduImportRowActionEnum.UPDATE;
        if (existing != null && action == EduImportRowActionEnum.UPDATE && !updateSupport) {
            errors.add("账号已存在；如需更新，请开启覆盖已有账号");
        }
        EduImportPreviewRow preview = EduImportPreviewRow.builder()
                .sheetName(SHEET_NAME)
                .rowNumber(source.rowNumber())
                .businessKey(row.getUsername())
                .normalizedData(JSONUtil.toJsonStr(row))
                .action(action)
                .errors(errors)
                .beforeData(before == null ? null : JSONUtil.toJsonStr(before))
                .afterData(JSONUtil.toJsonStr(after))
                .build();
        return new Candidate(row, preview);
    }

    private void mergeExisting(UserAccountImportRow row, AdminUserDO existing, EduAccountPreviewData data) {
        if (existing == null) {
            if (row.getSex() == null) row.setSex(0);
            if (row.getStatus() == null) row.setStatus(CommonStatusEnum.ENABLE.getStatus());
            return;
        }
        DeptDO dept = data.depts.get(existing.getDeptId());
        UserProfileDO profile = data.profiles.get(existing.getId());
        if (row.getNickname() == null) row.setNickname(existing.getNickname());
        if (row.getDeptCode() == null) row.setDeptCode(dept == null ? null : dept.getDeptCode());
        if (row.getEmail() == null) row.setEmail(existing.getEmail());
        if (row.getMobile() == null) row.setMobile(existing.getMobile());
        if (row.getSex() == null) row.setSex(existing.getSex());
        if (row.getStatus() == null) row.setStatus(existing.getStatus());
        if (includeProfessionalTitle() && row.getProfessionalTitle() == null && profile != null) {
            row.setProfessionalTitle(profile.getProfessionalTitle());
        }
    }

    private void validateFields(UserAccountImportRow row, List<String> errors) {
        if (row.getUsername() == null) errors.add(accountLabel() + "不能为空");
        else if (!USERNAME_PATTERN.matcher(row.getUsername()).matches()) errors.add(accountLabel() + "必须为 4 至 30 位字母或数字");
        if (row.getNickname() == null) errors.add("姓名不能为空");
        else if (row.getNickname().length() > 30) errors.add("姓名不能超过 30 个字符");
        if (row.getMobile() != null && !ValidationUtils.isMobile(row.getMobile())) errors.add("手机号格式不正确");
        if (row.getEmail() != null && (!EMAIL_PATTERN.matcher(row.getEmail()).matches() || row.getEmail().length() > 50)) {
            errors.add("邮箱格式不正确或超过 50 个字符");
        }
    }

    private void validateIdentity(AdminUserDO existing, List<String> errors) {
        if (existing != null && !Objects.equals(existing.getUserType(), userType())) {
            errors.add(accountLabel() + "已被其他身份账号占用");
        }
    }

    private void validateFileDuplicates(List<Candidate> candidates) {
        Map<String, List<Candidate>> usernameGroups = group(candidates, candidate -> candidate.row().getUsername());
        Map<String, List<Candidate>> mobileGroups = group(candidates, candidate -> candidate.row().getMobile());
        Map<String, List<Candidate>> emailGroups = group(candidates, candidate -> candidate.row().getEmail());
        markDuplicates(usernameGroups, "文件内账号重复");
        markDuplicates(mobileGroups, "文件内手机号重复");
        markDuplicates(emailGroups, "文件内邮箱重复");
    }

    private Map<String, List<Candidate>> group(List<Candidate> candidates,
                                                java.util.function.Function<Candidate, String> keyExtractor) {
        Map<String, List<Candidate>> groups = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            String key = keyExtractor.apply(candidate);
            if (key != null) groups.computeIfAbsent(key.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(candidate);
        }
        return groups;
    }

    private void markDuplicates(Map<String, List<Candidate>> groups, String message) {
        groups.values().stream().filter(group -> group.size() > 1)
                .forEach(group -> group.forEach(candidate -> candidate.preview().getErrors().add(message)));
    }

    private boolean parseUpdateSupport(String optionsJson) {
        if (StrUtil.isBlank(optionsJson)) return false;
        JSONObject options = JSONUtil.parseObj(optionsJson);
        return Boolean.TRUE.equals(options.getBool("updateSupport", false));
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
                errors.add("状态只支持 0/1 或正常/停用");
                yield null;
            }
        };
    }

    private Integer parseProfessionalTitle(String value, List<String> errors) {
        String normalized = nullable(value);
        if (normalized == null) return null;
        return switch (normalized) {
            case "1", "助教", "助教（高校）" -> 1;
            case "2", "讲师", "讲师（高校）" -> 2;
            case "3", "副教授" -> 3;
            case "4", "教授" -> 4;
            default -> {
                errors.add("职称只支持助教、讲师、副教授、教授或 1 至 4");
                yield null;
            }
        };
    }

    private String nullable(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private List<String> headers() {
        List<String> headers = new ArrayList<>(List.of(
                accountLabel() + "*", "姓名*", "部门编码", "邮箱", "手机号", "性别", "状态"));
        if (includeProfessionalTitle()) headers.add("职称");
        return headers;
    }

    private EduAccountPreviewData preload(List<ParsedAccount> rows) {
        EduAccountPreviewData data = new EduAccountPreviewData();
        Set<String> usernames = rows.stream().map(row -> row.row().getUsername()).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        data.users.putAll(support.getUsersByUsernames(usernames));
        data.scopeUsers.putAll(support.getScopeUsersByUsernames(usernames));
        Set<Long> userIds = data.users.values().stream().map(AdminUserDO::getId).collect(Collectors.toSet());
        data.profiles.putAll(support.getProfileMap(userIds));
        Set<Long> deptIds = data.users.values().stream().map(AdminUserDO::getDeptId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        support.getDepts(deptIds).forEach(dept -> data.depts.put(dept.getId(), dept));
        Set<String> codes = rows.stream().map(row -> row.row().getDeptCode()).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        data.depts.values().stream().map(DeptDO::getDeptCode).filter(Objects::nonNull).forEach(codes::add);
        data.deptCodes.putAll(support.getDeptsByCodes(codes));
        data.scopeDeptCodes.putAll(support.getScopeDeptsByCodes(codes));
        Set<String> mobiles = rows.stream().map(row -> row.row().getMobile()).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> emails = rows.stream().map(row -> row.row().getEmail()).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        data.setContactOwners(support.getMobileOwners(mobiles), support.getEmailOwners(emails));
        return data;
    }

    private record ParsedAccount(int rowNumber, UserAccountImportRow row, List<String> errors) { }

    private record Candidate(UserAccountImportRow row, EduImportPreviewRow preview) {
    }

}
