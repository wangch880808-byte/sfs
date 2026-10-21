package cn.nus.zknu.module.ai.controller.admin.model.vo.binding;

import lombok.Data;

@Data
public class AiBusinessModelBindingRespVO {

    private String businessCode;
    private String businessName;
    private Long modelId;
    private String modelName;
    private Boolean available;

}
