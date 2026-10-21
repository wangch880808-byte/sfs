package cn.nus.zknu.module.edu.dal.dataobject.classstudent;

import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 班级学生关联 DO
 */
@TableName("edu_class_student")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduClassStudentDO extends BaseDO {

    /**
     * 自增 ID
     */
    private Long id;

    /**
     * 班级 ID
     */
    private Long classId;

    /**
     * 学生 ID
     */
    private Long studentId;

    /**
     * 班级状态（0正常 1停用）
     * <p>
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

    /**
     * 加入时间
     */
    private LocalDateTime joinTime;

    /**
     * 备注
     */
    private String remark;

}
