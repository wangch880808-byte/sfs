package cn.nus.zknu.server.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "nus", name = "production-mode", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionProfileGuard implements ApplicationRunner {

    private static final Set<String> FORBIDDEN_PROFILES = Set.of("local", "dev");

    private final Environment environment;

    public ProductionProfileGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<String> activeProfiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        if (!activeProfiles.contains("prod") || activeProfiles.stream().anyMatch(FORBIDDEN_PROFILES::contains)) {
            throw new IllegalStateException(
                    "production mode requires the prod profile and rejects local/dev profiles: " + activeProfiles);
        }
    }
}
