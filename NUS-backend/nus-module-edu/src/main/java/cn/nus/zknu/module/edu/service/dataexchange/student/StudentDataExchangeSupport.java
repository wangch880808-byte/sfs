package cn.nus.zknu.module.edu.service.dataexchange.student;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.security.core.service.SecurityFrameworkService;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.service.classstudent.EduClassStudentService;
import cn.nus.zknu.module.edu.service.clazz.EduClassService;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.edu.service.dataexchange.EduImportPasswordUtils;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserPageReqVO;
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

@Service
public class StudentDataExchangeSupport {

    public static final int STUDENT_USER_TYPE = 2;

    @Resource
    private SecurityFrameworkService securityFrameworkService;
    @Resource
    private EduDataScopeService dataScopeService;
    @Resource
    private EduDeptScopeService deptScopeService;
    @Resource
    private AdminUserService adminUserService;
    @Resource
    private UserProfileExtService profileService;
    @Resource
    private PermissionService permissionService;
    @Resource
    private RoleMapper roleMapper;
    @Resource
    private EduRoleApi eduRoleApi;
    @Resource
    private DeptService deptService;
    @Resource
    private EduClassService classService;
    @Resource
    private EduClassStudentService classStudentService;
    @Resource
    private EduClassStudentMapper relationMapper;

    public void validateMasterPermission() {
        if (!(dataScopeService.isGlobalAdmin() || deptScopeService.isDeptScopedAdmin())
                || !securityFrameworkService.hasPermission("system:user:create")
                || !securityFrameworkService.hasPermission("system:user:update")) {
            throw exception(FORBIDDEN);
        }
    }

    public void validateMasterReadPermission() {
        if (!(dataScopeService.isGlobalAdmin() || deptScopeService.isDeptScopedAdmin()) || !securityFrameworkService.hasPermission("system:user:query")) {
            throw exception(FORBIDDEN);
        }
    }

    public void validateRosterPermission(Long classId) {
        validateRosterBasePermission();
        if (deptScopeService.isDeptScopedAdmin()) {
            deptScopeService.validateClassInScope(classId);
        } else {
            dataScopeService.validateClassWriteAccess(classId);
        }
    }

    public void validateRosterBasePermission() {
        if (!securityFrameworkService.hasPermission("edu:class:update")) {
            throw exception(FORBIDDEN);
        }
    }

    public void validateRosterReadPermission(Long classId) {
        if (!securityFrameworkService.hasPermission("edu:class:query")) {
            throw exception(FORBIDDEN);
        }
        if (deptScopeService.isDeptScopedAdmin()) {
            deptScopeService.validateClassInScope(classId);
        } else {
            dataScopeService.validateClassAccess(classId);
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

    public AdminUserService.PreEncodedPasswords preEncodePasswords(Map<String, String> rawPasswordByUsername) {
        return adminUserService.preEncodePasswords(rawPasswordByUsername);
    }

    public AdminUserDO getUser(String username) {
        return adminUserService.getUserByUsername(username);
    }

    public UserProfileDO getProfile(Long userId) {
        return profileService.getUserProfileByUserId(userId);
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

    public EduClassDO getClass(Long classId) {
        return classService.getClass(classId);
    }

    public List<EduClassDO> getActiveClasses() {
        return classService.getSimpleClassList(null, null);
    }

    @Resource
    private EduClassMapper previewClassMapper;

    public List<EduClassDO> getScopeClasses(Collection<Long> classIds) {
        return classIds.isEmpty() ? Collections.emptyList()
                : DataPermissionUtils.executeIgnore(() -> previewClassMapper.selectBatchIds(classIds));
    }

    public List<EduClassStudentDO> getImportRelations(Collection<Long> classIds, Collection<Long> studentIds) {
        return classIds.isEmpty() || studentIds.isEmpty() ? Collections.emptyList()
                : relationMapper.selectImportRelationsIncludingDeleted(classIds, studentIds);
    }

    public List<EduClassDO> getClasses(Collection<Long> classIds) {
        return classService.getClassList(classIds);
    }

    public List<DeptDO> getDepts(Collection<Long> deptIds) {
        return deptService.getDeptList(deptIds);
    }

    public AdminUserDO getUserByMobile(String mobile) {
        return mobile == null ? null : adminUserService.getUserByMobile(mobile);
    }

    public AdminUserDO getUserByEmail(String email) {
        return email == null ? null : adminUserService.getUserByEmail(email);
    }

    public List<AdminUserDO> getStudentList(UserPageReqVO reqVO) {
        reqVO.setUserType(STUDENT_USER_TYPE);
        return adminUserService.getUserList(reqVO);
    }

    public List<AdminUserDO> getUsers(Collection<Long> userIds) {
        return adminUserService.getUserList(userIds);
    }

    public Map<Long, UserProfileDO> getProfileMap(Collection<Long> userIds) {
        return profileService.getUserProfileMap(userIds);
    }

    public EduClassStudentDO getRelation(Long classId, Long studentId) {
        if (classId == null || studentId == null) {
            return null;
        }
        return relationMapper.selectListIncludingDeleted(classId, List.of(studentId)).stream().findFirst().orElse(null);
    }

    public List<EduClassStudentDO> getActiveRelationsByStudents(Collection<Long> studentIds) {
        return relationMapper.selectListByStudentIds(studentIds);
    }

    public List<EduClassStudentDO> getActiveRelationsByClass(Long classId) {
        return relationMapper.selectListByClassId(classId);
    }

    public Long saveStudent(StudentAccountImportRow row) {
        return saveStudent(row, null);
    }

    public Long saveStudent(StudentAccountImportRow row, String initialPassword) {
        if (!deptScopeService.isUsernameInScope(row.getUsername())) {
            throw exception(FORBIDDEN);
        }
        AdminUserDO existing = getUser(row.getUsername());
        UserProfileDO existingProfile = existing == null ? null : getProfile(existing.getId());
        DeptDO major = row.getMajorCode() == null ? null : getDept(row.getMajorCode());
        if (row.getMajorCode() != null && major == null) {
            throw exception(cn.nus.zknu.module.edu.enums.ErrorCodeConstants.IMPORT_DATA_CHANGED);
        }
        deptScopeService.validateDeptInScope(major == null ? null : major.getId());
        if (existing != null) {
            deptScopeService.validateUserInScope(existing.getId());
        }
        UserSaveReqVO req = new UserSaveReqVO();
        req.setId(existing == null ? null : existing.getId());
        req.setUsername(row.getUsername());
        req.setNickname(row.getNickname());
        req.setUserType(STUDENT_USER_TYPE);
        req.setDeptId(major == null ? null : major.getId());
        req.setSex(row.getSex());
        req.setMobile(row.getMobile());
        req.setEmail(row.getEmail());
        Set<Long> roleIds = existing == null ? new LinkedHashSet<>()
                : new LinkedHashSet<>(permissionService.getUserRoleIdListByUserId(existing.getId()));
        syncIdentityRoles(roleIds);
        req.setRoleIds(roleIds);
        UserProfileExtReqVO profile = new UserProfileExtReqVO();
        profile.setGradeYear(row.getGradeYear());
        profile.setClassIds(existingProfile == null || existingProfile.getClassIds() == null
                ? Collections.emptySet() : new LinkedHashSet<>(existingProfile.getClassIds()));
        req.setProfile(profile);
        Long userId;
        if (existing == null) {
            req.setPassword(EduImportPasswordUtils.resolve(initialPassword, row.getUsername()));
            userId = adminUserService.createStudentUser(req);
        } else {
            adminUserService.updateUser(req);
            userId = existing.getId();
        }
        if (!Objects.equals(row.getStatus(), CommonStatusEnum.ENABLE.getStatus())) {
            adminUserService.updateUserStatus(userId, row.getStatus());
        } else if (existing != null && !Objects.equals(existing.getStatus(), row.getStatus())) {
            adminUserService.updateUserStatus(userId, row.getStatus());
        }
        return userId;
    }

    private void syncIdentityRoles(Set<Long> roleIds) {
        // 身份角色按编码查找（参数配置 edu.role.teacher-code / edu.role.student-code），不依赖各库的角色编号
        RoleDO teacherRole = roleMapper.selectByCode(eduRoleApi.getTeacherRoleCode());
        RoleDO studentRole = roleMapper.selectByCode(eduRoleApi.getStudentRoleCode());
        if (teacherRole == null || studentRole == null) {
            throw exception(cn.nus.zknu.module.edu.enums.ErrorCodeConstants.IMPORT_DATA_CHANGED);
        }
        roleIds.remove(teacherRole.getId());
        roleIds.remove(studentRole.getId());
        roleIds.add(studentRole.getId());
    }

    public void addOrRestoreRelation(Long classId, Long studentId, String remark) {
        classStudentService.addStudents(classId, List.of(studentId), remark);
    }

    public Map<String, Object> studentSnapshot(AdminUserDO user) {
        UserProfileDO profile = user == null ? null : getProfile(user.getId());
        DeptDO major = user == null ? null : getDept(user.getDeptId());
        return studentSnapshot(user, profile, major);
    }

    public Map<String, Object> studentSnapshot(AdminUserDO user, UserProfileDO profile, DeptDO major) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", user == null ? null : user.getUsername());
        map.put("nickname", user == null ? null : user.getNickname());
        map.put("sex", user == null ? null : user.getSex());
        map.put("majorCode", major == null ? null : major.getDeptCode());
        map.put("gradeYear", profile == null ? null : profile.getGradeYear());
        map.put("mobile", user == null ? null : user.getMobile());
        map.put("email", user == null ? null : user.getEmail());
        map.put("status", user == null ? null : user.getStatus());
        return map;
    }

    public Map<String, Object> studentSnapshot(StudentAccountImportRow row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", row.getUsername());
        map.put("nickname", row.getNickname());
        map.put("sex", row.getSex());
        map.put("majorCode", row.getMajorCode());
        map.put("gradeYear", row.getGradeYear());
        map.put("mobile", row.getMobile());
        map.put("email", row.getEmail());
        map.put("status", row.getStatus());
        return map;
    }

    public Map<String, Object> relationSnapshot(EduClassStudentDO relation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("state", relation == null ? "NONE" : Boolean.TRUE.equals(relation.getDeleted()) ? "DELETED" : "ACTIVE");
        map.put("remark", relation == null ? null : relation.getRemark());
        return map;
    }

    public boolean isRemarkRequired(EduClassDO clazz, String majorCode, Integer gradeYear) {
        DeptDO major = getDept(majorCode);
        return clazz != null && (major == null || !Objects.equals(clazz.getDeptId(), major.getId())
                || !Objects.equals(clazz.getGradeYear(), gradeYear));
    }

    public void validateNoIdentityConflict(AdminUserDO existing, List<String> errors) {
        if (existing != null && !Objects.equals(existing.getUserType(), STUDENT_USER_TYPE)) {
            errors.add("学号已被非学生账号占用");
        }
    }

    /**
     * 手机号、邮箱唯一：与该学号账号的原值相同（没有改动）时不算冲突，历史上的重复数据不影响覆盖更新；
     * 改成别人在用的值时写明占用的账号。
     */
    public void validateContactUniqueness(String username, String mobile, String email, List<String> errors) {
        AdminUserDO existing = getUser(username);
        Long existingId = existing == null ? null : existing.getId();
        if (mobile != null && (existing == null || !mobile.equals(existing.getMobile()))) {
            AdminUserDO mobileOwner = adminUserService.getOtherUserByMobile(mobile, existingId);
            if (mobileOwner != null) errors.add("手机号已被账号「" + mobileOwner.getUsername() + "」使用");
        }
        if (email != null && (existing == null || !email.equalsIgnoreCase(existing.getEmail()))) {
            AdminUserDO emailOwner = adminUserService.getOtherUserByEmail(email, existingId);
            if (emailOwner != null) errors.add("邮箱已被账号「" + emailOwner.getUsername() + "」使用");
        }
    }

    public Set<Long> activeClassIds(UserProfileDO profile) {
        return profile == null || CollUtil.isEmpty(profile.getClassIds())
                ? Collections.emptySet() : new LinkedHashSet<>(profile.getClassIds());
    }

}
