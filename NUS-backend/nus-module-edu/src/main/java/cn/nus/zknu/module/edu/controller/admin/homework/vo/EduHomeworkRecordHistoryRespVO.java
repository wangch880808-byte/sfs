package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 任务提交与评分轨迹。 */
@Data
public class EduHomeworkRecordHistoryRespVO {
    private LocalDateTime createTime;
    private String eventType;
    private Integer fromStatus;
    private Integer toStatus;
    private Integer score;
    private String comment;
    private LocalDateTime redoDeadline;
    private String operatorName;
}
