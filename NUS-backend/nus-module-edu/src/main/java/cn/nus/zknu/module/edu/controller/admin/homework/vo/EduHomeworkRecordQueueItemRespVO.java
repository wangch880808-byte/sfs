package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 批改队列中的一份提交 Response VO")
@Data
public class EduHomeworkRecordQueueItemRespVO {

    @Schema(description = "记录 ID", example = "1024")
    private Long id;

    @Schema(description = "任务 ID", example = "1")
    private Long homeworkId;

    @Schema(description = "学生 ID", example = "10")
    private Long studentId;

    @Schema(description = "学生姓名")
    private String studentName;

    @Schema(description = "学号")
    private String studentNo;

    @Schema(description = "提交状态：1 待批改、2 已批改、3 已打回、4 已重新提交", example = "1")
    private Integer status;

    @Schema(description = "得分", example = "86")
    private Integer score;

    @Schema(description = "提交时间")
    private LocalDateTime submitTime;

    @Schema(description = "批改时间")
    private LocalDateTime correctTime;

    @Schema(description = "重做截止时间")
    private LocalDateTime redoDeadline;

    @Schema(description = "累计打回次数", example = "0")
    private Integer redoCount;

    @Schema(description = "更新时间；批改、打回时作为 expectedUpdateTime 传回")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime updateTime;

    @Schema(description = "最新 AI 初评状态，没有初评时为空", example = "2")
    private Integer aiReviewStatus;

}
