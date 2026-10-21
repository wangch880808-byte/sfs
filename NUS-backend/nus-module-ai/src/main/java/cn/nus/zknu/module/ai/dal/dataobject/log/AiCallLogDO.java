package cn.nus.zknu.module.ai.dal.dataobject.log;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 模型调用日志：每次模型调用一行（模型内部的有限重试算同一次调用）。
 * 不保存提示词和回复内容，错误只保存脱敏后的摘要。
 */
@TableName("ai_call_log")
@KeySequence("ai_call_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class AiCallLogDO extends BaseDO {

    @TableId
    private Long id;
    /** 业务码：AiBusinessCodeEnum 的业务，或 AI_CHAT、MODEL_TEST */
    private String businessCode;
    private Long modelId;
    /** 模型标识，例如 deepseek-chat */
    private String modelName;
    private String platform;
    /** 业务对象编号，例如提交记录编号 */
    private String bizId;
    private Long operatorId;
    /** 是否后台任务发起 */
    private Boolean background;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long durationMs;
    private Boolean success;
    /** 错误分类，见 AiErrorClassifier.TYPE_* */
    private String errorType;
    /** 脱敏后的错误摘要 */
    private String errorMessage;

}
