package cn.nus.zknu.module.ai.controller.admin.model.vo.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiModelTestRespVO {
    private boolean success;
    private long durationMs;
    private String message;
}
