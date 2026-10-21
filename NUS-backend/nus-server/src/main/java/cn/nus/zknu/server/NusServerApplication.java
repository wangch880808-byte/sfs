package cn.nus.zknu.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 项目的启动类
 * <p>
 *
 * @author hyh
 */
@SuppressWarnings("SpringComponentScan") // 忽略 IDEA 无法识别 ${nus.info.base-package}
@SpringBootApplication(scanBasePackages = {"${nus.info.base-package}.server", "${nus.info.base-package}.module"})
public class NusServerApplication {

    public static void main(String[] args) {

        ConfigurableApplicationContext context = SpringApplication.run(NusServerApplication.class, args);
        if (context.getEnvironment().getProperty("nus.admin-bootstrap.enabled", Boolean.class, false)) {
            System.exit(SpringApplication.exit(context, () -> 0));
        }
//        new SpringApplicationBuilder(NusServerApplication.class)
//                .applicationStartup(new BufferingApplicationStartup(20480))
//                .run(args);

    }

}
