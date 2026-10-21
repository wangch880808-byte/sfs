package cn.nus.zknu.module.system.dal.mysql.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** system 用户接口校验班级归属，避免依赖 edu 模块。 */
@Mapper
public interface UserClassScopeMapper {

    @Select("SELECT dept_id FROM edu_class WHERE id = #{classId} AND deleted = 0")
    Long selectDeptId(@Param("classId") Long classId);
}
