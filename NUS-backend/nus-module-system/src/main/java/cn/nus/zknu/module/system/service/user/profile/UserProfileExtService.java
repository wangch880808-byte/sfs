package cn.nus.zknu.module.system.service.user.profile;

import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserProfileExtReqVO;
import cn.nus.zknu.module.system.dal.dataobject.user.UserProfileDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 用户扩展信息 Service
 */
public interface UserProfileExtService {

    /**
     * 保存或更新用户扩展信息
     *
     * @param userId   用户 ID
     * @param userType 用户类型
     * @param reqVO    扩展信息
     */
    void saveOrUpdateUserProfile(Long userId, Integer userType, UserProfileExtReqVO reqVO);

    /**
     * 删除用户扩展信息
     *
     * @param userId 用户 ID
     */
    void deleteUserProfileByUserId(Long userId);

    /**
     * 批量删除用户扩展信息
     *
     * @param userIds 用户 ID 列表
     */
    void deleteUserProfileByUserIds(Collection<Long> userIds);

    /**
     * 根据用户 ID 获取扩展信息
     *
     * @param userId 用户 ID
     * @return 扩展信息
     */
    UserProfileDO getUserProfileByUserId(Long userId);

    /**
     * 根据用户 ID 列表获取扩展信息 Map
     *
     * @param userIds 用户 ID 列表
     * @return 用户扩展信息 Map
     */
    Map<Long, UserProfileDO> getUserProfileMap(Collection<Long> userIds);

    /**
     * 获取扩展信息精简列表
     *
     * @param userType 用户类型
     * @return 扩展信息列表
     */
    List<UserProfileDO> getSimpleList(Integer userType);

}
