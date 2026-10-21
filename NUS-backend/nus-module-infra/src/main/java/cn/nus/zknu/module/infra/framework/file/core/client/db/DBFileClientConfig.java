package cn.nus.zknu.module.infra.framework.file.core.client.db;

import cn.nus.zknu.module.infra.framework.file.core.client.FileClientConfig;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

/**
 * 基于 DB 存储的文件客户端的配置类
 *
 * @author hyh
 */
@Data
public class DBFileClientConfig implements FileClientConfig {

    /**
     * 自定义域名
     */
    @NotEmpty(message = "自定义域名不能为空")
    @URL(message = "自定义域名必须是 URL 格式")
    private String domain;

}
