package cn.nus.zknu.module.ai.enums.model;

import java.util.Arrays;

/**
 * 调用日志里的业务码：AiBusinessCodeEnum 的业务，加上不走业务绑定的 AI 对话和模型连接测试。
 */
public final class AiCallBusiness {

    public static final String AI_CHAT = "AI_CHAT";
    public static final String MODEL_TEST = "MODEL_TEST";
    public static final String UNKNOWN = "UNKNOWN";

    private AiCallBusiness() {
    }

    public static String nameOf(String code) {
        if (AI_CHAT.equals(code)) {
            return "AI 对话";
        }
        if (MODEL_TEST.equals(code)) {
            return "模型连接测试";
        }
        return Arrays.stream(AiBusinessCodeEnum.values()).filter(item -> item.getCode().equals(code))
                .map(AiBusinessCodeEnum::getName).findFirst().orElse("其他");
    }

}
