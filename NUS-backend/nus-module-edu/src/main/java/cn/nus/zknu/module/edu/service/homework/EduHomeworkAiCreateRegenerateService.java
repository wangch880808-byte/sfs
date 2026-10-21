package cn.nus.zknu.module.edu.service.homework;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.ai.api.edu.dto.AiEduQuestionRewriteRespDTO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.capacity.api.indicator.dto.CapacityIndicatorRespDTO;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.capacity.api.indicator.CapacityIndicatorApi;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreatePreviewRespVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkAiCreateRegenerateReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkAiCreatePreviewDO;
import cn.nus.zknu.module.edu.dal.dataobject.questionbank.EduQuestionBankDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAiCreatePreviewMapper;
import cn.nus.zknu.module.edu.dal.mysql.questionbank.EduQuestionBankMapper;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkAiCreatePreviewSupport.AiCreatePreviewItem;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executor;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;

/** AI 单题后台重新生成与令牌校验。 */
@Service
@Slf4j
public class EduHomeworkAiCreateRegenerateService {

    @Resource
    private EduHomeworkAiCreatePreviewMapper eduHomeworkAiCreatePreviewMapper;
    @Resource
    private EduQuestionBankMapper eduQuestionBankMapper;
    @Resource
    private CapacityCourseApi capacityCourseApi;
    @Resource
    private CapacityIndicatorApi capacityIndicatorApi;
    @Resource
    private EduHomeworkAiCreatePreviewSupport previewSupport;
    @Resource(name = "eduAiReviewTaskExecutor")
    private Executor aiTaskExecutor;
    private static final int AI_REGENERATE_CAS_ATTEMPTS = 5;

    /**
     * 单题重新生成改为后台执行：先把题目标记为“重新生成中”并立即返回，前端轮询任务状态接口取结果。
     * 结果按生成令牌写回，预览已确认或题目又被重新发起时丢弃旧结果；失败时保留原来的题目。
     */
    public EduHomeworkAiCreatePreviewRespVO.Item regenerateAiCreateHomeworkQuestion(EduHomeworkAiCreateRegenerateReqVO reqVO) {
        for (int attempt = 0; attempt < AI_REGENERATE_CAS_ATTEMPTS; attempt++) {
            EduHomeworkAiCreatePreviewDO preview = previewSupport.validatePreviewSession(reqVO.getPreviewId(), false);
            List<AiCreatePreviewItem> items = previewSupport.parsePreviewItems(preview.getItemsJson());
            AiCreatePreviewItem item = findPreviewItem(items, reqVO.getPreviewItemId());
            if (eduQuestionBankMapper.selectById(item.getSourceQuestionId()) == null) {
                throw exception(HOMEWORK_AI_CREATE_PREVIEW_ITEM_INVALID);
            }
            if (previewSupport.isRegenerating(item)) {
                // 同一题已在生成中，重复点击直接返回当前状态
                return previewSupport.toPreviewRespItem(item);
            }
            String token = UUID.randomUUID().toString();
            item.setRegenerating(true);
            item.setRegenerateToken(token);
            item.setRegenerateStartedAt(System.currentTimeMillis());
            if (!previewSupport.compareAndSetPreviewItems(preview, items)) {
                continue;
            }
            Long previewDbId = preview.getId();
            String previewItemId = item.getPreviewItemId();
            try {
                aiTaskExecutor.execute(() -> processAiCreateRegenerate(previewDbId, previewItemId, token));
            } catch (org.springframework.core.task.TaskRejectedException ex) {
                finishAiCreateRegenerate(previewDbId, previewItemId, token, null, null);
                throw exception(HOMEWORK_AI_CREATE_REGENERATE_BUSY);
            }
            return previewSupport.toPreviewRespItem(item);
        }
        throw exception(HOMEWORK_AI_CREATE_REGENERATE_CONFLICT);
    }

    private void processAiCreateRegenerate(Long previewDbId, String previewItemId, String token) {
        AiEduQuestionRewriteRespDTO result;
        try {
            EduHomeworkAiCreatePreviewDO preview = eduHomeworkAiCreatePreviewMapper.selectById(previewDbId);
            AiCreatePreviewItem item = preview == null ? null : previewSupport.parsePreviewItems(preview.getItemsJson()).stream()
                    .filter(candidate -> Objects.equals(candidate.getPreviewItemId(), previewItemId))
                    .findFirst().orElse(null);
            if (item == null || !Objects.equals(item.getRegenerateToken(), token)) {
                return;
            }
            EduQuestionBankDO question = eduQuestionBankMapper.selectById(item.getSourceQuestionId());
            if (question == null) {
                throw new IllegalStateException("题库原题已不存在");
            }
            CapacityCourseRespDTO course = capacityCourseApi.getCourse(preview.getCourseId());
            CapacityIndicatorRespDTO indicator = capacityIndicatorApi.getIndicator(preview.getIndicatorSecondId());
            result = previewSupport.rewriteQuestion(course, indicator, preview.getHomeworkLevel(), question);
        } catch (Exception ex) {
            log.error("[processAiCreateRegenerate] failed, previewId={}, previewItemId={}", previewDbId, previewItemId, ex);
            result = null;
        }
        finishAiCreateRegenerate(previewDbId, previewItemId, token, result,
                result == null ? "AI 模型调用失败" : null);
    }

    /**
     * 写回重新生成结果并清除生成中标记。result 和 fallbackError 都为空表示没能开始（排队已满），只清除标记。
     */
    private void finishAiCreateRegenerate(Long previewDbId, String previewItemId, String token,
                                          AiEduQuestionRewriteRespDTO result, String fallbackError) {
        for (int attempt = 0; attempt < AI_REGENERATE_CAS_ATTEMPTS; attempt++) {
            EduHomeworkAiCreatePreviewDO preview = eduHomeworkAiCreatePreviewMapper.selectById(previewDbId);
            if (preview == null || Boolean.TRUE.equals(preview.getConfirmed())) {
                return;
            }
            List<AiCreatePreviewItem> items = previewSupport.parsePreviewItems(preview.getItemsJson());
            AiCreatePreviewItem item = items.stream()
                    .filter(candidate -> Objects.equals(candidate.getPreviewItemId(), previewItemId))
                    .findFirst().orElse(null);
            if (item == null || !Objects.equals(item.getRegenerateToken(), token)) {
                return;
            }
            item.setRegenerating(false);
            item.setRegenerateToken(null);
            item.setRegenerateStartedAt(null);
            if (result != null && Boolean.TRUE.equals(result.getSuccess())
                    && StrUtil.isNotBlank(result.getTitle()) && StrUtil.isNotBlank(result.getRequirement())) {
                previewSupport.applyRewriteResult(item, result);
            } else if (result != null || fallbackError != null) {
                String reason = result != null ? StrUtil.blankToDefault(result.getErrorMessage(), fallbackError)
                        : fallbackError;
                applyRegenerateFailure(item, reason);
            }
            if (previewSupport.compareAndSetPreviewItems(preview, items)) {
                return;
            }
        }
        log.warn("[finishAiCreateRegenerate] preview changed concurrently, result dropped, previewId={}, previewItemId={}",
                previewDbId, previewItemId);
    }

    /** 重新生成失败时保留原来的标题和要求，教师可以直接修改或再试一次。 */
    private void applyRegenerateFailure(AiCreatePreviewItem item, String reason) {
        String detail = StrUtil.blankToDefault(reason, "AI 模型调用失败");
        if (StrUtil.isBlank(item.getHomeworkName()) && StrUtil.isBlank(item.getRequirement())) {
            item.setSuccess(false);
            item.setErrorMessage(StrUtil.maxLength(detail, 1000));
            return;
        }
        item.setErrorMessage(StrUtil.maxLength("重新生成没有成功：" + StrUtil.removeSuffix(detail, "。")
                + "。原来的题目已保留，可以直接修改或再试一次。", 1000));
    }

    private AiCreatePreviewItem findPreviewItem(List<AiCreatePreviewItem> items, String previewItemId) {
        return items.stream()
                .filter(candidate -> Objects.equals(candidate.getPreviewItemId(), previewItemId))
                .findFirst()
                .orElseThrow(() -> exception(HOMEWORK_AI_CREATE_PREVIEW_ITEM_INVALID));
    }

}
