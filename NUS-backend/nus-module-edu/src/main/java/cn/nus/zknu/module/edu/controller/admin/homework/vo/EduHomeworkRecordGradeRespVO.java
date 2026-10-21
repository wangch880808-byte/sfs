package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 批改、打回、撤销后的提交记录 Response VO")
@Data
public class EduHomeworkRecordGradeRespVO {

    @Schema(description = "记录 ID", example = "1024")
    private Long id;

    @Schema(description = "提交状态：1 待批改、2 已批改、3 已打回、4 已重新提交", example = "2")
    private Integer status;

    @Schema(description = "得分", example = "86")
    private Integer score;

    @Schema(description = "评语")
    private String comment;

    @Schema(description = "批改方式：1 手动、2 AI", example = "1")
    private Integer correctType;

    @Schema(description = "批改时间")
    private LocalDateTime correctTime;

    @Schema(description = "累计打回次数", example = "1")
    private Integer redoCount;

    @Schema(description = "重做截止时间")
    private LocalDateTime redoDeadline;

    @Schema(description = "更新时间；再次批改、打回时作为 expectedUpdateTime 传回")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime updateTime;

    @Schema(description = "最新 AI 初评状态，没有初评时为空", example = "4")
    private Integer aiReviewStatus;

    @Schema(description = "撤销凭据；为空表示这次操作不能撤销", example = "2048")
    private Long undoToken;

    @Schema(description = "撤销截止时间")
    private LocalDateTime undoDeadline;

    @Schema(description = "还能撤销的秒数，前端据此倒计时，不依赖本机时钟", example = "10")
    private Integer undoSeconds;

}
