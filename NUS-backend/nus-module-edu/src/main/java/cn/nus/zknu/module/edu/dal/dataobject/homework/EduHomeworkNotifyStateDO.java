package cn.nus.zknu.module.edu.dal.dataobject.homework;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 每个任务的提醒与汇总状态：教师催交限频、教师新提交汇总的统计位置。 */
@TableName("edu_homework_notify_state")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduHomeworkNotifyStateDO extends BaseDO {

    private Long id;
    private Long homeworkId;
    /** 最近一次教师催交时间 */
    private LocalDateTime manualRemindTime;
    /** 教师汇总的连续扫描游标（含）；有未通知提交时保留起点 */
    private LocalDateTime digestUntil;
    /** 最近一次抢占教师汇总通知的时间；限频与扫描位置分开。 */
    private LocalDateTime digestNotifyTime;

}
