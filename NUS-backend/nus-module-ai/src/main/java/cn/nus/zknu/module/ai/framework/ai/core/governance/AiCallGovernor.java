package cn.nus.zknu.module.ai.framework.ai.core.governance;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.module.ai.dal.dataobject.log.AiCallLogDO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiModelDO;
import cn.nus.zknu.module.ai.enums.ErrorCodeConstants;
import cn.nus.zknu.module.ai.enums.model.AiCallBusiness;
import cn.nus.zknu.module.ai.service.log.AiCallLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 模型调用的统一治理入口：调用前检查熔断和额度，调用后写调用日志、更新熔断状态。
 * AiModelService#getChatModel 返回的模型都经过这里，业务代码不需要各自处理。
 */
@Component
@Slf4j
public class AiCallGovernor {

    @Resource
    private AiCircuitBreaker circuitBreaker;
    @Resource
    private AiQuotaChecker quotaChecker;
    @Resource
    private AiCallLogService callLogService;

    public ChatModel govern(ChatModel delegate, AiModelDO model) {
        if (delegate instanceof AiGovernedChatModel governed) {
            return governed;
        }
        return new AiGovernedChatModel(delegate, model, this);
    }

    /** 当前线程的调用上下文；调用方没有设置时记为“其他”业务，发起人取当前登录用户。 */
    AiCallContext resolveContext() {
        AiCallContext context = AiCallContext.current();
        if (context != null) {
            if (context.operatorId() != null) {
                return context;
            }
            return new AiCallContext(context.businessCode(), context.bizId(), currentUserId(),
                    context.background(), context.governed());
        }
        return new AiCallContext(AiCallBusiness.UNKNOWN, null, currentUserId(),
                AiCallContext.isBackgroundThread(), true);
    }

    /** 调用前检查；被拒绝时写一条失败日志并抛出业务异常。 */
    void beforeCall(AiModelDO model, AiCallContext context) {
        if (!context.governed()) {
            return;
        }
        try {
            circuitBreaker.checkAllowed(breakerKey(model));
            quotaChecker.check(context.operatorId(), context.background());
        } catch (ServiceException ex) {
            String type = Objects.equals(ex.getCode(), ErrorCodeConstants.AI_QUOTA_EXCEEDED.getCode())
                    ? AiErrorClassifier.TYPE_QUOTA_EXCEEDED : AiErrorClassifier.TYPE_CIRCUIT_OPEN;
            writeLog(model, context, LocalDateTime.now(), 0L, null, false, type, ex.getMessage());
            throw ex;
        }
    }

    void onSuccess(AiModelDO model, AiCallContext context, LocalDateTime startTime, long durationMs,
                   ChatResponse lastResponse) {
        try {
            circuitBreaker.recordSuccess(breakerKey(model));
        } catch (RuntimeException ex) {
            log.warn("[onSuccess][更新熔断状态失败]", ex);
        }
        writeLog(model, context, startTime, durationMs, usageOf(lastResponse), true, null, null);
    }

    void onFailure(AiModelDO model, AiCallContext context, LocalDateTime startTime, long durationMs,
                   Throwable error, ChatResponse lastResponse) {
        try {
            if (AiErrorClassifier.isServiceFailure(error)) {
                circuitBreaker.recordFailure(breakerKey(model));
            }
        } catch (RuntimeException ex) {
            log.warn("[onFailure][更新熔断状态失败]", ex);
        }
        writeLog(model, context, startTime, durationMs, usageOf(lastResponse), false,
                AiErrorClassifier.classify(error), AiErrorClassifier.summarize(error));
    }

    void onCancelled(AiModelDO model, AiCallContext context, LocalDateTime startTime, long durationMs,
                     ChatResponse lastResponse) {
        writeLog(model, context, startTime, durationMs, usageOf(lastResponse), false,
                AiErrorClassifier.TYPE_CANCELLED, "调用在完成前被取消：页面中止或等待超时");
    }

    private void writeLog(AiModelDO model, AiCallContext context, LocalDateTime startTime, long durationMs,
                          Usage usage, boolean success, String errorType, String errorMessage) {
        try {
            AiCallLogDO callLog = new AiCallLogDO();
            callLog.setBusinessCode(StrUtil.maxLength(StrUtil.blankToDefault(context.businessCode(),
                    AiCallBusiness.UNKNOWN), 61));
            callLog.setModelId(model.getId());
            callLog.setModelName(StrUtil.maxLength(model.getModel(), 125));
            callLog.setPlatform(model.getPlatform());
            callLog.setBizId(StrUtil.maxLength(context.bizId(), 61));
            callLog.setOperatorId(context.operatorId());
            callLog.setBackground(context.background());
            if (usage != null) {
                callLog.setPromptTokens(usage.getPromptTokens());
                callLog.setCompletionTokens(usage.getCompletionTokens());
                callLog.setTotalTokens(usage.getTotalTokens());
            }
            callLog.setDurationMs(Math.max(0L, durationMs));
            callLog.setSuccess(success);
            callLog.setErrorType(errorType);
            callLog.setErrorMessage(errorMessage);
            callLog.setCreateTime(startTime);
            callLog.setUpdateTime(startTime);
            // 日志在独立线程写入，那里的登录信息不可靠，创建人按发起人显式写入
            String actor = context.operatorId() != null ? String.valueOf(context.operatorId()) : "";
            callLog.setCreator(actor);
            callLog.setUpdater(actor);
            callLogService.record(callLog);
        } catch (RuntimeException ex) {
            log.warn("[writeLog][记录 AI 调用日志失败]", ex);
        }
    }

    /** 服务商没有返回用量时为空；Spring AI 用 EmptyUsage 表示“没有用量”。 */
    static Usage usageOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null || usage instanceof EmptyUsage) {
            return null;
        }
        Integer prompt = usage.getPromptTokens();
        Integer completion = usage.getCompletionTokens();
        Integer total = usage.getTotalTokens();
        boolean empty = (prompt == null || prompt == 0) && (completion == null || completion == 0)
                && (total == null || total == 0);
        return empty ? null : usage;
    }

    static String breakerKey(AiModelDO model) {
        return "model:" + model.getId();
    }

    private static Long currentUserId() {
        try {
            return getLoginUserId();
        } catch (RuntimeException ex) {
            return null;
        }
    }

}
