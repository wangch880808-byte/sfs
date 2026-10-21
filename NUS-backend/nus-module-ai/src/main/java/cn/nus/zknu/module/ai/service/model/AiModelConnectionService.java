package cn.nus.zknu.module.ai.service.model;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.ai.controller.admin.model.vo.model.AiModelTestRespVO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiModelDO;
import cn.nus.zknu.module.ai.enums.model.AiCallBusiness;
import cn.nus.zknu.module.ai.enums.model.AiModelTypeEnum;
import cn.nus.zknu.module.ai.framework.ai.core.governance.AiCallContext;
import cn.nus.zknu.module.ai.enums.model.AiPlatformEnum;
import cn.nus.zknu.module.ai.util.AiUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

/** Tests the saved model from the server without storing conversations or generated files. */
@Service
@RequiredArgsConstructor
public class AiModelConnectionService {
    private final AiModelService modelService;
    private final Semaphore permits = new Semaphore(2);

    public AiModelTestRespVO testConnection(Long id) {
        AiModelDO model = modelService.validateModel(id);
        long startedAt = System.nanoTime();
        try {
            Boolean valid = Mono.fromCallable(() -> {
                if (!permits.tryAcquire()) {
                    throw new IllegalStateException("MODEL_TEST_BUSY");
                }
                try {
                    return probe(model);
                } finally {
                    // Retain the permit until the provider call actually stops, even on timeout.
                    permits.release();
                }
            }).subscribeOn(Schedulers.boundedElastic()).block(Duration.ofSeconds(60));
            return result(Boolean.TRUE.equals(valid), startedAt, Boolean.TRUE.equals(valid)
                    ? "连接成功，模型已返回有效响应" : "模型返回为空，请检查模型标识和参数配置");
        } catch (RuntimeException ex) {
            return result(false, startedAt, failureMessage(ex));
        }
    }

    boolean probe(AiModelDO model) {
        if (AiModelTypeEnum.CHAT.getType().equals(model.getType())) {
            var options = AiUtils.buildChatOptions(AiPlatformEnum.validatePlatform(model.getPlatform()),
                    model.getModel(), model.getTemperature(),
                    model.getMaxTokens() == null ? 512 : Math.min(model.getMaxTokens(), 512));
            var chatModel = modelService.getChatModel(model.getId());
            // 连接测试不受额度和熔断限制，只记调用日志；测试成功会让熔断恢复
            var response = AiCallContext.call(
                    // 管理员在页面上发起，按页面调用记录（探测在线程池里执行，不能按线程判断）
                    new AiCallContext(AiCallBusiness.MODEL_TEST, String.valueOf(model.getId()), null, false, false),
                    () -> chatModel.call(new Prompt("This is a connection test. Reply only with OK.", options)));
            return StrUtil.isNotBlank(AiUtils.getChatResponseContent(response))
                    || StrUtil.isNotBlank(AiUtils.getChatResponseReasoningContent(response));
        }
        throw new UnsupportedOperationException();
    }

    private AiModelTestRespVO result(boolean success, long start, String message) {
        return new AiModelTestRespVO(success, (System.nanoTime() - start) / 1_000_000L, message);
    }

    // Do not return raw provider bodies, URLs or credentials in error responses.
    static String failureMessage(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof java.net.SocketTimeoutException
                    || cause instanceof java.net.http.HttpTimeoutException) {
                return "连接超时，请检查服务器出网、API 地址或稍后重试";
            }
            if (cause instanceof UnsupportedOperationException) {
                return "当前平台或模型类型暂不支持连通性测试";
            }
            if ("MODEL_TEST_BUSY".equals(cause.getMessage())) {
                return "有其他模型正在测试，请稍后重试";
            }
            int status = cause instanceof RestClientResponseException http ? http.getStatusCode().value()
                    : cause instanceof WebClientResponseException web ? web.getStatusCode().value() : 0;
            if (status == 401 || status == 403) {
                return "认证或权限失败，请检查 API 密钥和模型使用权限";
            }
            if (status == 429) {
                return "服务商限流或额度不足，请检查账户后重试";
            }
            if (status == 400 || status == 404 || status == 422) {
                return "请求被拒绝，请检查 API 地址、模型标识及参数";
            }
        }
        return "连接失败，请检查服务器出网、API 地址、密钥及模型配置";
    }
}
