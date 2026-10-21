package cn.nus.zknu.module.system.service.user;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.collection.CollectionUtils;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.framework.datapermission.core.util.DataPermissionUtils;
import cn.nus.zknu.module.system.controller.admin.user.vo.profile.UserProfileUpdatePasswordReqVO;
import cn.nus.zknu.module.system.controller.admin.user.vo.profile.UserProfileUpdateReqVO;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserPageReqVO;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import cn.nus.zknu.module.system.dal.dataobject.dept.UserPostDO;
import cn.nus.zknu.module.system.dal.dataobject.permission.RoleDO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.mysql.dept.UserPostMapper;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import cn.nus.zknu.module.system.service.dept.DeptService;
import cn.nus.zknu.module.system.service.dept.PostService;
import cn.nus.zknu.module.system.service.oauth2.OAuth2TokenService;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.system.service.permission.PermissionService;
import cn.nus.zknu.module.system.service.permission.RoleService;
import cn.nus.zknu.module.system.service.user.classstudent.UserClassStudentService;
import cn.nus.zknu.module.system.service.user.profile.UserProfileExtService;
import com.google.common.annotations.VisibleForTesting;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.service.impl.DiffParseFunction;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.common.util.collection.CollectionUtils.*;
import static cn.nus.zknu.module.system.enums.ErrorCodeConstants.*;
import static cn.nus.zknu.module.system.enums.LogRecordConstants.*;

/**
 * 后台用户 Service 实现类
 *
 * @author hyh
 */
@Service("adminUserService")
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {

    private static final Integer DEFAULT_SYSTEM_USER_TYPE = 0;
    private static final Integer TEACHER_USER_TYPE = 1;
    private static final Integer STUDENT_USER_TYPE = 2;

    @Resource
    private AdminUserMapper userMapper;
    @Resource
    private UserDeptScopeService deptScopeService;

    @Resource
    private DeptService deptService;
    @Resource
    private PostService postService;
    @Resource
    private RoleService roleService;
    @Resource
    private PermissionService permissionService;
    @Resource
    private EduRoleApi eduRoleApi;
    @Resource
    private PasswordEncoder passwordEncoder;

    /** 批量导入时预先算好的密码哈希：用户名 -> [明文, 哈希]，只在 {@link #preEncodePasswords} 的作用域内有效 */
    private static final ThreadLocal<Map<String, String[]>> PRE_ENCODED_PASSWORDS = new ThreadLocal<>();
    @Resource
    @Lazy // 懒加载，避免循环依赖
    private OAuth2TokenService oauth2TokenService;

    @Resource
    private UserPostMapper userPostMapper;

    @Resource
    private UserProfileExtService userProfileExtService;
    @Resource
    private UserClassStudentService userClassStudentService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = SYSTEM_USER_TYPE, subType = SYSTEM_USER_CREATE_SUB_TYPE, bizNo = "{{#user.id}}",
            success = SYSTEM_USER_CREATE_SUCCESS)
    public Long createUser(UserSaveReqVO createReqVO) {
        deptScopeService.validateDeptInScope(createReqVO.getDeptId());
        validateScopedIdentity(createReqVO, null);
        // 1 校验正确性
        validateStudentRequiredFields(createReqVO);
        validateUserForCreateOrUpdate(null, createReqVO.getUsername(),
                createReqVO.getMobile(), createReqVO.getEmail(), createReqVO.getDeptId(),
                createReqVO.getPostIds(), createReqVO.getRoleIds());
        // 2.1 插入用户
        AdminUserDO user = BeanUtils.toBean(createReqVO, AdminUserDO.class);
        if (user.getUserType() == null) {
            user.setUserType(DEFAULT_SYSTEM_USER_TYPE);
        }
        user.setPasswordChangeRequired(Objects.equals(user.getUserType(), STUDENT_USER_TYPE));
        user.setStatus(CommonStatusEnum.ENABLE.getStatus()); // 默认开启
        user.setPassword(encodePassword(createReqVO.getUsername(), createReqVO.getPassword())); // 加密密码
        userMapper.insert(user);
        // 2.2 插入关联岗位
        if (CollectionUtil.isNotEmpty(user.getPostIds())) {
            userPostMapper.insertBatch(convertList(user.getPostIds(),
                    postId -> new UserPostDO().setUserId(user.getId()).setPostId(postId)));
        }
        // 2.3 插入关联角色
        if (CollectionUtil.isNotEmpty(createReqVO.getRoleIds())) {
            permissionService.assignUserRole(user.getId(), createReqVO.getRoleIds());
        }
        // 2.4 插入用户扩展信息
        userProfileExtService.saveOrUpdateUserProfile(user.getId(), user.getUserType(), createReqVO.getProfile());
        userClassStudentService.syncStudentClasses(user.getId(), user.getUserType(), getProfileClassIds(createReqVO));

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("user", user);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTeacherUser(UserSaveReqVO createReqVO) {
        return createUserWithIdentity(createReqVO, TEACHER_USER_TYPE, eduRoleApi.getTeacherRoleCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStudentUser(UserSaveReqVO createReqVO) {
        return createUserWithIdentity(createReqVO, STUDENT_USER_TYPE, eduRoleApi.getStudentRoleCode());
    }

    private Long createUserWithIdentity(UserSaveReqVO createReqVO, Integer userType, String roleCode) {
        RoleDO identityRole = roleService.getRoleList().stream()
                .filter(role -> roleCode.equals(role.getCode()))
                .filter(role -> CommonStatusEnum.ENABLE.getStatus().equals(role.getStatus()))
                .findFirst()
                .orElseThrow(() -> exception(USER_IDENTITY_ROLE_NOT_EXISTS, roleCode));
        Set<Long> roleIds = createReqVO.getRoleIds() == null
                ? new HashSet<>() : new HashSet<>(createReqVO.getRoleIds());
        roleIds.add(identityRole.getId());
        createReqVO.setUserType(userType);
        createReqVO.setRoleIds(roleIds);
        return createUser(createReqVO);
    }

    private void validateScopedIdentity(UserSaveReqVO reqVO, AdminUserDO existing) {
        if (!deptScopeService.isRestricted()) {
            return;
        }
        Integer userType = reqVO.getUserType() == null && existing != null
                ? existing.getUserType() : reqVO.getUserType();
        if ((!Objects.equals(userType, TEACHER_USER_TYPE) && !Objects.equals(userType, STUDENT_USER_TYPE))
                || (existing != null && !Objects.equals(userType, existing.getUserType()))) {
            throw exception(FORBIDDEN);
        }
        Set<Long> allowedRoleIds = existing == null ? new HashSet<>()
                : new HashSet<>(permissionService.getUserRoleIdListByUserId(existing.getId()));
        String identityCode = Objects.equals(userType, TEACHER_USER_TYPE)
                ? eduRoleApi.getTeacherRoleCode() : eduRoleApi.getStudentRoleCode();
        roleService.getRoleList().stream().filter(role -> identityCode.equals(role.getCode()))
                .forEach(role -> allowedRoleIds.add(role.getId()));
        if (reqVO.getRoleIds() != null && !allowedRoleIds.containsAll(reqVO.getRoleIds())) {
            throw exception(FORBIDDEN);
        }
    }

    private void validateStudentRequiredFields(UserSaveReqVO createReqVO) {
        if (!Objects.equals(createReqVO.getUserType(), STUDENT_USER_TYPE)) {
            return;
        }
        if (createReqVO.getDeptId() == null) {
            throw exception(STUDENT_DEPT_REQUIRED);
        }
        if (createReqVO.getProfile() == null || createReqVO.getProfile().getGradeYear() == null) {
            throw exception(STUDENT_GRADE_YEAR_REQUIRED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = SYSTEM_USER_TYPE, subType = SYSTEM_USER_UPDATE_SUB_TYPE, bizNo = "{{#updateReqVO.id}}",
            success = SYSTEM_USER_UPDATE_SUCCESS)
    public void updateUser(UserSaveReqVO updateReqVO) {
        deptScopeService.validateUserInScope(updateReqVO.getId());
        deptScopeService.validateDeptInScope(updateReqVO.getDeptId());
        updateReqVO.setPassword(null); // 特殊：此处不更新密码
        // 1. 校验正确性
        AdminUserDO oldUser = validateUserForCreateOrUpdate(updateReqVO.getId(), updateReqVO.getUsername(),
                updateReqVO.getMobile(), updateReqVO.getEmail(), updateReqVO.getDeptId(),
                updateReqVO.getPostIds(), updateReqVO.getRoleIds());

        validateScopedIdentity(updateReqVO, oldUser);

        // 2.1 更新用户
        AdminUserDO updateObj = BeanUtils.toBean(updateReqVO, AdminUserDO.class);
        deptScopeService.validateAffectedRows(userMapper.updateById(updateObj));
        // 2.2 更新岗位
        updateUserPost(updateReqVO, updateObj);
        // 2.3 更新角色
        updateUserRole(updateReqVO);
        // 2.4 更新用户扩展信息
        Integer userType = ObjUtil.defaultIfNull(updateReqVO.getUserType(), oldUser.getUserType());
        userProfileExtService.saveOrUpdateUserProfile(updateReqVO.getId(), userType, updateReqVO.getProfile());
        userClassStudentService.syncStudentClasses(updateReqVO.getId(), userType, getProfileClassIds(updateReqVO));

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable(DiffParseFunction.OLD_OBJECT, BeanUtils.toBean(oldUser, UserSaveReqVO.class));
        LogRecordContext.putVariable("user", oldUser);
    }

    private void updateUserPost(UserSaveReqVO reqVO, AdminUserDO updateObj) {
        if (reqVO.getPostIds() == null) {
            return;
        }
        Long userId = reqVO.getId();
        Set<Long> dbPostIds = convertSet(userPostMapper.selectListByUserId(userId), UserPostDO::getPostId);
        // 计算新增和删除的岗位编号
        Set<Long> postIds = CollUtil.emptyIfNull(updateObj.getPostIds());
        Collection<Long> createPostIds = CollUtil.subtract(postIds, dbPostIds);
        Collection<Long> deletePostIds = CollUtil.subtract(dbPostIds, postIds);
        // 执行新增和删除。对于已经授权的岗位，不用做任何处理
        if (!CollectionUtil.isEmpty(createPostIds)) {
            userPostMapper.insertBatch(convertList(createPostIds,
                    postId -> new UserPostDO().setUserId(userId).setPostId(postId)));
        }
        if (!CollectionUtil.isEmpty(deletePostIds)) {
            userPostMapper.deleteByUserIdAndPostId(userId, deletePostIds);
        }
    }

    private void updateUserRole(UserSaveReqVO reqVO) {
        if (reqVO.getRoleIds() == null) {
            return;
        }
        permissionService.assignUserRole(reqVO.getId(), reqVO.getRoleIds());
    }

    @Override
    public void updateUserLogin(Long id, String loginIp) {
        userMapper.updateById(new AdminUserDO().setId(id).setLoginIp(loginIp).setLoginDate(LocalDateTime.now()));
    }

    @Override
    public void updateUserProfile(Long id, UserProfileUpdateReqVO reqVO) {
        // 校验正确性
        AdminUserDO current = validateUserExists(id);
        // 关闭数据权限，唯一校验要能看到所有账号
        DataPermissionUtils.executeIgnore(() -> {
            validateEmailUnique(id, reqVO.getEmail(), current);
            validateMobileUnique(id, reqVO.getMobile(), current);
        });
        // 执行更新
        userMapper.updateById(BeanUtils.toBean(reqVO, AdminUserDO.class).setId(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserPassword(Long id, UserProfileUpdatePasswordReqVO reqVO) {
        AdminUserDO user = validateUserExists(id);
        if (!isPasswordMatch(reqVO.getOldPassword(), user.getPassword())) {
            throw exception(USER_PASSWORD_FAILED);
        }
        validateNewPassword(user, reqVO.getOldPassword(), reqVO.getNewPassword());
        // 执行更新
        AdminUserDO updateObj = new AdminUserDO().setId(id);
        updateObj.setPassword(encodePassword(reqVO.getNewPassword())); // 加密密码
        updateObj.setPasswordChangeRequired(false);
        userMapper.updateById(updateObj);
        oauth2TokenService.removeAccessToken(id, UserTypeEnum.ADMIN.getValue());
    }

    @Override
    @LogRecord(type = SYSTEM_USER_TYPE, subType = SYSTEM_USER_UPDATE_PASSWORD_SUB_TYPE, bizNo = "{{#id}}",
            success = SYSTEM_USER_UPDATE_PASSWORD_SUCCESS)
    public void updateUserPassword(Long id, String password) {
        deptScopeService.validateUserInScope(id);
        // 1. 校验用户存在
        AdminUserDO user = validateUserExists(id);

        // 2. 更新密码
        AdminUserDO updateObj = new AdminUserDO();
        updateObj.setId(id);
        updateObj.setPassword(encodePassword(password)); // 加密密码
        if (Objects.equals(user.getUserType(), STUDENT_USER_TYPE)) {
            updateObj.setPasswordChangeRequired(true);
        }
        deptScopeService.validateAffectedRows(userMapper.updateById(updateObj));

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("user", user);
        LogRecordContext.putVariable("newPassword", updateObj.getPassword());
    }

    @Override
    public void updateUserStatus(Long id, Integer status) {
        deptScopeService.validateUserInScope(id);
        // 校验用户存在
        validateUserExists(id);
        // 更新状态
        AdminUserDO updateObj = new AdminUserDO();
        updateObj.setId(id);
        updateObj.setStatus(status);
        deptScopeService.validateAffectedRows(userMapper.updateById(updateObj));

        // 如果是禁用用户，则删除其 Token 信息
        if (CommonStatusEnum.isDisable(status)) {
            oauth2TokenService.removeAccessToken(id, UserTypeEnum.ADMIN.getValue());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = SYSTEM_USER_TYPE, subType = SYSTEM_USER_DELETE_SUB_TYPE, bizNo = "{{#id}}",
            success = SYSTEM_USER_DELETE_SUCCESS)
    public void deleteUser(Long id) {
        deptScopeService.validateUserInScope(id);
        // 1. 校验用户存在
        AdminUserDO user = validateUserExists(id);

        // 2.1 删除用户
        deptScopeService.validateAffectedRows(userMapper.deleteById(id));
        // 2.2 删除用户关联数据
        permissionService.processUserDeleted(id);
        // 2.2 删除用户岗位
        userPostMapper.deleteByUserId(id);
        // 2.3 删除用户扩展信息
        userProfileExtService.deleteUserProfileByUserId(id);
        // 2.4 删除学生班级关联
        userClassStudentService.deleteByStudentId(id);

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("user", user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserList(List<Long> ids) {
        // 1. 批量删除用户
        ids.forEach(deptScopeService::validateUserInScope);
        deptScopeService.validateAffectedRows(userMapper.deleteByIds(ids));
        userProfileExtService.deleteUserProfileByUserIds(ids);
        userClassStudentService.deleteByStudentIds(ids);

        // 2. 批量删除用户关联数据
        ids.forEach(id -> {
            permissionService.processUserDeleted(id);
            userPostMapper.deleteByUserId(id);
        });
    }

    @Override
    public AdminUserDO getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    public AdminUserDO getUserByMobile(String mobile) {
        return StrUtil.isBlank(mobile) ? null : CollUtil.getFirst(userMapper.selectListByMobile(mobile));
    }

    @Override
    public AdminUserDO getUserByEmail(String email) {
        return StrUtil.isBlank(email) ? null : CollUtil.getFirst(userMapper.selectListByEmail(email));
    }

    @Override
    public AdminUserDO getOtherUserByMobile(String mobile, Long excludeUserId) {
        return StrUtil.isBlank(mobile) ? null : findOtherOwner(userMapper.selectListByMobile(mobile), excludeUserId);
    }

    @Override
    public AdminUserDO getOtherUserByEmail(String email, Long excludeUserId) {
        return StrUtil.isBlank(email) ? null : findOtherOwner(userMapper.selectListByEmail(email), excludeUserId);
    }

    @Override
    public PageResult<AdminUserDO> getUserPage(UserPageReqVO reqVO) {
        // 如果有角色编号，查询角色对应的用户编号
        Set<Long> userIds = null;
        if (reqVO.getRoleId() != null) {
            userIds = permissionService.getUserRoleIdListByRoleId(singleton(reqVO.getRoleId()));
            if (CollUtil.isEmpty(userIds)) {
                return PageResult.empty();
            }
        }

        // 分页查询
        return userMapper.selectPage(reqVO, getDeptCondition(reqVO.getDeptId()), userIds);
    }

    @Override
    public List<AdminUserDO> getUserList(UserPageReqVO reqVO) {
        Set<Long> userIds = null;
        if (reqVO.getRoleId() != null) {
            userIds = permissionService.getUserRoleIdListByRoleId(singleton(reqVO.getRoleId()));
            if (CollUtil.isEmpty(userIds)) {
                return Collections.emptyList();
            }
        }
        return userMapper.selectList(reqVO, getDeptCondition(reqVO.getDeptId()), userIds);
    }

    @Override
    public AdminUserDO getUser(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public List<AdminUserDO> getUserListByDeptIds(Collection<Long> deptIds) {
        if (CollUtil.isEmpty(deptIds)) {
            return Collections.emptyList();
        }
        return userMapper.selectListByDeptIds(deptIds);
    }

    @Override
    public List<AdminUserDO> getUserListByPostIds(Collection<Long> postIds) {
        if (CollUtil.isEmpty(postIds)) {
            return Collections.emptyList();
        }
        Set<Long> userIds = convertSet(userPostMapper.selectListByPostIds(postIds), UserPostDO::getUserId);
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        return userMapper.selectByIds(userIds);
    }

    @Override
    public List<AdminUserDO> getUserList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return userMapper.selectByIds(ids);
    }

    @Override
    public void validateUserList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        // 获得岗位信息
        List<AdminUserDO> users = userMapper.selectByIds(ids);
        Map<Long, AdminUserDO> userMap = CollectionUtils.convertMap(users, AdminUserDO::getId);
        // 校验
        ids.forEach(id -> {
            AdminUserDO user = userMap.get(id);
            if (user == null) {
                throw exception(USER_NOT_EXISTS);
            }
            if (!CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())) {
                throw exception(USER_IS_DISABLE, user.getNickname());
            }
        });
    }

    @Override
    public List<AdminUserDO> getUserListByNickname(String nickname) {
        return userMapper.selectListByNickname(nickname);
    }

    /**
     * 获得部门条件：查询指定部门的子部门编号们，包括自身
     *
     * @param deptId 部门编号
     * @return 部门编号集合
     */
    private Set<Long> getDeptCondition(Long deptId) {
        if (deptId == null) {
            return Collections.emptySet();
        }
        // 缓存返回的集合可能被复用，补自身编号前先复制，避免污染组织子树缓存。
        Set<Long> deptIds = new HashSet<>(deptService.getChildDeptIdListFromCache(deptId));
        deptIds.add(deptId); // 包括自身
        return deptIds;
    }

    private AdminUserDO validateUserForCreateOrUpdate(Long id, String username, String mobile, String email,
                                                      Long deptId, Set<Long> postIds, Set<Long> roleIds) {
        // 关闭数据权限，避免因为没有数据权限，查询不到数据，进而导致唯一校验不正确
        return DataPermissionUtils.executeIgnore(() -> {
            // 校验用户存在
            AdminUserDO user = validateUserExists(id);
            // 校验用户名唯一
            validateUsernameUnique(id, username);
            // 校验手机号、邮箱唯一（没有改动时不校验）
            validateMobileUnique(id, mobile, user);
            validateEmailUnique(id, email, user);
            // 校验部门处于开启状态
            deptService.validateDeptList(CollectionUtils.singleton(deptId));
            // 校验岗位处于开启状态
            postService.validatePostList(postIds);
            // 校验角色处于开启状态
            roleService.validateRoleList(roleIds);
            return user;
        });
    }

    @VisibleForTesting
    AdminUserDO validateUserExists(Long id) {
        if (id == null) {
            return null;
        }
        AdminUserDO user = userMapper.selectById(id);
        if (user == null) {
            if (deptScopeService.isRestricted()) {
                throw exception(FORBIDDEN);
            }
            throw exception(USER_NOT_EXISTS);
        }
        return user;
    }

    @VisibleForTesting
    void validateUsernameUnique(Long id, String username) {
        if (StrUtil.isBlank(username)) {
            return;
        }
        AdminUserDO user = userMapper.selectByUsername(username);
        if (user == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的用户
        if (id == null) {
            throw exception(USER_USERNAME_EXISTS);
        }
        if (!user.getId().equals(id)) {
            throw exception(USER_USERNAME_EXISTS);
        }
    }

    @VisibleForTesting
    void validateEmailUnique(Long id, String email) {
        validateEmailUnique(id, email, id == null ? null : userMapper.selectById(id));
    }

    /**
     * 邮箱唯一：没有改动（与当前账号原值相同，不区分大小写）时直接放行，历史上的重复数据不影响保存；
     * 改成别人在用的邮箱时，提示占用的账号，停用账号也写明。
     */
    private void validateEmailUnique(Long id, String email, AdminUserDO current) {
        if (StrUtil.isBlank(email) || (current != null && StrUtil.equalsIgnoreCase(email, current.getEmail()))) {
            return;
        }
        AdminUserDO owner = findOtherOwner(userMapper.selectListByEmail(email), id);
        if (owner != null) {
            throw exception(USER_EMAIL_EXISTS, owner.getUsername(), ownerLabel(owner));
        }
    }

    @VisibleForTesting
    void validateMobileUnique(Long id, String mobile) {
        validateMobileUnique(id, mobile, id == null ? null : userMapper.selectById(id));
    }

    /** 手机号唯一：规则同邮箱 */
    private void validateMobileUnique(Long id, String mobile, AdminUserDO current) {
        if (StrUtil.isBlank(mobile) || (current != null && StrUtil.equals(mobile, current.getMobile()))) {
            return;
        }
        AdminUserDO owner = findOtherOwner(userMapper.selectListByMobile(mobile), id);
        if (owner != null) {
            throw exception(USER_MOBILE_EXISTS, owner.getUsername(), ownerLabel(owner));
        }
    }

    private static AdminUserDO findOtherOwner(List<AdminUserDO> owners, Long id) {
        return owners.stream().filter(owner -> !Objects.equals(owner.getId(), id)).findFirst().orElse(null);
    }

    private static String ownerLabel(AdminUserDO owner) {
        String name = StrUtil.blankToDefault(owner.getNickname(), owner.getUsername());
        return CommonStatusEnum.isDisable(owner.getStatus()) ? name + "，已停用" : name;
    }

    /**
     * 校验旧密码
     *
     * @param id          用户 id
     * @param oldPassword 旧密码
     */
    @VisibleForTesting
    void validateOldPassword(Long id, String oldPassword) {
        AdminUserDO user = userMapper.selectById(id);
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        if (!isPasswordMatch(oldPassword, user.getPassword())) {
            throw exception(USER_PASSWORD_FAILED);
        }
    }

    private void validateNewPassword(AdminUserDO user, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 16
                || !newPassword.matches(".*[A-Za-z].*") || !newPassword.matches(".*[0-9].*")) {
            throw exception(USER_NEW_PASSWORD_INVALID);
        }
        if (newPassword.equals(user.getUsername())) {
            throw exception(USER_NEW_PASSWORD_SAME_AS_USERNAME);
        }
        if (newPassword.equals(oldPassword)) {
            throw exception(USER_NEW_PASSWORD_SAME_AS_OLD);
        }
    }

    @Override
    public List<AdminUserDO> getUserListByStatus(Integer status) {
        return userMapper.selectListByStatus(status);
    }

    @Override
    public List<AdminUserDO> getSimpleUserList(Integer userType) {
        return userMapper.selectSimpleList(CommonStatusEnum.ENABLE.getStatus(), userType);
    }

    @Override
    public boolean isPasswordMatch(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * 对密码进行加密
     *
     * @param password 密码
     * @return 加密后的密码
     */
    private String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    private String encodePassword(String username, String password) {
        Map<String, String[]> preEncoded = PRE_ENCODED_PASSWORDS.get();
        String[] entry = preEncoded == null || username == null ? null : preEncoded.get(username);
        if (entry != null && Objects.equals(entry[0], password)) {
            return entry[1];
        }
        return encodePassword(password);
    }

    @Override
    public PreEncodedPasswords preEncodePasswords(Map<String, String> rawPasswordByUsername) {
        Map<String, String[]> encoded = new HashMap<>();
        Map<String, Future<String>> futures = new LinkedHashMap<>();
        // 密码哈希强度为 12 时单个约 0.25 秒，逐个计算是大批量导入的主要耗时；最多占用一半 CPU，避免影响其他请求
        int threads = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        ExecutorService pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "password-encode");
            thread.setDaemon(true);
            return thread;
        });
        try {
            if (rawPasswordByUsername != null) {
                rawPasswordByUsername.forEach((username, raw) -> {
                    if (username != null && raw != null) {
                        futures.put(username, pool.submit(() -> passwordEncoder.encode(raw)));
                    }
                });
            }
            for (Map.Entry<String, Future<String>> entry : futures.entrySet()) {
                encoded.put(entry.getKey(), new String[]{rawPasswordByUsername.get(entry.getKey()), entry.getValue().get()});
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("密码加密被中断", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("密码加密失败", ex.getCause());
        } finally {
            pool.shutdownNow();
        }
        PRE_ENCODED_PASSWORDS.set(encoded);
        return PRE_ENCODED_PASSWORDS::remove;
    }

    private Set<Long> getProfileClassIds(UserSaveReqVO reqVO) {
        return reqVO == null || reqVO.getProfile() == null ? null : reqVO.getProfile().getClassIds();
    }

}
