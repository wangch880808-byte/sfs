package cn.nus.zknu.module.infra.service.monitor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "nus.monitor-access")
@Data
public class MonitorAccessProperties {

    private Duration ticketTtl = Duration.ofSeconds(45);
    private Duration sessionTtl = Duration.ofMinutes(20);
    private boolean cookieSecure;
    private String skywalkingUrl = "http://127.0.0.1:8082/";
    private String horizonInternalUrl = "http://skywalking-ui:8081";
    private String horizonUsername;
    private String horizonPassword;

}
