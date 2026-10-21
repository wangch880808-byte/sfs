package cn.nus.zknu.module.edu.service.homework;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.nus.zknu.module.ai.api.edu.AiEduReviewApi;
import cn.nus.zknu.module.ai.api.edu.dto.AiEduQuestionRewriteReqDTO;
import cn.nus.zknu.module.ai.api.edu.dto.AiEduQuestionRewriteRespDTO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.capacity.api.indicator.dto.CapacityIndicatorRespDTO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreatePreviewRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAiCreatePreviewDO;
import cn.nus.zknu.module.edu.dal.dataobject.questionbank.EduQuestionBankDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAiCreatePreviewMapper;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;

/** AI 预览会话、题目序列化与并发写回。 */
@Service
@Slf4j
public class EduHomeworkAiCreatePreviewSupport {

    @Resource
    private EduHomeworkAiCreatePreviewMapper eduHomeworkAiCreatePreviewMapper;
    @Resource
    private AiEduReviewApi aiEduReviewApi;
    @Resource
    private ConfigApi configApi;

    static final String AI_CREATE_SUCCEEDED = "SUCCEEDED";
    private static final String AI_REGENERATE_TIMEOUT_CONFIG_KEY = "edu.ai-create.regenerate-timeout-minutes";

    public AiEduQuestionRewriteRespDTO rewriteQuestion(CapacityCourseRespDTO course, CapacityIndicatorRespDTO indicator,
                                                        Integer homeworkLevel, EduQuestionBankDO question) {
        AiEduQuestionRewriteReqDTO req = new AiEduQuestionRewriteReqDTO();
        req.setCourseName(course != null ? course.getCourseName() : null);
        req.setIndicatorName(indicator != null ? indicator.getIndicatorName() : null);
        req.setHomeworkLevel(homeworkLevel);
        req.setTitle(question.getTitle());
        req.setRequirement(question.getRequirement());
        return aiEduReviewApi.rewriteQuestion(req);
    }

    public void applyRewriteResult(AiCreatePreviewItem item, AiEduQuestionRewriteRespDTO rewriteResp) {
        boolean success = rewriteResp != null && Boolean.TRUE.equals(rewriteResp.getSuccess())
                && StrUtil.isNotBlank(rewriteResp.getTitle()) && StrUtil.isNotBlank(rewriteResp.getRequirement());
        item.setSuccess(success);
        if (success) {
            item.setHomeworkName(rewriteResp.getTitle().trim());
            item.setRequirement(rewriteResp.getRequirement().trim());
            item.setErrorMessage(null);
            return;
        }
        item.setHomeworkName(null);
        item.setRequirement(null);
        item.setErrorMessage(rewriteResp != null ? rewriteResp.getErrorMessage() : "AI 模型调用失败");
    }

    public EduHomeworkAiCreatePreviewDO validatePreviewSession(String previewId, boolean allowConfirmed) {
        EduHomeworkAiCreatePreviewDO preview = eduHomeworkAiCreatePreviewMapper.selectByPreviewId(previewId);
        if (preview == null || !Objects.equals(preview.getTeacherId(), getLoginUserId())) {
            throw exception(HOMEWORK_AI_CREATE_PREVIEW_NOT_EXISTS);
        }
        if (preview.getExpireTime() != null && preview.getExpireTime().isBefore(LocalDateTime.now())) {
            throw exception(HOMEWORK_AI_CREATE_PREVIEW_EXPIRED);
        }
        if (!AI_CREATE_SUCCEEDED.equals(preview.getTaskStatus())) {
            throw exception(HOMEWORK_AI_CREATE_TASK_NOT_READY);
        }
        if (!allowConfirmed && Boolean.TRUE.equals(preview.getConfirmed())) {
            throw exception(HOMEWORK_AI_CREATE_PREVIEW_CONFIRMED);
        }
        return preview;
    }

    public List<AiCreatePreviewItem> parsePreviewItems(String itemsJson) {
        return JSONUtil.toList(JSONUtil.parseArray(StrUtil.blankToDefault(itemsJson, "[]")), AiCreatePreviewItem.class);
    }

    public EduHomeworkAiCreatePreviewRespVO buildPreviewResp(String previewId, Integer classCount,
                                                              List<AiCreatePreviewItem> items) {
        EduHomeworkAiCreatePreviewRespVO respVO = new EduHomeworkAiCreatePreviewRespVO();
        respVO.setPreviewId(previewId);
        respVO.setQuestionCount(items.size());
        respVO.setClassCount(classCount);
        respVO.setItems(items.stream().map(this::toPreviewRespItem).toList());
        return respVO;
    }

    public EduHomeworkAiCreatePreviewRespVO.Item toPreviewRespItem(AiCreatePreviewItem item) {
        EduHomeworkAiCreatePreviewRespVO.Item respItem = new EduHomeworkAiCreatePreviewRespVO.Item();
        respItem.setPreviewItemId(item.getPreviewItemId());
        respItem.setSourceQuestionId(item.getSourceQuestionId());
        respItem.setSourceTitle(item.getSourceTitle());
        respItem.setSourceRequirement(item.getSourceRequirement());
        respItem.setHomeworkName(item.getHomeworkName());
        respItem.setRequirement(item.getRequirement());
        respItem.setSuccess(item.getSuccess());
        respItem.setErrorMessage(item.getErrorMessage());
        respItem.setRegenerating(isRegenerating(item));
        if (Boolean.TRUE.equals(item.getRegenerating()) && !respItem.getRegenerating()) {
            respItem.setErrorMessage("重新生成超时：没有在规定时间内拿到 AI 结果。原来的题目已保留，可以再试一次。");
        }
        return respItem;
    }

    /** 生成中且未超过超时时间；超时的标记视为已失效，允许重新发起。 */
    public boolean isRegenerating(AiCreatePreviewItem item) {
        if (!Boolean.TRUE.equals(item.getRegenerating()) || item.getRegenerateStartedAt() == null) {
            return false;
        }
        return System.currentTimeMillis() - item.getRegenerateStartedAt() < regenerateTimeoutMillis();
    }

    private long regenerateTimeoutMillis() {
        long minutes = 10;
        try {
            String value = configApi.getConfigValueByKey(AI_REGENERATE_TIMEOUT_CONFIG_KEY);
            if (StrUtil.isNotBlank(value)) {
                long parsed = Long.parseLong(value.trim());
                if (parsed >= 1 && parsed <= 120) {
                    minutes = parsed;
                } else {
                    log.warn("重新生成超时配置超出 1–120 分钟，使用 10 分钟：{}", value);
                }
            }
        } catch (Exception ex) {
            log.warn("读取重新生成超时配置失败，使用 10 分钟");
        }
        return minutes * 60_000L;
    }

    /** 以整段题目 JSON 为条件更新，避免并发的重新生成互相覆盖；已确认的预览不再修改。 */
    public boolean compareAndSetPreviewItems(EduHomeworkAiCreatePreviewDO preview, List<AiCreatePreviewItem> items) {
        int updated = eduHomeworkAiCreatePreviewMapper.update(new EduHomeworkAiCreatePreviewDO(),
                new LambdaUpdateWrapper<EduHomeworkAiCreatePreviewDO>()
                        .set(EduHomeworkAiCreatePreviewDO::getItemsJson, JSONUtil.toJsonStr(items))
                        .eq(EduHomeworkAiCreatePreviewDO::getId, preview.getId())
                        .eq(EduHomeworkAiCreatePreviewDO::getItemsJson, preview.getItemsJson())
                        .eq(EduHomeworkAiCreatePreviewDO::getConfirmed, false));
        return updated > 0;
    }

    @Data
    public static class AiCreatePreviewItem {
        private String previewItemId;
        private Long sourceQuestionId;
        private String sourceTitle;
        private String sourceRequirement;
        private String homeworkName;
        private String requirement;
        private Boolean success;
        private String errorMessage;
        /** 是否正在后台重新生成 */
        private Boolean regenerating;
        /** 本次重新生成的令牌，结果按令牌写回 */
        private String regenerateToken;
        /** 本次重新生成的开始时间（毫秒时间戳） */
        private Long regenerateStartedAt;
    }

}
