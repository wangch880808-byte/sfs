package cn.nus.zknu.module.edu.service.dataexchange;

import cn.hutool.core.util.StrUtil;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.IMPORT_INITIAL_PASSWORD_INVALID;

public final class EduImportPasswordUtils {

    private EduImportPasswordUtils() {
    }

    public static String resolve(String overridePassword, String defaultPassword) {
        String password = StrUtil.blankToDefault(StrUtil.trimToNull(overridePassword), defaultPassword);
        if (password == null || password.length() < 4 || password.length() > 16) {
            throw exception(IMPORT_INITIAL_PASSWORD_INVALID);
        }
        return password;
    }

}
