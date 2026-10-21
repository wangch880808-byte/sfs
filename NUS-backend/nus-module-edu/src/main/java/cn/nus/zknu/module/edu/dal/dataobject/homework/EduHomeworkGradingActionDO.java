package cn.nus.zknu.module.edu.dal.dataobject.homework;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 批改操作：保存批改、打回前的快照，用于撤销期内恢复；同时是学生通知的延时发送队列。
 */
@TableName("edu_homework_grading_action")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduHomeworkGradingActionDO extends BaseDO {

    private Long id;
    private Long recordId;
    private Long homeworkId;
    private Long studentId;
    /** CORRECT / REDO，见 EduHomeworkGradingActionEnum */
    private String actionType;
    /** 对应的成绩轨迹 ID */
    private Long historyId;
    private Long operatorId;

    // ===== 操作前的提交记录 =====
    private Integer prevStatus;
    private Integer prevScore;
    private String prevComment;
    private Integer prevCorrectType;
    private LocalDateTime prevCorrectTime;
    private Integer prevRedoCount;
    private LocalDateTime prevRedoDeadline;

    // ===== 操作后的提交记录，撤销时据此判断是否又有变化 =====
    private Integer afterStatus;
    private LocalDateTime afterUpdateTime;

    // ===== 操作前的 AI 初评 =====
    private Long aiReviewId;
    private Integer aiPrevStatus;
    private String aiPrevDecision;
    private Integer aiPrevTeacherScore;
    private String aiPrevTeacherComment;

    /** 撤销截止时间；过后发送通知 */
    private LocalDateTime undoDeadline;
    /** 通知状态，见 EduHomeworkGradingActionEnum.NOTIFY_* */
    private Integer notifyStatus;
    private String notifyTemplate;
    /** 通知参数（JSON 对象） */
    private String notifyParams;
    private String notifyUrl;
    /** 通知发出或取消的时间 */
    private LocalDateTime notifyTime;
}
