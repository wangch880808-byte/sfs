package cn.nus.zknu.module.ai.util;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.module.ai.enums.model.AiPlatformEnum;
import org.springaicommunity.moonshot.MoonshotChatOptions;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.minimax.MiniMaxChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring AI 工具类
 */
public class AiUtils {

    public static final String TOOL_CONTEXT_LOGIN_USER = "LOGIN_USER";

    public static ChatOptions buildChatOptions(AiPlatformEnum platform, String model, Double temperature, Integer maxTokens) {
        return buildChatOptions(platform, model, temperature, maxTokens, null, null);
    }

    public static ChatOptions buildChatOptions(AiPlatformEnum platform, String model, Double temperature,
                                               Integer maxTokens, List<ToolCallback> toolCallbacks,
                                               Map<String, Object> toolContext) {
        toolCallbacks = ObjUtil.defaultIfNull(toolCallbacks, Collections.emptyList());
        toolContext = ObjUtil.defaultIfNull(toolContext, Collections.emptyMap());
        switch (platform) {
            case DEEP_SEEK:
                return DeepSeekChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .toolCallbacks(toolCallbacks)
                        .toolContext(toolContext)
                        .build();
            case ZHI_PU:
                return ZhiPuAiChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .toolCallbacks(toolCallbacks)
                        .toolContext(toolContext)
                        .build();
            case MINI_MAX:
                return MiniMaxChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .toolCallbacks(toolCallbacks)
                        .toolContext(toolContext)
                        .build();
            case MOONSHOT:
                return MoonshotChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .toolCallbacks(toolCallbacks)
                        .toolContext(toolContext)
                        .build();
            case OPENAI:
                OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                        .model(model)
                        .toolCallbacks(toolCallbacks)
                        .toolContext(toolContext);
                if (isOpenAiGpt5Model(model)) {
                    if (maxTokens != null) {
                        builder.maxTokens(maxTokens);
                    }
                    builder.reasoningEffort("none");
                    builder.verbosity("high");
                } else {
                    builder.temperature(temperature).maxTokens(maxTokens);
                }
                return builder.build();
            case ANTHROPIC:
                return AnthropicChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .toolCallbacks(toolCallbacks)
                        .toolContext(toolContext)
                        .build();
            case OLLAMA:
                return OllamaChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .numPredict(maxTokens)
                        .toolCallbacks(toolCallbacks)
                        .toolContext(toolContext)
                        .build();
            default:
                throw new IllegalArgumentException(StrUtil.format("未知平台({})", platform));
        }
    }

    private static boolean isOpenAiGpt5Model(String model) {
        return StrUtil.startWithIgnoreCase(StrUtil.blankToDefault(model, ""), "gpt-5");
    }

    public static Message buildMessage(String type, String content) {
        if (MessageType.USER.getValue().equals(type)) {
            return new UserMessage(content);
        }
        if (MessageType.ASSISTANT.getValue().equals(type)) {
            return new AssistantMessage(content);
        }
        if (MessageType.SYSTEM.getValue().equals(type)) {
            return new SystemMessage(content);
        }
        if (MessageType.TOOL.getValue().equals(type)) {
            throw new UnsupportedOperationException("暂不支持 tool 消息：" + content);
        }
        throw new IllegalArgumentException(StrUtil.format("未知消息类型({})", type));
    }

    public static Map<String, Object> buildCommonToolContext() {
        Map<String, Object> context = new HashMap<>();
        context.put(TOOL_CONTEXT_LOGIN_USER, SecurityFrameworkUtils.getLoginUser());
        return context;
    }

    @SuppressWarnings("ConstantValue")
    public static String getChatResponseContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    @SuppressWarnings("ConstantValue")
    public static String getChatResponseReasoningContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        if (response.getResult().getOutput() instanceof DeepSeekAssistantMessage) {
            return ((DeepSeekAssistantMessage) response.getResult().getOutput()).getReasoningContent();
        }
        return null;
    }

}
