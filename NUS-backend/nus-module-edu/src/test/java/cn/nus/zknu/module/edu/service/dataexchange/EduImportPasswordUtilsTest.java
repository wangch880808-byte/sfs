package cn.nus.zknu.module.edu.service.dataexchange;

import cn.nus.zknu.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.IMPORT_INITIAL_PASSWORD_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EduImportPasswordUtilsTest {

    @Test
    void resolve_prefersTrimmedOverride() {
        assertEquals("newPass1", EduImportPasswordUtils.resolve("  newPass1  ", "admin123"));
    }

    @Test
    void resolve_usesDefaultWhenOverrideIsBlank() {
        assertEquals("admin123", EduImportPasswordUtils.resolve("  ", "admin123"));
    }

    @Test
    void resolve_rejectsInvalidEffectivePassword() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> EduImportPasswordUtils.resolve(null, "123"));
        assertEquals(IMPORT_INITIAL_PASSWORD_INVALID.getCode(), exception.getCode());
    }

}
