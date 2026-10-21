package cn.nus.zknu.module.ai.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoToolFunctionRemovalTest {

    @Test
    void demoToolFunctionsAreRemoved() {
        List.of("cn.nus.zknu.module.ai.tool.function.DirectoryListToolFunction",
                        "cn.nus.zknu.module.ai.tool.function.WeatherQueryToolFunction",
                        "cn.nus.zknu.module.ai.tool.function.UserProfileQueryToolFunction")
                .forEach(className -> assertThatThrownBy(() -> Class.forName(className))
                        .as(className).isInstanceOf(ClassNotFoundException.class));
    }

}
