package cn.nus.zknu.module.edu.enums;

import cn.nus.zknu.framework.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeConstantsUniqueTest {
    @Test
    void everyEduErrorCodeHasAUniqueNumber() throws Exception {
        var names = new HashMap<Integer, String>();
        for (var field : ErrorCodeConstants.class.getFields()) {
            if (field.getType() == ErrorCode.class) {
                var code = (ErrorCode) field.get(null);
                assertNull(names.putIfAbsent(code.getCode(), field.getName()), field.getName());
            }
        }
    }
}
