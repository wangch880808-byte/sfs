package cn.nus.zknu.module.system.service.user;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.collection.CollectionUtils;
import cn.nus.zknu.module.system.controller.admin.user.vo.profile.UserProfileUpdatePasswordReqVO;
import cn.nus.zknu.module.system.controller.admin.user.vo.profile.UserProfileUpdateReqVO;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserPageReqVO;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台用户 Service 接口
 *
 * @author hyh
 */
public interface AdminUserService {

    /**
     * 创建用户
     *
     * @param createReqVO 用户信息
     * @return 用户编号
     */
    Long createUser(@Valid UserSaveReqVO createReqVO);

    /** 创建教师，并强制合并教师身份角色。 */
    Long createTeacherUser(@Valid UserSaveReqVO createReqVO);

    /** 创建学生，并强制合并学生身份角色。 */
    Long createStudentUser(@Valid UserSaveReqVO createReqVO);

    /**
     * 批量导入前并行计算新账号的密码哈希（每个账号单独加盐，与逐个创建的结果等价）。
     * 返回的作用域关闭前，当前线程里创建这些账号时直接使用算好的哈希；账号的明文密码不一致时照常现算。
     *
     * @param rawPasswordByUsername 用户名 -> 明文密码
     */
    PreEncodedPasswords preEncodePasswords(Map<String, String> rawPasswordByUsername);

    /** {@link #preEncodePasswords} 的作用域，用 try-with-resources 关闭。 */
    interface PreEncodedPasswords extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * 修改用户
     *
     * @param updateReqVO 用户信息
     */
    void updateUser(@Valid UserSaveReqVO updateReqVO);

    /**
     * 更新用户的最后登陆信息
     *
     * @param id      用户编号
     * @param loginIp 登陆 IP
     */
    void updateUserLogin(Long id, String loginIp);

    /**
     * 修改用户个人信息
     *
     * @param id    用户编号
     * @param reqVO 用户个人信息
     */
    void updateUserProfile(Long id, @Valid UserProfileUpdateReqVO reqVO);

    /**
     * 修改用户个人密码
     *
     * @param id    用户编号
     * @param reqVO 更新用户个人密码
     */
    void updateUserPassword(Long id, @Valid UserProfileUpdatePasswordReqVO reqVO);

    /**
     * 修改密码
     *
     * @param id       用户编号
     * @param password 密码
     */
    void updateUserPassword(Long id, String password);

    /**
     * 修改状态
     *
     * @param id     用户编号
     * @param status 状态
     */
    void updateUserStatus(Long id, Integer status);

    /**
     * 删除用户
     *
     * @param id 用户编号
     */
    void deleteUser(Long id);

    /**
     * 批量删除用户
     *
     * @param ids 用户编号数组
     */
    void deleteUserList(List<Long> ids);

    /**
     * 通过用户名查询用户
     *
     * @param username 用户名
     * @return 用户对象信息
     */
    AdminUserDO getUserByUsername(String username);

    /**
     * 通过手机号获取用户
     *
     * @param mobile 手机号
     * @return 用户对象信息
     */
    AdminUserDO getUserByMobile(String mobile);

    AdminUserDO getUserByEmail(String email);

    /** 使用该手机号的其他账号（排除 excludeUserId）；历史上可能有重复，取第一个，没有返回 null */
    AdminUserDO getOtherUserByMobile(String mobile, Long excludeUserId);

    /** 使用该邮箱的其他账号（排除 excludeUserId）；历史上可能有重复，取第一个，没有返回 null */
    AdminUserDO getOtherUserByEmail(String email, Long excludeUserId);

    /**
     * 获得用户分页列表
     *
     * @param reqVO 分页条件
     * @return 分页列表
     */
    PageResult<AdminUserDO> getUserPage(UserPageReqVO reqVO);

    List<AdminUserDO> getUserList(UserPageReqVO reqVO);

    /**
     * 通过用户 ID 查询用户
     *
     * @param id 用户ID
     * @return 用户对象信息
     */
    AdminUserDO getUser(Long id);

    /**
     * 获得指定部门的用户数组
     *
     * @param deptIds 部门数组
     * @return 用户数组
     */
    List<AdminUserDO> getUserListByDeptIds(Collection<Long> deptIds);

    /**
     * 获得指定岗位的用户数组
     *
     * @param postIds 岗位数组
     * @return 用户数组
     */
    List<AdminUserDO> getUserListByPostIds(Collection<Long> postIds);

    /**
     * 获得用户列表
     *
     * @param ids 用户编号数组
     * @return 用户列表
     */
    List<AdminUserDO> getUserList(Collection<Long> ids);

    /**
     * 校验用户们是否有效。如下情况，视为无效：
     * 1. 用户编号不存在
     * 2. 用户被禁用
     *
     * @param ids 用户编号数组
     */
    void validateUserList(Collection<Long> ids);

    /**
     * 获得用户 Map
     *
     * @param ids 用户编号数组
     * @return 用户 Map
     */
    default Map<Long, AdminUserDO> getUserMap(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return new HashMap<>();
        }
        return CollectionUtils.convertMap(getUserList(ids), AdminUserDO::getId);
    }

    /**
     * 获得用户列表，基于名称模糊匹配
     *
     * @param nickname 名称
     * @return 用户列表
     */
    List<AdminUserDO> getUserListByNickname(String nickname);

    /**
     * 获得指定状态的用户们
     *
     * @param status 状态
     * @return 用户们
     */
    List<AdminUserDO> getUserListByStatus(Integer status);

    /** 下拉选项只需要精简字段，用户类型交由数据库筛选。 */
    List<AdminUserDO> getSimpleUserList(Integer userType);

    /**
     * 判断密码是否匹配
     *
     * @param rawPassword     未加密的密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    boolean isPasswordMatch(String rawPassword, String encodedPassword);

}
