package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 撤销批改或打回 Request VO")
@Data
public class EduHomeworkRecordUndoReqVO {

    @Schema(description = "批改、打回返回的撤销凭据", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "撤销凭据不能为空")
    private Long undoToken;

}
