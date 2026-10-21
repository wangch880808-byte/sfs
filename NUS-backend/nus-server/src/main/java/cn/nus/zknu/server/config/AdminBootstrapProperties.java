package cn.nus.zknu.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nus.admin-bootstrap")
@Data
public class AdminBootstrapProperties {

    private boolean enabled;

    private String username = "admin";

    private String passwordFile;
}
