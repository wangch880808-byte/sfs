package cn.nus.zknu.module.edu.service.aireview;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.nus.zknu.module.infra.framework.file.core.utils.FileTypeUtils;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.service.aireview.config.EduAiReviewProperties;
import cn.nus.zknu.module.edu.service.aireview.support.LocalCommandRunner;
import cn.nus.zknu.module.edu.service.aireview.support.ReviewEvidence;
import cn.nus.zknu.module.infra.api.file.FileApi;
import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class HomeworkReviewEvidenceService {

    @Resource
    private EduAiReviewProperties properties;
    @Resource
    private LocalCommandRunner localCommandRunner;
    @Resource
    private FileApi fileApi;

    public ReviewEvidence build(EduHomeworkDO homework, EduHomeworkRecordDO record) {
        ReviewEvidence evidence = new ReviewEvidence();
        evidence.setSubmissionMode(StrUtil.blankToDefault(homework.getSubmissionMode(), "text"));
        if ("text".equalsIgnoreCase(evidence.getSubmissionMode())) {
            evidence.setTranscript(record.getContent());
        } else {
            evidence.setTranscript(null);
        }

        List<String> attachments = parseAttachmentUrls(record.getAttachmentUrls());
        if (attachments.size() > properties.getMaxAttachmentCount()) {
            evidence.getRiskFlags().add("attachment_count_exceeded");
            attachments = attachments.subList(0, properties.getMaxAttachmentCount());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recordContent", StrUtil.blankToDefault(record.getContent(), ""));
        payload.put("attachmentUrls", attachments);
        payload.put("submissionMode", evidence.getSubmissionMode());

        for (String attachmentUrl : attachments) {
            String mimeType = FileTypeUtils.getMineType(FileNameUtil.getName(attachmentUrl));
            if (StrUtil.startWith(mimeType, "image/")) {
                evidence.getImageUrls().add(attachmentUrl);
            } else if (StrUtil.startWith(mimeType, "audio/")) {
                evidence.getAudioUrls().add(attachmentUrl);
            } else if (StrUtil.startWith(mimeType, "video/")) {
                evidence.getVideoUrls().add(attachmentUrl);
            } else {
                evidence.getTextUrls().add(attachmentUrl);
            }
        }

        if (!evidence.getAudioUrls().isEmpty()) {
            payload.put("audioEvidence", buildAudioEvidence(evidence.getAudioUrls(), evidence));
        }
        if (!evidence.getImageUrls().isEmpty()) {
            payload.put("imageEvidence", buildImageEvidence(evidence.getImageUrls(), evidence));
        }
        if (!evidence.getVideoUrls().isEmpty()) {
            evidence.getRiskFlags().add("video_manual_review_only");
            payload.put("videoEvidence", Map.of(
                    "count", evidence.getVideoUrls().size(),
                    "mode", "archive_only"
            ));
        }
        if (!evidence.getRiskFlags().isEmpty()) {
            payload.put("riskFlags", evidence.getRiskFlags());
        }
        evidence.setPayload(payload);
        return evidence;
    }

    private Map<String, Object> buildAudioEvidence(List<String> audioUrls, ReviewEvidence evidence) {
        List<Map<String, Object>> items = new ArrayList<>();
        String transcript = null;
        for (String audioUrl : audioUrls) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("url", audioUrl);
            File downloaded = null;
            File normalizedAudio = null;
            try {
                downloaded = downloadToTemp(audioUrl);
                item.put("fileName", downloaded.getName());
                String ffprobe = properties.getFfprobeCommand();
                if (StrUtil.isNotBlank(ffprobe)) {
                    List<String> command = List.of(ffprobe, "-v", "error", "-show_entries", "format=duration",
                            "-of", "default=noprint_wrappers=1:nokey=1", FileUtil.getAbsolutePath(downloaded));
                    String output = localCommandRunner.run(command, Duration.ofSeconds(20));
                    item.put("durationSec", StrUtil.trim(output));
                }
                if (StrUtil.isAllNotBlank(properties.getAudioTranscriptionPython(),
                        properties.getAudioTranscriptionScript(), properties.getAudioTranscriptionModelDir())) {
                    String normalized = downloaded.getAbsolutePath();
                    if (StrUtil.isNotBlank(properties.getFfmpegCommand())) {
                        normalizedAudio = FileUtil.createTempFile(
                                "review-audio-", ".wav", FileUtil.file(properties.getWorkDir()), true);
                        List<String> command = List.of(properties.getFfmpegCommand(), "-y", "-i",
                                FileUtil.getAbsolutePath(downloaded), "-ac", "1", "-ar", "16000",
                                FileUtil.getAbsolutePath(normalizedAudio));
                        localCommandRunner.run(command, Duration.ofMinutes(2));
                        normalized = normalizedAudio.getAbsolutePath();
                    }
                    List<String> command = List.of(properties.getAudioTranscriptionPython(),
                            properties.getAudioTranscriptionScript(), "--model-dir",
                            properties.getAudioTranscriptionModelDir(), normalized);
                    String asrText = sanitizeTranscript(
                            localCommandRunner.run(command, Duration.ofMinutes(5)));
                    item.put("transcript", asrText);
                    if (StrUtil.isNotBlank(asrText)) {
                        transcript = appendTranscript(transcript, asrText);
                    } else {
                        evidence.getRiskFlags().add("audio_transcript_empty");
                    }
                }
            } catch (Exception ex) {
                log.warn("[buildAudioEvidence] failed for {}", audioUrl, ex);
                evidence.getRiskFlags().add("audio_evidence_failed");
                item.put("error", ex.getMessage());
            } finally {
                FileUtil.del(downloaded);
                FileUtil.del(normalizedAudio);
            }
            items.add(item);
        }
        evidence.setTranscript(transcript);
        return Map.of(
                "items", items,
                "transcript", StrUtil.blankToDefault(transcript, "")
        );
    }

    private Map<String, Object> buildImageEvidence(List<String> imageUrls, ReviewEvidence evidence) {
        List<Map<String, Object>> items = new ArrayList<>();
        List<String> ocrTexts = new ArrayList<>();
        for (String imageUrl : imageUrls) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("url", imageUrl);
            File downloaded = null;
            try {
                downloaded = downloadToTemp(imageUrl);
                item.put("fileName", downloaded.getName());
                if (!properties.getImageOcrCommand().isEmpty()) {
                    String inputPath = downloaded.getAbsolutePath();
                    List<String> command = properties.getImageOcrCommand().stream()
                            .map(argument -> "{input}".equals(argument) ? inputPath : argument)
                            .toList();
                    String ocrText = StrUtil.trim(localCommandRunner.run(command, Duration.ofMinutes(2)));
                    item.put("ocrText", ocrText);
                    if (StrUtil.isNotBlank(ocrText)) {
                        ocrTexts.add(ocrText);
                    }
                }
            } catch (Exception ex) {
                log.warn("[buildImageEvidence] failed for {}", imageUrl, ex);
                evidence.getRiskFlags().add("image_evidence_failed");
                item.put("error", ex.getMessage());
            } finally {
                FileUtil.del(downloaded);
            }
            items.add(item);
        }
        return Map.of(
                "items", items,
                "ocrText", String.join("\n\n", ocrTexts)
        );
    }

    private List<String> parseAttachmentUrls(String value) {
        if (StrUtil.isBlank(value)) {
            return List.of();
        }
        try {
            return JSONUtil.parseArray(value).toList(String.class);
        } catch (Exception ex) {
            log.warn("[parseAttachmentUrls] invalid json: {}", value, ex);
            return List.of();
        }
    }

    private File downloadToTemp(String url) {
        FileContentRespDTO platformFile = fileApi.getFileContent(url, properties.getMaxAttachmentBytes());
        File dir = FileUtil.mkdir(properties.getWorkDir());
        String extension = FileNameUtil.extName(StrUtil.blankToDefault(platformFile.getName(), url));
        File file = FileUtil.createTempFile("review-", StrUtil.isBlank(extension) ? ".bin" : "." + extension, dir, true);
        FileUtil.writeBytes(platformFile.getContent(), file);
        return file;
    }

    private String appendTranscript(String original, String delta) {
        if (StrUtil.isBlank(original)) {
            return delta;
        }
        return original + "\n" + delta;
    }

    private String sanitizeTranscript(String rawTranscript) {
        if (StrUtil.isBlank(rawTranscript)) {
            return "";
        }
        List<String> lines = StrUtil.splitTrim(rawTranscript, '\n');
        List<String> cleaned = new ArrayList<>();
        for (String line : lines) {
            String normalized = StrUtil.trim(line);
            if (StrUtil.isBlank(normalized)) {
                continue;
            }
            normalized = normalized.replaceAll("\\s+", " ").trim();
            if (StrUtil.startWithIgnoreCase(normalized, "LOG ")
                    || StrUtil.startWithIgnoreCase(normalized, "LOG(")
                    || StrUtil.startWithIgnoreCase(normalized, "WARNING:")
                    || StrUtil.startWithIgnoreCase(normalized, "INFO:")
                    || StrUtil.startWithIgnoreCase(normalized, "DEBUG:")
                    || StrUtil.containsIgnoreCase(normalized, "vosk")
                    || StrUtil.containsIgnoreCase(normalized, "kaldi")
                    || normalized.matches("^\\d{4}-\\d{2}-\\d{2}.*")
                    || normalized.matches("^I\\d{4} .*")
                    || normalized.matches("^W\\d{4} .*")
                    || normalized.matches("^E\\d{4} .*")) {
                continue;
            }
            cleaned.add(normalized);
        }
        return String.join("\n", cleaned).trim();
    }
}
