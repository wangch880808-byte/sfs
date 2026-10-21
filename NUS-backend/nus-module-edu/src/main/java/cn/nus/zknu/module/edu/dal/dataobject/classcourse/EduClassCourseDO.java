package cn.nus.zknu.module.edu.dal.dataobject.classcourse;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 班级课程关联 DO
 */
@TableName("edu_class_course")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduClassCourseDO extends BaseDO {

    /**
     * ID
     */
    private Long id;
    /**
     * 班级 ID
     */
    private Long classId;
    /**
     * 课程 ID
     */
    private Long courseId;
    /**
     * 学期名称（示例：2025-2026-2）
     */
    private String semesterName;
    /**
     * 学期开始日期
     */
    private LocalDate semesterStartDate;
    /**
     * 学期结束日期
     */
    private LocalDate semesterEndDate;
    /**
     * 助教教师 ID 串，逗号分隔
     */
    private String assistantTeacherIds;

    /** 0 正常，1 已归档。 */
    private Integer status;

    /** 同步关联已提交、任务补发尚未完成；成功后在补发事务内清除。 */
    private Boolean backfillPending;

    /**
     * 公共课的认领教师（用户 ID）；未认领为 null。专属课程不使用。
     * 只能经认领、放弃接口写入，并保留原 updater（同步按 updater 判断自动关联）。
     */
    private Long claimTeacherId;

    /** 认领时间；未认领为 null。 */
    private LocalDateTime claimTime;

}
