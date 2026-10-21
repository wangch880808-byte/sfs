package cn.nus.zknu.module.system.dal.mysql.user;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserPageReqVO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.nus.zknu.module.system.dal.dataobject.user.UserImportLookupDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface AdminUserMapper extends BaseMapperX<AdminUserDO> {

    default AdminUserDO selectByUsername(String username) {
        return selectOne(AdminUserDO::getUsername, username);
    }

    default int updatePasswordIfCurrent(Long id, String currentPassword, String newPassword) {
        return update(new AdminUserDO().setPassword(newPassword), new LambdaUpdateWrapper<AdminUserDO>()
                .eq(AdminUserDO::getId, id)
                .eq(AdminUserDO::getPassword, currentPassword));
    }

    default AdminUserDO selectByEmail(String email) {
        return selectOne(AdminUserDO::getEmail, email);
    }

    /** 唯一性校验用：历史数据里同一邮箱可能对应多个账号，不能用 selectOne */
    default List<AdminUserDO> selectListByEmail(String email) {
        return selectList(AdminUserDO::getEmail, email);
    }

    /** 唯一性校验用：历史数据里同一手机号可能对应多个账号，不能用 selectOne */
    default List<AdminUserDO> selectListByMobile(String mobile) {
        return selectList(AdminUserDO::getMobile, mobile);
    }

    default AdminUserDO selectByMobile(String mobile) {
        return selectOne(AdminUserDO::getMobile, mobile);
    }

    default PageResult<AdminUserDO> selectPage(UserPageReqVO reqVO, Collection<Long> deptIds, Collection<Long> userIds) {
        return selectPage(reqVO, buildQuery(reqVO, deptIds, userIds));
    }

    default List<AdminUserDO> selectList(UserPageReqVO reqVO, Collection<Long> deptIds, Collection<Long> userIds) {
        return selectList(buildQuery(reqVO, deptIds, userIds));
    }

    default LambdaQueryWrapperX<AdminUserDO> buildQuery(UserPageReqVO reqVO, Collection<Long> deptIds,
                                                        Collection<Long> userIds) {
        return new LambdaQueryWrapperX<AdminUserDO>()
                .likeIfPresent(AdminUserDO::getUsername, reqVO.getUsername())
                .likeIfPresent(AdminUserDO::getNickname, reqVO.getNickname())
                .likeIfPresent(AdminUserDO::getMobile, reqVO.getMobile())
                .eqIfPresent(AdminUserDO::getStatus, reqVO.getStatus())
                .eqIfPresent(AdminUserDO::getUserType, reqVO.getUserType())
                .betweenIfPresent(AdminUserDO::getCreateTime, reqVO.getCreateTime())
                .inIfPresent(AdminUserDO::getDeptId, deptIds)
                .inIfPresent(AdminUserDO::getId, userIds)
                .orderByDesc(AdminUserDO::getId);
    }

    default List<AdminUserDO> selectListByNickname(String nickname) {
        return selectList(new LambdaQueryWrapperX<AdminUserDO>().like(AdminUserDO::getNickname, nickname));
    }

    default List<AdminUserDO> selectListByStatus(Integer status) {
        return selectList(AdminUserDO::getStatus, status);
    }

    default List<AdminUserDO> selectSimpleList(Integer status, Integer userType) {
        return selectList(new LambdaQueryWrapperX<AdminUserDO>()
                .select(AdminUserDO::getId, AdminUserDO::getNickname, AdminUserDO::getUserType, AdminUserDO::getDeptId)
                .eq(AdminUserDO::getStatus, status)
                .eq(userType != null, AdminUserDO::getUserType, userType));
    }

    default List<AdminUserDO> selectListByDeptIds(Collection<Long> deptIds) {
        return selectList(AdminUserDO::getDeptId, deptIds);
    }

    /** 由数据库匹配输入键，保留大小写、重音及尾部空格的原查询语义。调用方保证非空集合。 */
    @Select("<script>SELECT u.*, k.lookup_key FROM system_users u JOIN ("
            + "<foreach collection='keys' item='key' separator=' UNION ALL '>SELECT CONCAT(#{key}, '') AS lookup_key</foreach>"
            + ") k ON u.username = k.lookup_key WHERE u.deleted = 0 AND u.username IN "
            + "<foreach collection='keys' item='key' open='(' separator=',' close=')'>#{key}</foreach></script>")
    List<UserImportLookupDO> selectImportByUsernames(@Param("keys") Collection<String> keys);

    /** 由数据库匹配输入键，保留大小写、重音及尾部空格的原查询语义。调用方保证非空集合。 */
    @Select("<script>SELECT u.*, k.lookup_key FROM system_users u JOIN ("
            + "<foreach collection='keys' item='key' separator=' UNION ALL '>SELECT CONCAT(#{key}, '') AS lookup_key</foreach>"
            + ") k ON u.mobile = k.lookup_key WHERE u.deleted = 0 AND u.mobile IN "
            + "<foreach collection='keys' item='key' open='(' separator=',' close=')'>#{key}</foreach></script>")
    List<UserImportLookupDO> selectImportByMobiles(@Param("keys") Collection<String> keys);

    /** 由数据库匹配输入键，保留大小写、重音及尾部空格的原查询语义。调用方保证非空集合。 */
    @Select("<script>SELECT u.*, k.lookup_key FROM system_users u JOIN ("
            + "<foreach collection='keys' item='key' separator=' UNION ALL '>SELECT CONCAT(#{key}, '') AS lookup_key</foreach>"
            + ") k ON u.email = k.lookup_key WHERE u.deleted = 0 AND u.email IN "
            + "<foreach collection='keys' item='key' open='(' separator=',' close=')'>#{key}</foreach></script>")
    List<UserImportLookupDO> selectImportByEmails(@Param("keys") Collection<String> keys);

}
