package cn.nus.zknu.module.infra.framework.file.core.client.ftp;

import cn.nus.zknu.module.infra.framework.file.core.client.FileClientConfig;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

/**
 * Ftp 文件客户端的配置类
 *
 * @author hyh
 */
@Data
public class FtpFileClientConfig implements FileClientConfig {

    /**
     * 基础路径
     */
    @NotEmpty(message = "基础路径不能为空")
    private String basePath;

    /**
     * 自定义域名
     */
    @NotEmpty(message = "自定义域名不能为空")
    @URL(message = "自定义域名必须是 URL 格式")
    private String domain;

    /**
     * 主机地址
     */
    @NotEmpty(message = "主机地址不能为空")
    private String host;
    /**
     * 主机端口
     */
    @NotNull(message = "主机端口不能为空")
    private Integer port;
    /**
     * 用户名
     */
    @NotEmpty(message = "用户名不能为空")
    private String username;
    /**
     * 密码
     */
    @NotEmpty(message = "密码不能为空")
    private String password;
    /**
     * 连接模式
     * <p>
     * 使用 {@link  cn.hutool.extra.ftp.FtpMode} 对应的字符串
     */
    @NotEmpty(message = "连接模式不能为空")
    private String mode;

}
