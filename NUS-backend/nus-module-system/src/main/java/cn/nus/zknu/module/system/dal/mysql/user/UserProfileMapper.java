package cn.nus.zknu.module.system.dal.mysql.user;

import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.system.dal.dataobject.user.UserProfileDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface UserProfileMapper extends BaseMapperX<UserProfileDO> {

    default UserProfileDO selectByUserId(Long userId) {
        return selectOne(UserProfileDO::getUserId, userId);
    }

    default List<UserProfileDO> selectListByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        return selectList(UserProfileDO::getUserId, userIds);
    }

    default List<UserProfileDO> selectSimpleList(Integer userType) {
        return selectList(new LambdaQueryWrapperX<UserProfileDO>()
                .eqIfPresent(UserProfileDO::getUserType, userType)
                .orderByDesc(UserProfileDO::getId));
    }

    default List<UserProfileDO> selectListByUserTypeAndGradeYear(Integer userType, Integer gradeYear) {
        return selectList(new LambdaQueryWrapperX<UserProfileDO>()
                .eqIfPresent(UserProfileDO::getUserType, userType)
                .eqIfPresent(UserProfileDO::getGradeYear, gradeYear)
                .orderByDesc(UserProfileDO::getId));
    }

    default void deleteByUserId(Long userId) {
        delete(new LambdaQueryWrapper<UserProfileDO>().eq(UserProfileDO::getUserId, userId));
    }

    default void deleteByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        delete(new LambdaQueryWrapper<UserProfileDO>().in(UserProfileDO::getUserId, userIds));
    }

}
