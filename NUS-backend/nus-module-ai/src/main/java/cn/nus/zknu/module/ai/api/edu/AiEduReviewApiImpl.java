package cn.nus.zknu.module.ai.api.edu;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.module.ai.api.edu.dto.*;
import cn.nus.zknu.module.ai.api.edu.support.AiEduRuntimeGuard;
import cn.nus.zknu.module.ai.framework.ai.config.NusAiProperties;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiApiKeyDO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiModelDO;
import cn.nus.zknu.module.ai.enums.model.AiBusinessCodeEnum;
import cn.nus.zknu.module.ai.enums.model.AiPlatformEnum;
import cn.nus.zknu.module.ai.enums.prompt.AiPromptCodeEnum;
import cn.nus.zknu.module.ai.service.model.AiApiKeyService;
import cn.nus.zknu.module.ai.service.model.AiBusinessModelBindingService;
import cn.nus.zknu.module.ai.service.model.AiModelService;
import cn.nus.zknu.module.ai.service.prompt.AiPromptTemplateService;
import cn.nus.zknu.module.ai.util.AiUtils;
import cn.nus.zknu.module.ai.util.FileTypeUtils;
import cn.nus.zknu.module.ai.util.ImageOrientationUtil;
import cn.nus.zknu.module.infra.api.file.FileApi;
import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * AI教育评阅 API 实现
 */
@Service
@Slf4j
public class AiEduReviewApiImpl implements AiEduReviewApi {

    private static final int MAX_REVIEW_IMAGE_COUNT = 5;
    private static final long MAX_REVIEW_IMAGE_BYTES = 10L * 1024 * 1024;

    @Resource
    private AiModelService modalService;
    @Resource
    private AiApiKeyService apiKeyService;
    @Resource
    private FileApi fileApi;
    @Resource
    private AiBusinessModelBindingService bindingService;
    @Resource
    private AiEduRuntimeGuard runtimeGuard;
    @Resource
    private NusAiProperties aiProperties;
    @Resource
    private AiPromptTemplateService promptTemplateService;

    @Override
    public AiEduReviewRespDTO correctHomework(AiEduReviewReqDTO req) {
        runtimeGuard.requireExternalStudentDataEnabled();
        AiModelDO model = bindingService.getRequiredModel(AiBusinessCodeEnum.HOMEWORK_REVIEW.getCode());
        ChatModel chatModel = modalService.getChatModel(model.getId());
        ChatOptions options = buildOptions(model);

        String systemPrompt = promptTemplateService.getActiveContent(AiPromptCodeEnum.HOMEWORK_REVIEW)
                + (StrUtil.isBlank(req.getCalibrationHint()) ? "" : "\n【教师评分偏好】\n" + req.getCalibrationHint());

        String userPrompt = String.format(
                "【提交模式】%s\n【评分维度】%s\n【任务要求】%s\n【及格分数线】%d分\n【评分规则】%s\n【参考资料】%s\n[学生作答开始]\n【附件列表】%s\n【转写文本】%s\n【结构化证据】%s\n【学生补充说明】%s\n[学生作答结束]",
                StrUtil.blankToDefault(req.getSubmissionMode(), "text"),
                StrUtil.blankToDefault(req.getIndicatorName(), "综合评价"),
                StrUtil.blankToDefault(req.getRequirement(), "无"),
                req.getPassScore() != null ? req.getPassScore() : 60,
                StrUtil.blankToDefault(req.getRubricJson(), "[]"),
                StrUtil.blankToDefault(req.getReferenceJson(), "{}"),
                limitStudentText(req.getAttachmentUrls(), "[]"),
                limitStudentText(req.getTranscript(), "无"),
                limitStudentText(req.getEvidenceJson(), "{}"),
                limitStudentText(req.getContent(), "（空）")
        );
        UserMessage userMessage = buildReviewUserMessage(req, userPrompt);
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), userMessage), options);
        ChatResponse response = runtimeGuard.execute(AiBusinessCodeEnum.HOMEWORK_REVIEW.getCode(), model,
                req.getBusinessObjectId(), () -> chatModel.call(prompt));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("AI 模型未返回有效的初评结果");
        }
        String text = response.getResult().getOutput().getText();
        AiEduReviewRespDTO result = parseReviewResponse(text);
        result.setModelId(model.getId());
        result.setModelName(model.getModel());
        return result;
    }

    private String limitStudentText(String value, String fallback) {
        String text = StrUtil.blankToDefault(value, fallback)
                .replace("[学生作答开始]", "[学生作答开始-原文]")
                .replace("[学生作答结束]", "[学生作答结束-原文]");
        int maxLength = Math.max(1, aiProperties.getEdu().getMaxStudentTextLength());
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "[已截断]";
    }

    @Override
    public String generateComment(AiEduGenerateReqDTO req) {
        runtimeGuard.requireExternalStudentDataEnabled();
        String businessCode = resolveGenerateBusinessCode(req.getType());
        AiModelDO model = bindingService.getRequiredModel(businessCode);
        ChatModel chatModel = modalService.getChatModel(model.getId());
        ChatOptions options = buildOptions(model);

        String systemPrompt = buildGenerateSystemPrompt(req.getType());
        String userContent = buildGenerateUserContent(req.getType(), req.getStudentData());
        log.info("[generateComment] start type={}, modelId={}, platform={}, model={}, maxTokens={}, systemPromptLength={}, userContentLength={}",
                req.getType(), model.getId(), model.getPlatform(), model.getModel(), model.getMaxTokens(),
                systemPrompt.length(), userContent.length());

        Prompt prompt = buildGeneratePrompt(systemPrompt, userContent, options);
        String content;
        content = runtimeGuard.execute(businessCode, model, req.getBusinessObjectId(), () -> {
            if (shouldUseStreamingAggregation(model, chatModel)) {
                return callWithStreamingAggregation((StreamingChatModel) chatModel, prompt, req.getType(), model);
            }
            ChatResponse response = chatModel.call(prompt);
            String responseContent = response.getResult() != null && response.getResult().getOutput() != null
                    ? response.getResult().getOutput().getText() : "";
            if (StrUtil.isBlank(responseContent)) {
                log.warn("[generateComment] AI 返回空内容, type={}, modelId={}, platform={}, model={}, response={}",
                        req.getType(), model.getId(), model.getPlatform(), model.getModel(), response);
            }
            return responseContent;
        });
        if (StrUtil.isBlank(content)) {
            return "## 研判结果\n当前模型未返回有效内容，请检查默认模型可用性或稍后重试。";
        }
        log.info("[generateComment] finish type={}, modelId={}, contentLength={}", req.getType(), model.getId(), content.trim().length());
        return content.trim();
    }

    @Override
    public AiEduQuestionRewriteRespDTO rewriteQuestion(AiEduQuestionRewriteReqDTO req) {
        AiModelDO model = bindingService.getRequiredModel(AiBusinessCodeEnum.HOMEWORK_REWRITE.getCode());
        ChatModel chatModel = modalService.getChatModel(model.getId());
        ChatOptions options = buildOptions(model);

        String systemPrompt = promptTemplateService.getActiveContent(AiPromptCodeEnum.HOMEWORK_REWRITE);
        String userPrompt = String.format(
                "【课程】%s\n【技能指标】%s\n【任务等级】%s\n【原题标题】%s\n【原题要求】%s",
                StrUtil.blankToDefault(req.getCourseName(), "未提供"),
                StrUtil.blankToDefault(req.getIndicatorName(), "未提供"),
                req.getHomeworkLevel() != null ? req.getHomeworkLevel() : "未提供",
                StrUtil.blankToDefault(req.getTitle(), "无"),
                StrUtil.blankToDefault(req.getRequirement(), "无")
        );
        Prompt prompt = buildGeneratePrompt(systemPrompt, userPrompt, options);
        String text;
        try {
            ChatResponse response = runtimeGuard.execute(AiBusinessCodeEnum.HOMEWORK_REWRITE.getCode(), model,
                    null, () -> chatModel.call(prompt));
            text = response.getResult() != null && response.getResult().getOutput() != null
                    ? response.getResult().getOutput().getText()
                    : "";
        } catch (Exception ex) {
            log.warn("[rewriteQuestion] AI 调用失败, modelId={}", model.getId(), ex);
            // 额度用尽、熔断、繁忙等业务提示原样交给教师，其他错误不外露细节
            return buildRewriteFailure(ex instanceof ServiceException serviceException
                    ? serviceException.getMessage() : "AI 模型调用失败", null);
        }
        return parseRewriteResponse(text);
    }

    @Override
    public Duration getReviewHardTimeout() {
        return aiProperties.getEdu().getHardTimeout();
    }

    @Override
    public Flux<String> generateCommentStream(AiEduGenerateReqDTO req) {
        runtimeGuard.requireExternalStudentDataEnabled();
        String businessCode = resolveGenerateBusinessCode(req.getType());
        AiModelDO model = bindingService.getRequiredModel(businessCode);
        StreamingChatModel chatModel = (StreamingChatModel) modalService.getChatModel(model.getId());
        ChatOptions options = buildOptions(model);

        String systemPrompt = buildGenerateSystemPrompt(req.getType());
        String userContent = buildGenerateUserContent(req.getType(), req.getStudentData());
        log.info("[generateCommentStream] start type={}, modelId={}, platform={}, model={}, maxTokens={}, systemPromptLength={}, userContentLength={}",
                req.getType(), model.getId(), model.getPlatform(), model.getModel(), model.getMaxTokens(),
                systemPrompt.length(), userContent.length());

        Prompt prompt = buildGeneratePrompt(systemPrompt, userContent, options);
        return runtimeGuard.executeStream(businessCode, model, req.getBusinessObjectId(),
                        () -> chatModel.stream(prompt)
                                .map(chunk -> StrUtil.nullToDefault(AiUtils.getChatResponseContent(chunk), ""))
                                .filter(StrUtil::isNotBlank))
                .doOnComplete(() -> log.info("[generateCommentStream] finish type={}, modelId={}",
                        req.getType(), model.getId()));
    }

    /**
     * 根据模型信息构建 ChatOptions，指定模型名称
     */
    private ChatOptions buildOptions(AiModelDO model) {
        AiApiKeyDO apiKey = apiKeyService.validateApiKey(model.getKeyId());
        AiPlatformEnum platform = AiPlatformEnum.validatePlatform(apiKey.getPlatform());
        return AiUtils.buildChatOptions(platform, model.getModel(), model.getTemperature(), model.getMaxTokens());
    }

    private Prompt buildGeneratePrompt(String systemPrompt, String userContent, ChatOptions options) {
        return new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userContent)), options);
    }

    private boolean shouldUseStreamingAggregation(AiModelDO model, ChatModel chatModel) {
        return AiPlatformEnum.OPENAI.getPlatform().equalsIgnoreCase(model.getPlatform())
                && StrUtil.startWithIgnoreCase(StrUtil.blankToDefault(model.getModel(), ""), "gpt-5")
                && chatModel instanceof StreamingChatModel;
    }

    private String callWithStreamingAggregation(StreamingChatModel chatModel, Prompt prompt, String type, AiModelDO model) {
        log.info("[generateComment] use streaming aggregation type={}, modelId={}, platform={}, model={}",
                type, model.getId(), model.getPlatform(), model.getModel());
        String content = chatModel.stream(prompt)
                .map(chunk -> StrUtil.nullToDefault(AiUtils.getChatResponseContent(chunk), ""))
                .filter(StrUtil::isNotBlank)
                .collectList()
                .map(parts -> String.join("", parts))
                .block(Duration.ofMinutes(2));
        if (StrUtil.isBlank(content)) {
            log.warn("[generateComment] streaming aggregation still empty, type={}, modelId={}, platform={}, model={}",
                    type, model.getId(), model.getPlatform(), model.getModel());
        }
        return StrUtil.nullToDefault(content, "");
    }

    private String buildGenerateSystemPrompt(String type) {
        if (StrUtil.equals(type, "analysis_class")) {
            return promptTemplateService.getActiveContent(AiPromptCodeEnum.CLASS_ANALYSIS);
        }
        if (StrUtil.equals(type, "analysis_person")) {
            return promptTemplateService.getActiveContent(AiPromptCodeEnum.STUDENT_ANALYSIS);
        }
        return promptTemplateService.getActiveContent(AiPromptCodeEnum.STUDENT_COMMENT);
    }

    private String resolveGenerateBusinessCode(String type) {
        return StrUtil.equals(type, "analysis_class")
                ? AiBusinessCodeEnum.CLASS_ANALYSIS.getCode()
                : AiBusinessCodeEnum.STUDENT_ANALYSIS.getCode();
    }

    private String buildGenerateUserContent(String type, String studentData) {
        String payload = StrUtil.blankToDefault(studentData, "{}");
        if (StrUtil.equals(type, "analysis_class")) {
            return "【任务说明】请基于以下“班级在课程中的总体数据”完成整体研判，不要输出与数据无关的泛化建议。\n"
                    + "【班级课程数据】" + payload;
        }
        if (StrUtil.equals(type, "analysis_person")) {
            return "【任务说明】请基于以下“学生课程成绩画像”输出个人学习诊断。\n"
                    + "【学生画像数据】" + payload;
        }
        return "【学生数据】" + payload;
    }

    private AiEduReviewRespDTO parseReviewResponse(String text) {
        AiEduReviewRespDTO resp = new AiEduReviewRespDTO();
        resp.setRawResponse(text);
        try {
            if (StrUtil.isBlank(text)) {
                throw new IllegalArgumentException("AI返回内容为空");
            }
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException("AI未返回有效JSON");
            }
            JSONObject obj = JSONUtil.parseObj(text.substring(start, end + 1));
            Integer score = obj.getInt("score");
            String comment = obj.getStr("comment");
            Double confidence = obj.getDouble("confidence");
            if (score == null || score < 0 || score > 100) {
                throw new IllegalArgumentException("AI评分必须是0到100的整数");
            }
            if (StrUtil.isBlank(comment)) {
                throw new IllegalArgumentException("AI评语不能为空");
            }
            if (confidence == null || confidence < 0 || confidence > 1) {
                throw new IllegalArgumentException("AI置信度必须在0到1之间");
            }
            resp.setScore(score);
            resp.setComment(comment);
            resp.setConfidence(confidence);
            Object subScores = obj.get("subScores");
            resp.setSubScoresJson(JSONUtil.toJsonStr(subScores != null ? subScores : List.of()));
            Object riskFlags = obj.get("riskFlags");
            resp.setRiskFlagsJson(JSONUtil.toJsonStr(riskFlags != null ? riskFlags : List.of()));
            Object evidenceSummary = obj.get("evidenceSummary");
            resp.setEvidenceSummaryJson(JSONUtil.toJsonStr(evidenceSummary != null ? evidenceSummary : new JSONObject()));
        } catch (Exception ex) {
            log.warn("[parseReviewResponse] invalid AI response, contentLength={}",
                    text != null ? text.length() : 0);
            throw new IllegalArgumentException("AI 初评结果格式不合法", ex);
        }
        resp.setComment(sanitizeReviewComment(resp.getComment()));
        return resp;
    }

    private AiEduQuestionRewriteRespDTO parseRewriteResponse(String text) {
        AiEduQuestionRewriteRespDTO resp = new AiEduQuestionRewriteRespDTO();
        resp.setRawResponse(text);
        try {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return buildRewriteFailure("AI 返回的内容格式不正确", text);
            }
            JSONObject obj = JSONUtil.parseObj(text.substring(start, end + 1));
            String title = obj.getStr("title");
            String requirement = obj.getStr("requirement");
            if (StrUtil.isBlank(title) || StrUtil.isBlank(requirement)) {
                return buildRewriteFailure("AI 返回的内容缺少标题或要求", text);
            }
            resp.setSuccess(true);
            resp.setTitle(title.trim());
            resp.setRequirement(requirement.trim());
            return resp;
        } catch (Exception ex) {
            log.warn("[rewriteQuestion] 解析AI响应失败, text={}", text, ex);
            return buildRewriteFailure("AI 返回的内容无法解析", text);
        }
    }

    private AiEduQuestionRewriteRespDTO buildRewriteFailure(String errorMessage, String rawResponse) {
        AiEduQuestionRewriteRespDTO resp = new AiEduQuestionRewriteRespDTO();
        resp.setSuccess(false);
        resp.setErrorMessage(errorMessage);
        resp.setRawResponse(rawResponse);
        return resp;
    }

    private String sanitizeReviewComment(String comment) {
        if (StrUtil.isBlank(comment)) {
            return comment;
        }
        String normalized = comment.trim();
        for (String prefix : List.of("自动初评：", "自动初评:", "AI初评：", "AI初评:", "初评：", "初评:")) {
            if (normalized.startsWith(prefix)) {
                return normalized.substring(prefix.length()).trim();
            }
        }
        normalized = normalized.replace("识别系统日志", "无关噪声文本");
        normalized = normalized.replace("系统日志", "无关噪声");
        normalized = normalized.replace("内部日志", "无关噪声");
        normalized = normalized.replace("模型推理", "初评判断");
        normalized = normalized.replace("命令执行", "转写处理");
        normalized = normalized.replace("接口调用", "转写处理");
        normalized = normalized.replace("OCR", "识别结果");
        normalized = normalized.replace("ASR", "转写结果");
        return normalized;
    }

    private UserMessage buildReviewUserMessage(AiEduReviewReqDTO req, String userPrompt) {
        List<Media> mediaList = buildImageMedia(req.getImageUrlsJson());
        if (mediaList.isEmpty()) {
            return new UserMessage(userPrompt);
        }
        return UserMessage.builder()
                .text(userPrompt)
                .media(mediaList)
                .build();
    }

    private List<Media> buildImageMedia(String imageUrlsJson) {
        if (StrUtil.isBlank(imageUrlsJson)) {
            return List.of();
        }
        try {
            JSONArray array = JSONUtil.parseArray(imageUrlsJson);
            List<Media> mediaList = new ArrayList<>();
            for (Object item : array) {
                if (mediaList.size() >= MAX_REVIEW_IMAGE_COUNT) {
                    log.warn("[buildImageMedia] image count exceeds limit, ignored remaining images");
                    break;
                }
                String url = String.valueOf(item);
                if (StrUtil.isBlank(url)) {
                    continue;
                }
                FileContentRespDTO platformFile = fileApi.getFileContent(url, MAX_REVIEW_IMAGE_BYTES);
                byte[] imageBytes = ImageOrientationUtil.normalize(platformFile.getContent());
                String mimeType = FileTypeUtils.getMineType(imageBytes, platformFile.getName());
                if (!FileTypeUtils.isImage(mimeType)) {
                    continue;
                }
                MimeType mime = MimeTypeUtils.parseMimeType(mimeType);
                mediaList.add(new Media(mime, new ByteArrayResource(imageBytes)));
            }
            return mediaList;
        } catch (Exception ex) {
            log.warn("[buildImageMedia] build media failed, imageUrlsJson={}", imageUrlsJson, ex);
            return List.of();
        }
    }
}

