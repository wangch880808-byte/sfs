package cn.nus.zknu.module.ai.controller.admin.model.vo.binding;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AiBusinessModelBindingBatchSaveReqVO {
    @NotEmpty(message = "业务绑定不能为空")
    @Size(max = 64)
    private List<@NotNull @Valid AiBusinessModelBindingSaveReqVO> bindings;
}
