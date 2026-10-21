package cn.nus.zknu.module.edu.service.homework;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import cn.nus.zknu.module.infra.api.file.FileApi;
import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_ATTACHMENT_INVALID;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_ATTACHMENT_REQUIRED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.HOMEWORK_ATTACHMENT_TYPE_INVALID;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_HOMEWORK_ATTACHMENT_EMPTY;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_HOMEWORK_ATTACHMENT_FILE_TOO_LARGE;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_HOMEWORK_ATTACHMENT_TOO_MANY;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_HOMEWORK_ATTACHMENT_TOTAL_TOO_LARGE;

/**
 * 学生作业附件校验。
 * <p>
 * 附件个数、单个大小、合计大小的上限放在 infra_config（edu.homework.attachment.*），
 * 读取失败或超出允许范围时回退到默认值（10 个、100 MB、300 MB）。
 */
@Service
@Slf4j
public class HomeworkAttachmentService {

    public static final String CONFIG_MAX_COUNT = "edu.homework.attachment.max-count";
    public static final String CONFIG_MAX_FILE_MB = "edu.homework.attachment.max-file-mb";
    public static final String CONFIG_MAX_TOTAL_MB = "edu.homework.attachment.max-total-mb";

    static final int DEFAULT_MAX_COUNT = 10;
    static final int DEFAULT_MAX_FILE_MB = 100;
    static final int DEFAULT_MAX_TOTAL_MB = 300;
    /** 个数上限的允许范围 */
    static final int MAX_COUNT_UPPER = 50;
    /** 单个附件上限不能超过 multipart 的 max-file-size（application.yaml 为 500MB） */
    static final int MAX_FILE_MB_UPPER = 500;
    /** 合计上限的允许范围（5 GB） */
    static final int MAX_TOTAL_MB_UPPER = 5120;

    /** 附件类型的取值：image、audio、video；为空表示不限 */
    private static final Set<String> SUPPORTED_TYPES = Set.of("image", "audio", "video");

    @Resource
    private FileApi fileApi;
    @Resource
    private ConfigApi configApi;

    /**
     * 当前生效的附件上限。
     */
    public HomeworkAttachmentLimits getLimits() {
        return new HomeworkAttachmentLimits(
                readInt(CONFIG_MAX_COUNT, DEFAULT_MAX_COUNT, 1, MAX_COUNT_UPPER),
                readInt(CONFIG_MAX_FILE_MB, DEFAULT_MAX_FILE_MB, 1, MAX_FILE_MB_UPPER),
                readInt(CONFIG_MAX_TOTAL_MB, DEFAULT_MAX_TOTAL_MB, 1, MAX_TOTAL_MB_UPPER));
    }

    /**
     * 任务允许的附件类型（image、audio、video，按字母序）；空列表表示不限类型。
     * 任务的类型配置不合法时返回空列表，提交时仍由 {@link #validateAndNormalize} 报错。
     */
    public List<String> getAllowedTypes(EduHomeworkDO homework) {
        try {
            return parseRequiredTypes(homework).stream().sorted().toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    public String validateAndNormalize(EduHomeworkDO homework, Long studentId,
                                       String content, String attachmentUrls) {
        List<String> urls = parseUrls(attachmentUrls);
        return validateUrls(homework, studentId, content, urls);
    }

    /**
     * 上传前校验单个附件，最终提交时仍会再次基于平台文件元数据做完整校验。
     */
    public void validateUpload(EduHomeworkDO homework, long fileSize, String mimeType) {
        if (fileSize <= 0) {
            throw exception(STUDENT_HOMEWORK_ATTACHMENT_EMPTY);
        }
        HomeworkAttachmentLimits limits = getLimits();
        if (fileSize > limits.maxFileBytes()) {
            throw exception(STUDENT_HOMEWORK_ATTACHMENT_FILE_TOO_LARGE, limits.maxFileMb());
        }
        Set<String> requiredTypes = parseRequiredTypes(homework);
        if (!requiredTypes.isEmpty() && !requiredTypes.contains(resolveType(mimeType))) {
            throw exception(HOMEWORK_ATTACHMENT_TYPE_INVALID);
        }
    }

    private String validateUrls(EduHomeworkDO homework, Long studentId,
                                String content, List<String> urls) {
        HomeworkAttachmentLimits limits = getLimits();
        if (urls.size() > limits.maxCount()) {
            throw exception(STUDENT_HOMEWORK_ATTACHMENT_TOO_MANY, limits.maxCount());
        }
        Set<String> requiredTypes = parseRequiredTypes(homework);
        if (!requiredTypes.isEmpty() && urls.isEmpty()) {
            throw exception(HOMEWORK_ATTACHMENT_REQUIRED);
        }
        if ("text".equalsIgnoreCase(StrUtil.blankToDefault(homework.getSubmissionMode(), "text"))
                && StrUtil.isBlank(content)) {
            throw exception(HOMEWORK_ATTACHMENT_INVALID, "纯文本任务必须填写提交内容");
        }
        if (!"text".equalsIgnoreCase(StrUtil.blankToDefault(homework.getSubmissionMode(), "text"))
                && StrUtil.isBlank(content) && urls.isEmpty()) {
            throw exception(HOMEWORK_ATTACHMENT_REQUIRED);
        }

        long totalBytes = 0;
        Set<String> uniqueUrls = new HashSet<>();
        for (String url : urls) {
            if (!uniqueUrls.add(url)) {
                throw exception(HOMEWORK_ATTACHMENT_INVALID, "附件地址不能重复");
            }
            FileContentRespDTO file;
            try {
                file = fileApi.getFile(url);
            } catch (Exception ex) {
                throw exception(HOMEWORK_ATTACHMENT_INVALID, "附件不是已登记的平台文件");
            }
            if (!String.valueOf(studentId).equals(file.getCreator())) {
                throw exception(HOMEWORK_ATTACHMENT_INVALID, "只能提交本人上传的附件");
            }
            String expectedPathPrefix = "edu/homework-submission/" + homework.getId() + "/";
            if (StrUtil.isBlank(file.getPath()) || !file.getPath().startsWith(expectedPathPrefix)) {
                throw exception(HOMEWORK_ATTACHMENT_INVALID, "附件不属于当前任务");
            }
            long fileSize = file.getSize() != null ? file.getSize() : 0L;
            if (fileSize < 0 || fileSize > limits.maxFileBytes()) {
                throw exception(STUDENT_HOMEWORK_ATTACHMENT_FILE_TOO_LARGE, limits.maxFileMb());
            }
            totalBytes += fileSize;
            if (totalBytes > limits.maxTotalBytes()) {
                throw exception(STUDENT_HOMEWORK_ATTACHMENT_TOTAL_TOO_LARGE, limits.maxTotalMb());
            }
            if (!requiredTypes.isEmpty() && !requiredTypes.contains(resolveType(file))) {
                throw exception(HOMEWORK_ATTACHMENT_TYPE_INVALID);
            }
        }
        return JSONUtil.toJsonStr(urls);
    }

    private List<String> parseUrls(String value) {
        if (StrUtil.isBlank(value)) {
            return List.of();
        }
        try {
            List<String> urls = JSONUtil.parseArray(value).toList(String.class).stream()
                    .map(StrUtil::trim)
                    .filter(StrUtil::isNotBlank)
                    .toList();
            if (urls.size() != JSONUtil.parseArray(value).size()) {
                throw new IllegalArgumentException("附件地址不能为空");
            }
            return urls;
        } catch (Exception ex) {
            throw exception(HOMEWORK_ATTACHMENT_INVALID, "附件地址必须是字符串 JSON 数组");
        }
    }

    private Set<String> parseRequiredTypes(EduHomeworkDO homework) {
        if (StrUtil.isNotBlank(homework.getRequiredAttachmentTypesJson())) {
            try {
                Set<String> types = new HashSet<>();
                for (String item : JSONUtil.parseArray(homework.getRequiredAttachmentTypesJson()).toList(String.class)) {
                    String type = StrUtil.trim(item).toLowerCase(Locale.ROOT);
                    if (!SUPPORTED_TYPES.contains(type)) {
                        throw new IllegalArgumentException("unsupported type");
                    }
                    types.add(type);
                }
                return types;
            } catch (Exception ignored) {
                throw exception(HOMEWORK_ATTACHMENT_INVALID, "任务的附件类型配置不合法，请联系教师修正");
            }
        }
        return switch (StrUtil.blankToDefault(homework.getSubmissionMode(), "text")) {
            case "image" -> Set.of("image");
            case "audio" -> Set.of("audio");
            case "video" -> Set.of("video");
            case "mixed" -> Set.of("image", "audio", "video");
            default -> Set.of();
        };
    }

    private String resolveType(FileContentRespDTO file) {
        return resolveType(file.getType());
    }

    private String resolveType(String value) {
        String mimeType = StrUtil.blankToDefault(value, "").toLowerCase(Locale.ROOT);
        if (mimeType.startsWith("image/")) {
            return "image";
        }
        if (mimeType.startsWith("audio/")) {
            return "audio";
        }
        if (mimeType.startsWith("video/")) {
            return "video";
        }
        return "other";
    }

    private int readInt(String key, int fallback, int min, int max) {
        String raw;
        try {
            raw = configApi == null ? null : configApi.getConfigValueByKey(key);
        } catch (Exception ex) {
            log.warn("读取附件上限配置失败，使用默认值 {}：{}", fallback, key);
            return fallback;
        }
        if (StrUtil.isBlank(raw)) {
            return fallback;
        }
        try {
            // 允许 “100” 或 “100.0” 这样的写法，小数部分不计
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value) || value < min || value > max) {
                log.warn("附件上限配置超出范围 [{}, {}]，使用默认值 {}：{}={}", min, max, fallback, key, raw);
                return fallback;
            }
            return (int) value;
        } catch (NumberFormatException ex) {
            log.warn("附件上限配置不是数字，使用默认值 {}：{}={}", fallback, key, raw);
            return fallback;
        }
    }

}
