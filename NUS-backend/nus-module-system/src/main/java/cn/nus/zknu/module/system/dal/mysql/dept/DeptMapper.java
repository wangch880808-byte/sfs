package cn.nus.zknu.module.system.dal.mysql.dept;

import cn.nus.zknu.framework.mybatis.core.mapper.BaseMapperX;
import cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.nus.zknu.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptImportLookupDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface DeptMapper extends BaseMapperX<DeptDO> {

    default List<DeptDO> selectList(DeptListReqVO reqVO) {
        return selectList(new LambdaQueryWrapperX<DeptDO>()
                .likeIfPresent(DeptDO::getDeptCode, reqVO.getDeptCode())
                .eqIfPresent(DeptDO::getDeptType, reqVO.getDeptType())
                .likeIfPresent(DeptDO::getName, reqVO.getName())
                .eqIfPresent(DeptDO::getStatus, reqVO.getStatus()));
    }

    default DeptDO selectByParentIdAndName(Long parentId, String name) {
        return selectOne(DeptDO::getParentId, parentId, DeptDO::getName, name);
    }

    default DeptDO selectByDeptCode(String deptCode) {
        return selectOne(DeptDO::getDeptCode, deptCode);
    }

    default Long selectCountByParentId(Long parentId) {
        return selectCount(DeptDO::getParentId, parentId);
    }

    default List<DeptDO> selectListByParentId(Collection<Long> parentIds) {
        return selectList(DeptDO::getParentId, parentIds);
    }

    default List<DeptDO> selectListByLeaderUserId(Long id) {
        return selectList(DeptDO::getLeaderUserId, id);
    }

    /** 由数据库匹配输入键，保留大小写、重音及尾部空格的原查询语义。调用方保证非空集合。 */
    @Select("<script>SELECT u.*, k.lookup_key FROM system_dept u JOIN ("
            + "<foreach collection='keys' index='key' item='code' separator=' UNION ALL '>SELECT CONCAT(#{key}, '') AS lookup_key, CONCAT(#{code}, '') AS lookup_code</foreach>"
            + ") k ON u.dept_code = k.lookup_code WHERE u.deleted = 0 AND u.dept_code IN "
            + "<foreach collection='keys' item='code' open='(' separator=',' close=')'>#{code}</foreach></script>")
    List<DeptImportLookupDO> selectImportByCodes(@Param("keys") Map<String, String> keys);

}
