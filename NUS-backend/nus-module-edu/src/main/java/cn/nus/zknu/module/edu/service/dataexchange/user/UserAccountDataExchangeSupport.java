package cn.nus.zknu.module.edu.service.dataexchange.user;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.security.core.service.SecurityFrameworkService;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import cn.nus.zknu.module.edu.service.dataexchange.EduImportPasswordUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.system.api.dict.DictDataApi;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserProfileExtReqVO;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import cn.nus.zknu.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.dal.dataobject.permission.RoleDO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.dataobject.user.UserProfileDO;
import cn.nus.zknu.module.system.dal.mysql.permission.RoleMapper;
import cn.nus.zknu.module.system.service.dept.DeptService;
import cn.nus.zknu.module.system.service.permission.PermissionService;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import cn.nus.zknu.module.system.service.user.profile.UserProfileExtService;
import cn.nus.zknu.module.edu.service.dataexchange.EduAccountPreviewData;
import cn.nus.zknu.framework.datapermission.core.util.DataPermissionUtils;
import cn.nus.zknu.module.system.dal.dataobject.user.UserImportLookupDO;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import cn.nus.zknu.module.system.dal.mysql.dept.DeptMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.IMPORT_DATA_CHANGED;

@Service
public class UserAccountDataExchangeSupport {

    public static final int SYSTEM_USER_TYPE = 0;
    public static final int TEACHER_USER_TYPE = 1;
    public static final int STUDENT_USER_TYPE = 2;
    private static final int NON_STUDENT_GRADE_YEAR = 0;
    private static final String USER_INIT_PASSWORD_KEY = "system.user.init-password";
    private static final String TEACHER_TITLE_DICT_TYPE = "edu_teacher_title";

    @Resource
    private SecurityFrameworkService securityFrameworkService;
    @Resource
    private AdminUserService adminUserService;
    @Resource
    private EduDeptScopeService deptScopeService;
    @Resource
    private UserProfileExtService profileService;
    @Resource
    private DeptService deptService;
    @Resource
    private PermissionService permissionService;
    @Resource
    private RoleMapper roleMapper;
    @Resource
    private EduRoleApi eduRoleApi;
    @Resource
    private ConfigApi configApi;
    @Resource
    private DictDataApi dictDataApi;

    public void validatePermission(String permission) {
        if (!securityFrameworkService.hasPermission(permission)) {
            throw exception(FORBIDDEN);
        }
    }

    @Resource
    private AdminUserMapper previewUserMapper;
    @Resource
    private DeptMapper previewDeptMapper;

    public Map<String, AdminUserDO> getUsersByUsernames(Collection<String> usernames) {
        return usernames.isEmpty() ? Collections.emptyMap()
                : EduAccountPreviewData.usersByKey(previewUserMapper.selectImportByUsernames(usernames));
    }

    public Map<String, AdminUserDO> getScopeUsersByUsernames(Collection<String> usernames) {
        return DataPermissionUtils.executeIgnore(() -> getUsersByUsernames(usernames));
    }

    public Map<String, DeptDO> getDeptsByCodes(Collection<String> codes) {
        if (codes.isEmpty()) return Collections.emptyMap();
        // 与 DeptService.getDeptByCode 的 normalizeCode 完全一致，Map 仍使用原始键。
        Map<String, String> normalized = new LinkedHashMap<>();
        codes.forEach(code -> normalized.put(code,
                code == null || code.trim().isEmpty() ? null : code.trim().toUpperCase(Locale.ROOT)));
        return EduAccountPreviewData.deptsByKey(previewDeptMapper.selectImportByCodes(normalized));
    }

    public Map<String, DeptDO> getScopeDeptsByCodes(Collection<String> codes) {
        return DataPermissionUtils.executeIgnore(() -> getDeptsByCodes(codes));
    }

    public List<UserImportLookupDO> getMobileOwners(Collection<String> mobiles) {
        return mobiles.isEmpty() ? Collections.emptyList() : previewUserMapper.selectImportByMobiles(mobiles);
    }

    public List<UserImportLookupDO> getEmailOwners(Collection<String> emails) {
        return emails.isEmpty() ? Collections.emptyList() : previewUserMapper.selectImportByEmails(emails);
    }

    public AdminUserDO getUser(String username) {
        return adminUserService.getUserByUsername(username);
    }

    public AdminUserDO getUserByMobile(String mobile) {
        return mobile == null ? null : adminUserService.getUserByMobile(mobile);
    }

    public AdminUserDO getUserByEmail(String email) {
        return email == null ? null : adminUserService.getUserByEmail(email);
    }

    /** 使用该手机号的其他账号；与导入账号原值相同（没有改动）时不算冲突 */
    public AdminUserDO getConflictingMobileOwner(String mobile, AdminUserDO existing) {
        if (mobile == null || (existing != null && mobile.equals(existing.getMobile()))) return null;
        return adminUserService.getOtherUserByMobile(mobile, existing == null ? null : existing.getId());
    }

    /** 使用该邮箱的其他账号；与导入账号原值相同（没有改动，不区分大小写）时不算冲突 */
    public AdminUserDO getConflictingEmailOwner(String email, AdminUserDO existing) {
        if (email == null || (existing != null && email.equalsIgnoreCase(existing.getEmail()))) return null;
        return adminUserService.getOtherUserByEmail(email, existing == null ? null : existing.getId());
    }

    public DeptDO getDept(String deptCode) {
        return deptService.getDeptByCode(deptCode);
    }

    public DeptDO getDept(Long deptId) {
        return deptService.getDept(deptId);
    }

    public List<DeptDO> getActiveDepartments() {
        return deptService.getDeptList(new DeptListReqVO().setStatus(CommonStatusEnum.ENABLE.getStatus()));
    }

    public UserProfileDO getProfile(Long userId) {
        return profileService.getUserProfileByUserId(userId);
    }

    public Map<Long, UserProfileDO> getProfileMap(Collection<Long> ids) {
        return profileService.getUserProfileMap(ids);
    }

    public List<DeptDO> getDepts(Collection<Long> ids) {
        return deptService.getDeptList(ids);
    }

    /** 单次预检只解析一次身份角色配置；确认导入仍使用原方法实时查询。 */
    public Map<String, Long> getIdentityRoleIds() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String code : List.of(eduRoleApi.getTeacherRoleCode(), eduRoleApi.getStudentRoleCode())) {
            if (result.containsKey(code)) continue;
            RoleDO role = roleMapper.selectByCode(code);
            if (role == null) throw exception(IMPORT_DATA_CHANGED);
            result.put(code, role.getId());
        }
        return result;
    }

    public Set<Long> getRoleIds(Long userId) {
        return permissionService.getUserRoleIdListByUserId(userId);
    }

    public Set<Long> expectedRoleIds(Set<Long> currentRoles, String identityRoleCode, Map<String, Long> identities) {
        Set<Long> result = new LinkedHashSet<>(currentRoles);
        identities.forEach((code, id) -> {
            result.remove(id);
            if (Objects.equals(code, identityRoleCode)) result.add(id);
        });
        return result;
    }

    public String getInitialPassword() {
        return configApi.getConfigValueByKey(USER_INIT_PASSWORD_KEY);
    }

    /** 新账号的初始密码：导入时填写的优先，否则用系统参数里的默认初始密码。 */
    public String resolveNewAccountPassword(String initialPassword) {
        String passwordOverride = StrUtil.trimToNull(initialPassword);
        return EduImportPasswordUtils.resolve(passwordOverride, passwordOverride == null ? getInitialPassword() : null);
    }

    public AdminUserService.PreEncodedPasswords preEncodePasswords(Map<String, String> rawPasswordByUsername) {
        return adminUserService.preEncodePasswords(rawPasswordByUsername);
    }

    public List<String> getTeacherTitleOptions() {
        return dictDataApi.getDictDataList(TEACHER_TITLE_DICT_TYPE).stream()
                .filter(item -> CommonStatusEnum.ENABLE.getStatus().equals(item.getStatus()))
                .map(DictDataRespDTO::getLabel)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
    }

    /** 教师身份角色编码（参数配置 edu.role.teacher-code） */
    public String getTeacherRoleCode() {
        return eduRoleApi.getTeacherRoleCode();
    }

    public Set<Long> expectedRoleIds(AdminUserDO existing, String identityRoleCode) {
        Set<Long> result = existing == null ? new LinkedHashSet<>()
                : new LinkedHashSet<>(permissionService.getUserRoleIdListByUserId(existing.getId()));
        // 教师、学生身份角色互斥：先移除两者，再按本次导入的身份补回一个
        for (String code : List.of(eduRoleApi.getTeacherRoleCode(), eduRoleApi.getStudentRoleCode())) {
            RoleDO role = roleMapper.selectByCode(code);
            if (role == null) {
                throw exception(IMPORT_DATA_CHANGED);
            }
            result.remove(role.getId());
            if (Objects.equals(code, identityRoleCode)) {
                result.add(role.getId());
            }
        }
        return result;
    }

    public Long save(UserAccountImportRow row, int userType, String identityRoleCode, String initialPassword) {
        if (!deptScopeService.isUsernameInScope(row.getUsername())) {
            throw exception(FORBIDDEN);
        }
        AdminUserDO existing = getUser(row.getUsername());
        DeptDO dept = row.getDeptCode() == null ? null : getDept(row.getDeptCode());
        if (row.getDeptCode() != null && dept == null) {
            throw exception(IMPORT_DATA_CHANGED);
        }
        deptScopeService.validateDeptInScope(dept == null ? null : dept.getId());
        if (existing != null) {
            deptScopeService.validateUserInScope(existing.getId());
        }
        UserProfileDO currentProfile = existing == null ? null : getProfile(existing.getId());
        UserSaveReqVO req = new UserSaveReqVO();
        req.setId(existing == null ? null : existing.getId());
        req.setUsername(row.getUsername());
        req.setNickname(row.getNickname());
        req.setUserType(userType);
        req.setDeptId(dept == null ? null : dept.getId());
        req.setEmail(row.getEmail());
        req.setMobile(row.getMobile());
        req.setSex(row.getSex());
        req.setPostIds(existing == null ? Collections.emptySet() : null);
        req.setRoleIds(expectedRoleIds(existing, identityRoleCode));
        req.setProfile(toProfile(row, currentProfile));
        Long userId;
        if (existing == null) {
            req.setPassword(resolveNewAccountPassword(initialPassword));
            userId = adminUserService.createUser(req);
        } else {
            adminUserService.updateUser(req);
            userId = existing.getId();
        }
        if (existing == null || !Objects.equals(existing.getStatus(), row.getStatus())) {
            adminUserService.updateUserStatus(userId, row.getStatus());
        }
        return userId;
    }

    public Map<String, Object> snapshot(AdminUserDO user) {
        if (user == null) {
            return null;
        }
        DeptDO dept = getDept(user.getDeptId());
        UserProfileDO profile = getProfile(user.getId());
        return snapshot(user, dept, profile, permissionService.getUserRoleIdListByUserId(user.getId()));
    }

    public Map<String, Object> snapshot(AdminUserDO user, DeptDO dept, UserProfileDO profile, Set<Long> roles) {
        if (user == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("userType", user.getUserType());
        map.put("deptCode", dept == null ? null : dept.getDeptCode());
        map.put("email", user.getEmail());
        map.put("mobile", user.getMobile());
        map.put("sex", user.getSex());
        map.put("status", user.getStatus());
        map.put("professionalTitle", profile == null ? null : profile.getProfessionalTitle());
        map.put("roleIds", sortedRoleIds(roles));
        return map;
    }

    public Map<String, Object> snapshot(UserAccountImportRow row, int userType, Set<Long> roleIds) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", row.getUsername());
        map.put("nickname", row.getNickname());
        map.put("userType", userType);
        map.put("deptCode", row.getDeptCode());
        map.put("email", row.getEmail());
        map.put("mobile", row.getMobile());
        map.put("sex", row.getSex());
        map.put("status", row.getStatus());
        map.put("professionalTitle", row.getProfessionalTitle());
        map.put("roleIds", sortedRoleIds(roleIds));
        return map;
    }

    private UserProfileExtReqVO toProfile(UserAccountImportRow row, UserProfileDO current) {
        if (row.getProfessionalTitle() == null && current == null) {
            return null;
        }
        UserProfileExtReqVO profile = new UserProfileExtReqVO();
        profile.setProfessionalTitle(row.getProfessionalTitle());
        if (current != null) {
            profile.setGradeYear(current.getGradeYear());
            profile.setClassIds(current.getClassIds() == null ? null : new LinkedHashSet<>(current.getClassIds()));
        } else {
            // The installation baseline stores 0 for profiles where grade year is not applicable.
            profile.setGradeYear(NON_STUDENT_GRADE_YEAR);
        }
        return profile;
    }

    private List<Long> sortedRoleIds(Collection<Long> roleIds) {
        return Optional.ofNullable(roleIds).orElseGet(Collections::emptySet).stream().sorted().toList();
    }

}
