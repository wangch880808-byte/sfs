package cn.nus.zknu.module.ai.framework.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI 配置类
 */
@ConfigurationProperties(prefix = "nus.ai")
@Data
public class NusAiProperties {

    /** 教育业务 AI 的数据外发和运行保护。 */
    private Edu edu = new Edu();

    /** 提示词启用版本的本地缓存时长；后台保存新版本时本实例立即刷新。 */
    private Duration promptCacheTtl = Duration.ofSeconds(30);

    /** AI 接入治理参数（额度、熔断、日志保留天数）的本地缓存时长；在参数配置里修改后最多这么久生效。 */
    private Duration configCacheTtl = Duration.ofSeconds(30);

    @Data
    public static class Edu {

        /** 是否允许向配置的模型服务发送学生作业和学情数据。 */
        private boolean externalStudentDataEnabled = false;

        /** 单实例允许同时执行的教育 AI 请求数。 */
        private int maxConcurrent = 4;

        /** 单次请求等待模型完整响应的最长时间（每次尝试各自计时）。 */
        private Duration callTimeout = Duration.ofSeconds(120);

        /** 单次教育 AI 同步调用的总时间上限；重试只在剩余时间够完成一次完整尝试时进行。 */
        private Duration hardTimeout = Duration.ofSeconds(180);

        /** 单个学生文本字段的最大字符数。 */
        private int maxStudentTextLength = 8000;

        /** 建立模型服务连接的最长时间。 */
        private Duration connectTimeout = Duration.ofSeconds(10);

        /** 每次调用最多尝试几次（含第一次）；只对限流、服务端错误、超时和连接失败重试。 */
        private int maxAttempts = 3;

        /** 第一次重试前的等待时间，之后按倍数递增。 */
        private Duration retryInitialInterval = Duration.ofSeconds(1);

        /** 重试等待时间的递增倍数。 */
        private double retryMultiplier = 2.0;

        /** 单次重试等待时间的上限。 */
        private Duration retryMaxInterval = Duration.ofSeconds(4);

        /** 后台任务（AI 初评队列、AI 新建任务等）在并发已满时最多排队等待多久；页面上的请求不等待。 */
        private Duration backgroundPermitWait = Duration.ofSeconds(60);

    }

}
