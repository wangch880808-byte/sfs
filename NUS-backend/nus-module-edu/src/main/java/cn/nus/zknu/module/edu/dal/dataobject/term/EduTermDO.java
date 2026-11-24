package cn.nus.zknu.module.edu.dal.dataobject.term;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 学期 DO
 */
@TableName("edu_term")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduTermDO extends BaseDO {

    /**
     * 学期 ID
     */
    private Long id;
    /**
     * 组织 ID
     */
    private Long organizationId;
    /**
     * 学期编码
     */
    private String code;
    /**
     * 学期名称
     */
    private String name;
    /**
     * 开始日期
     */
    private LocalDate startsOn;
    /**
     * 结束日期
     */
    private LocalDate endsOn;
    /**
     * 学期状态（0正常 1已归档）
     */
    private Integer status;

}
