package cn.nus.zknu.module.system.controller.admin.user.vo.profile;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserProfileUpdateReqVOTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cleanup() {
        factory.close();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"15601691300", "19912345678"})
    void acceptsOptionalOrValidMobile(String mobile) {
        UserProfileUpdateReqVO request = new UserProfileUpdateReqVO();
        request.setMobile(mobile);

        assertTrue(validator.validate(request).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1560169130", "156016913000", "25601691300", "1560169130a", " 15601691300", " "})
    void rejectsInvalidMobile(String mobile) {
        UserProfileUpdateReqVO request = new UserProfileUpdateReqVO();
        request.setMobile(mobile);

        var violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("mobile", violations.iterator().next().getPropertyPath().toString());
    }

}
