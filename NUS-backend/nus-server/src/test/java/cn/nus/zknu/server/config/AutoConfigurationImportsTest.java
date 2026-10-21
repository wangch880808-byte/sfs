package cn.nus.zknu.server.config;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 删除代码时容易漏掉 AutoConfiguration.imports 中的登记项，单测不启动容器发现不了，
 * 生产启动时才会报 “Unable to read meta-data for class”。这里逐个确认本项目登记的自动配置类都存在。
 */
class AutoConfigurationImportsTest {

    private static final String IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    @Test
    void registeredAutoConfigurationsAreLoadable() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        List<String> registered = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (URL url : Collections.list(classLoader.getResources(IMPORTS))) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String className = line.strip();
                    if (!className.startsWith("cn.nus.zknu.")) {
                        continue;
                    }
                    registered.add(className);
                    try {
                        Class.forName(className, false, classLoader);
                    } catch (ClassNotFoundException ex) {
                        missing.add(className + "（登记于 " + url + "）");
                    }
                }
            }
        }
        assertFalse(registered.isEmpty(), "未找到本项目的自动配置登记，检查测试类路径");
        assertTrue(missing.isEmpty(), "以下自动配置类已不存在：" + missing);
    }

}
