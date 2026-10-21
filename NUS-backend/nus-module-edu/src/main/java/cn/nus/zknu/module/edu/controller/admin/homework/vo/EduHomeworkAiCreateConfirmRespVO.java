package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - AI新建任务确认创建 Response VO")
@Data
public class EduHomeworkAiCreateConfirmRespVO {

    @Schema(description = "抽取题目数量", example = "3")
    private Integer questionCount;

    @Schema(description = "覆盖班级数量", example = "2")
    private Integer classCount;

    @Schema(description = "最终创建的任务数量", example = "6")
    private Integer createdCount;

}
