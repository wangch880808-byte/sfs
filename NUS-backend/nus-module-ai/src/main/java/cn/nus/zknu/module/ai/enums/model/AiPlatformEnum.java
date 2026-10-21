package cn.nus.zknu.module.ai.enums.model;

import cn.nus.zknu.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * AI 模型平台
 */
@Getter
@AllArgsConstructor
public enum AiPlatformEnum implements ArrayValuable<String> {

    OPENAI("OpenAI", "OpenAI"),
    ANTHROPIC("Anthropic", "Anthropic"),
    DEEP_SEEK("DeepSeek", "DeepSeek"),
    OLLAMA("Ollama", "Ollama"),
    ZHI_PU("ZhiPu", "智谱"),
    MINI_MAX("MiniMax", "MiniMax"),
    MOONSHOT("Moonshot", "月之暗面");

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(AiPlatformEnum::getPlatform)
            .toArray(String[]::new);

    private final String platform;
    private final String name;

    public static AiPlatformEnum validatePlatform(String platform) {
        for (AiPlatformEnum platformEnum : values()) {
            if (platformEnum.getPlatform().equals(platform)) {
                return platformEnum;
            }
        }
        throw new IllegalArgumentException("非法平台： " + platform);
    }

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
