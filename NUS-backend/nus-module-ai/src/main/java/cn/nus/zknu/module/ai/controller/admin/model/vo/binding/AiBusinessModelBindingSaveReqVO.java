package cn.nus.zknu.module.ai.controller.admin.model.vo.binding;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiBusinessModelBindingSaveReqVO {

    @NotEmpty(message = "业务码不能为空")
    private String businessCode;

    @NotNull(message = "模型不能为空")
    private Long modelId;

}
