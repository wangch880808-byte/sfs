package cn.nus.zknu.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ProductionProfileGuardTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    @Test
    void acceptsProdProfile() {
        ProductionProfileGuard guard = new ProductionProfileGuard(new MockEnvironment().withProperty(
                "spring.profiles.active", "prod"));

        assertDoesNotThrow(() -> guard.run(NO_ARGS));
    }

    @Test
    void rejectsMissingProdProfile() {
        ProductionProfileGuard guard = new ProductionProfileGuard(new MockEnvironment().withProperty(
                "spring.profiles.active", "staging"));

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires the prod profile");
    }

    @Test
    void rejectsLocalProfileMixedWithProd() {
        ProductionProfileGuard guard = new ProductionProfileGuard(new MockEnvironment().withProperty(
                "spring.profiles.active", "prod,local"));

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rejects local/dev profiles");
    }
}
