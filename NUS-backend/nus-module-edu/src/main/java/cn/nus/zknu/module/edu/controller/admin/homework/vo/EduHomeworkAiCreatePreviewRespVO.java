package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - AI新建任务预览 Response VO")
@Data
public class EduHomeworkAiCreatePreviewRespVO {

    @Schema(description = "预览编号")
    private String previewId;

    @Schema(description = "抽取题目数量", example = "3")
    private Integer questionCount;

    @Schema(description = "覆盖班级数量", example = "2")
    private Integer classCount;

    @Schema(description = "预览题目列表")
    private List<Item> items;

    @Data
    public static class Item {

        @Schema(description = "预览题目编号")
        private String previewItemId;

        @Schema(description = "源题目 ID")
        private Long sourceQuestionId;

        @Schema(description = "源题目标题")
        private String sourceTitle;

        @Schema(description = "源题目要求")
        private String sourceRequirement;

        @Schema(description = "AI新建任务标题")
        private String homeworkName;

        @Schema(description = "AI新建任务要求")
        private String requirement;

        @Schema(description = "是否生成成功")
        private Boolean success;

        @Schema(description = "错误信息")
        private String errorMessage;

        @Schema(description = "是否正在重新生成；为 true 时轮询任务状态接口取结果")
        private Boolean regenerating;
    }
}
