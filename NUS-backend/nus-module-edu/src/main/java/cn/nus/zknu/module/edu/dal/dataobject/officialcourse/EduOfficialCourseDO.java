package cn.nus.zknu.module.edu.dal.dataobject.officialcourse;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 正式课程 DO
 */
@TableName("edu_official_course")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduOfficialCourseDO extends BaseDO {

    /**
     * 课程 ID
     */
    private Long id;
    /**
     * 部门 ID
     */
    private Long deptId;
    /**
     * 课程编码
     */
    private String code;
    /**
     * 课程名称
     */
    private String name;
    /**
     * 学分
     */
    private BigDecimal credit;
    /**
     * 课程状态（0正常 1已归档）
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;

}
