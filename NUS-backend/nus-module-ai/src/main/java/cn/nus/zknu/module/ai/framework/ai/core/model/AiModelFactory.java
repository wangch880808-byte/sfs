package cn.nus.zknu.module.ai.framework.ai.core.model;

import cn.nus.zknu.module.ai.enums.model.AiPlatformEnum;
import org.springframework.ai.chat.model.ChatModel;

/**
 * AI 模型工厂接口
 */
public interface AiModelFactory {

    ChatModel getOrCreateChatModel(AiPlatformEnum platform, String apiKey, String url);


}
