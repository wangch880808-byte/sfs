package cn.nus.zknu.module.edu.dal.dataobject.clazz;

import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 班级 DO
 */
@TableName("edu_class")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduClassDO extends BaseDO {

    /**
     * 班级 ID
     */
    private Long id;
    /**
     * 班级名称
     */
    private String className;
    /**
     * 所属年级（入学年份）
     */
    private Integer gradeYear;
    /**
     * 部门 ID
     */
    private Long deptId;
    /**
     * 教育层次（1-专科/2-本科/3-硕士/4-博士）
     */
    private Long educationLevel;
    /**
     * 学制（2/3/4/5 年）
     */
    private Long schoolingYear;
    /**
     * 班级状态（0正常 1已归档）
     * <p>
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;

}
