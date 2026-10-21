package cn.nus.zknu.module.ai.framework.ai.core.model;

import cn.hutool.core.lang.Singleton;
import cn.hutool.core.lang.func.Func0;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.nus.zknu.module.ai.enums.model.AiPlatformEnum;
import cn.nus.zknu.module.ai.framework.ai.config.NusAiProperties;
import io.micrometer.observation.ObservationRegistry;
import org.springaicommunity.moonshot.MoonshotChatModel;
import org.springaicommunity.moonshot.MoonshotChatOptions;
import org.springaicommunity.moonshot.api.MoonshotApi;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.minimax.MiniMaxChatOptions;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.common.OpenAiApiConstants;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.LongSupplier;


/**
 * AI 模型工厂实现
 */
public class AiModelFactoryImpl implements AiModelFactory {

    private static final String DEFAULT_OLLAMA_URL = "http://127.0.0.1:11434";

    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final RetryTemplate retryTemplate;

    public AiModelFactoryImpl(NusAiProperties properties) {
        this.connectTimeout = properties.getEdu().getConnectTimeout();
        this.readTimeout = properties.getEdu().getCallTimeout();
        this.retryTemplate = createRetryTemplate(properties.getEdu(), System::nanoTime);
    }

    /**
     * 重试预算与教育 AI 的硬超时对齐：每次尝试最长 callTimeout，总时长不超过 hardTimeout，
     * 只对限流、服务端错误、超时和连接失败重试。
     */
    static RetryTemplate createRetryTemplate(NusAiProperties.Edu edu, LongSupplier nanoClock) {
        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(new AiRetryPolicy(edu.getMaxAttempts(), edu.getHardTimeout(), edu.getCallTimeout(),
                edu.getRetryInitialInterval(), edu.getRetryMultiplier(), edu.getRetryMaxInterval(), nanoClock));
        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(Math.max(1L, edu.getRetryInitialInterval().toMillis()));
        backOff.setMultiplier(Math.max(1.0, edu.getRetryMultiplier()));
        backOff.setMaxInterval(Math.max(backOff.getInitialInterval(), edu.getRetryMaxInterval().toMillis()));
        template.setBackOffPolicy(backOff);
        return template;
    }

    private static String buildClientCacheKey(Class<?> clazz, Object... params) {
        if (ArrayUtil.isEmpty(params)) {
            return clazz.getName();
        }
        return StrUtil.format("{}#{}", clazz.getName(), ArrayUtil.join(params, "_"));
    }

    private static ToolCallingManager getToolCallingManager() {
        return SpringUtil.getBean(ToolCallingManager.class);
    }

    private static ObservationRegistry getObservationRegistry() {
        return SpringUtil.getBean(ObservationRegistry.class);
    }

    private OpenAiChatModel buildOpenAiChatModel(String apiKey, String url) {
        String baseUrl = StrUtil.blankToDefault(url, OpenAiApiConstants.DEFAULT_BASE_URL);
        OpenAiApi openAiApi = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey)
                .restClientBuilder(restClientBuilder()).webClientBuilder(webClientBuilder()).build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .retryTemplate(retryTemplate)
                .toolCallingManager(getToolCallingManager())
                .build();
    }

    private AnthropicChatModel buildAnthropicChatModel(String apiKey, String url) {
        AnthropicApi.Builder builder = AnthropicApi.builder().apiKey(apiKey)
                .restClientBuilder(restClientBuilder()).webClientBuilder(webClientBuilder());
        if (StrUtil.isNotBlank(url)) {
            builder.baseUrl(url);
        }
        return AnthropicChatModel.builder()
                .anthropicApi(builder.build())
                .retryTemplate(retryTemplate)
                .toolCallingManager(getToolCallingManager())
                .build();
    }

    private DeepSeekChatModel buildDeepSeekChatModel(String apiKey, String url) {
        DeepSeekApi.Builder builder = DeepSeekApi.builder().apiKey(apiKey)
                .restClientBuilder(restClientBuilder()).webClientBuilder(webClientBuilder());
        if (StrUtil.isNotBlank(url)) {
            builder.baseUrl(url);
        }
        return DeepSeekChatModel.builder()
                .deepSeekApi(builder.build())
                .retryTemplate(retryTemplate)
                .defaultOptions(DeepSeekChatOptions.builder()
                        .model(DeepSeekApi.DEFAULT_CHAT_MODEL)
                        .temperature(0.7)
                        .build())
                .toolCallingManager(getToolCallingManager())
                .build();
    }

    private OllamaChatModel buildOllamaChatModel(String url) {
        String baseUrl = StrUtil.blankToDefault(url, DEFAULT_OLLAMA_URL);
        OllamaApi ollamaApi = OllamaApi.builder().baseUrl(baseUrl)
                .restClientBuilder(restClientBuilder()).webClientBuilder(webClientBuilder()).build();
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .retryTemplate(retryTemplate)
                .toolCallingManager(getToolCallingManager())
                .build();
    }

    private ZhiPuAiChatModel buildZhiPuChatModel(String apiKey, String url) {
        ZhiPuAiApi.Builder builder = ZhiPuAiApi.builder().apiKey(apiKey)
                .restClientBuilder(restClientBuilder()).webClientBuilder(webClientBuilder());
        if (StrUtil.isNotBlank(url)) {
            builder.baseUrl(url);
        }
        ZhiPuAiChatOptions options = ZhiPuAiChatOptions.builder()
                .model(ZhiPuAiApi.DEFAULT_CHAT_MODEL)
                .temperature(0.7)
                .build();
        return new ZhiPuAiChatModel(builder.build(), options, getToolCallingManager(),
                retryTemplate, getObservationRegistry());
    }

    private MiniMaxChatModel buildMiniMaxChatModel(String apiKey, String url) {
        MiniMaxApi miniMaxApi = new MiniMaxApi(StrUtil.blankToDefault(url,
                org.springframework.ai.minimax.api.MiniMaxApiConstants.DEFAULT_BASE_URL), apiKey, restClientBuilder());
        MiniMaxChatOptions options = MiniMaxChatOptions.builder()
                .model(MiniMaxApi.DEFAULT_CHAT_MODEL)
                .temperature(0.7)
                .build();
        return new MiniMaxChatModel(miniMaxApi, options, getToolCallingManager(), retryTemplate);
    }

    private MoonshotChatModel buildMoonshotChatModel(String apiKey, String url) {
        MoonshotApi.Builder builder = MoonshotApi.builder().apiKey(apiKey)
                .restClientBuilder(restClientBuilder()).webClientBuilder(webClientBuilder());
        if (StrUtil.isNotBlank(url)) {
            builder.baseUrl(url);
        }
        MoonshotChatOptions options = MoonshotChatOptions.builder()
                .model(MoonshotApi.DEFAULT_CHAT_MODEL)
                .build();
        return MoonshotChatModel.builder()
                .moonshotApi(builder.build())
                .retryTemplate(retryTemplate)
                .defaultOptions(options)
                .toolCallingManager(getToolCallingManager())
                .build();
    }

    private RestClient.Builder restClientBuilder() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory);
    }

    private WebClient.Builder webClientBuilder() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpConnector connector = new JdkClientHttpConnector(client);
        connector.setReadTimeout(readTimeout);
        return WebClient.builder().clientConnector(connector);
    }

    @Override
    public ChatModel getOrCreateChatModel(AiPlatformEnum platform, String apiKey, String url) {
        String cacheKey = buildClientCacheKey(ChatModel.class, platform,
                DigestUtil.sha256Hex(StrUtil.nullToEmpty(apiKey)), url);
        return Singleton.get(cacheKey, (Func0<ChatModel>) () -> switch (platform) {
            case OPENAI -> buildOpenAiChatModel(apiKey, url);
            case ANTHROPIC -> buildAnthropicChatModel(apiKey, url);
            case DEEP_SEEK -> buildDeepSeekChatModel(apiKey, url);
            case OLLAMA -> buildOllamaChatModel(url);
            case ZHI_PU -> buildZhiPuChatModel(apiKey, url);
            case MINI_MAX -> buildMiniMaxChatModel(apiKey, url);
            case MOONSHOT -> buildMoonshotChatModel(apiKey, url);
        });
    }

}
