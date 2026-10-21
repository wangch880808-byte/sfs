package cn.nus.zknu.module.edu.controller.admin.student.vo;

import cn.nus.zknu.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "学生端 - 校园资讯分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppStudentNewsPageReqVO extends PageParam {

    @Schema(description = "来源编码", example = "zsxw")
    private String sourceCode;

    @Schema(description = "关键词（标题或正文）", example = "师范")
    private String keyword;

}
