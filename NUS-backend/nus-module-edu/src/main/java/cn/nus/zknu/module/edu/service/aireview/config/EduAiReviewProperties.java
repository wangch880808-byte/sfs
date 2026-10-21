package cn.nus.zknu.module.edu.service.aireview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "nus.edu.ai-review")
@Data
public class EduAiReviewProperties {

    /**
     * 是否启用自动初评
     */
    private boolean enabled = true;

    /**
     * ffmpeg 命令
     */
    private String ffmpegCommand = "ffmpeg";

    /**
     * ffprobe 命令
     */
    private String ffprobeCommand = "ffprobe";

    /** Vosk 转写 Python 可执行文件 */
    private String audioTranscriptionPython = "";

    /** Vosk 转写脚本 */
    private String audioTranscriptionScript = "";

    /** Vosk 模型目录 */
    private String audioTranscriptionModelDir = "";

    /**
     * 图片 OCR 参数列表，单独的 {input} 参数会替换为文件路径
     */
    private List<String> imageOcrCommand = List.of();

    /**
     * 临时工作目录
     */
    private String workDir = System.getProperty("java.io.tmpdir");

    /** AI 初评最多处理的附件数。 */
    private int maxAttachmentCount = 5;

    /** AI 初评单个附件最大字节数。 */
    private long maxAttachmentBytes = 20L * 1024 * 1024;

    /** 自动初评后台任务线程数。 */
    private int taskPoolSize = 2;

    /** 自动初评等待队列容量。 */
    private int taskQueueCapacity = 100;
}
