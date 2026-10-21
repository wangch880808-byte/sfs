package cn.nus.zknu.module.edu.controller.admin.student.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "学生端 - 校园资讯来源 Response VO")
@Data
public class AppStudentNewsSourceRespVO {

    @Schema(description = "来源编码", example = "zsxw")
    private String sourceCode;

    @Schema(description = "来源名称", example = "周师要闻")
    private String sourceName;

}
