package cn.nus.zknu.module.system.service.user.profile;

import cn.hutool.core.collection.CollUtil;
import cn.nus.zknu.framework.common.util.collection.CollectionUtils;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserProfileExtReqVO;
import cn.nus.zknu.module.system.dal.dataobject.user.UserProfileDO;
import cn.nus.zknu.module.system.dal.mysql.user.UserProfileMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 用户扩展信息 Service 实现
 */
@Service
public class UserProfileExtServiceImpl implements UserProfileExtService {

    @Resource
    private UserProfileMapper userProfileMapper;

    @Override
    public void saveOrUpdateUserProfile(Long userId, Integer userType, UserProfileExtReqVO reqVO) {
        if (userId == null) {
            return;
        }
        if (reqVO == null || isProfileEmpty(reqVO)) {
            userProfileMapper.deleteByUserId(userId);
            return;
        }

        UserProfileDO exists = userProfileMapper.selectByUserId(userId);
        UserProfileDO saveObj = BeanUtils.toBean(reqVO, UserProfileDO.class);
        saveObj.setUserId(userId);
        saveObj.setUserType(userType);
        if (exists == null) {
            userProfileMapper.insert(saveObj);
            return;
        }

        saveObj.setId(exists.getId());
        userProfileMapper.updateById(saveObj);
    }

    @Override
    public void deleteUserProfileByUserId(Long userId) {
        userProfileMapper.deleteByUserId(userId);
    }

    @Override
    public void deleteUserProfileByUserIds(Collection<Long> userIds) {
        userProfileMapper.deleteByUserIds(userIds);
    }

    @Override
    public UserProfileDO getUserProfileByUserId(Long userId) {
        return userProfileMapper.selectByUserId(userId);
    }

    @Override
    public Map<Long, UserProfileDO> getUserProfileMap(Collection<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        return CollectionUtils.convertMap(userProfileMapper.selectListByUserIds(userIds), UserProfileDO::getUserId);
    }

    @Override
    public List<UserProfileDO> getSimpleList(Integer userType) {
        return userProfileMapper.selectSimpleList(userType);
    }

    private boolean isProfileEmpty(UserProfileExtReqVO reqVO) {
        return reqVO.getProfessionalTitle() == null
                && reqVO.getGradeYear() == null
                && CollUtil.isEmpty(reqVO.getClassIds());
    }

}
