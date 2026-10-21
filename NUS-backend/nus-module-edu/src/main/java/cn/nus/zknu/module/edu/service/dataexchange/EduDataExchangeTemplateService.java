package cn.nus.zknu.module.edu.service.dataexchange;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.edu.enums.ErrorCodeConstants;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
public class EduDataExchangeTemplateService {

    private final Map<String, EduDataExchangeTemplate> templates;

    public EduDataExchangeTemplateService(List<EduDataExchangeTemplate> templates) {
        this.templates = templates.stream().collect(Collectors.toUnmodifiableMap(
                template -> normalize(template.getBizType()), Function.identity(),
                (left, right) -> {
                    throw new IllegalStateException("重复的数据交换模板: " + left.getBizType());
                }));
    }

    public EduDataExchangeTemplate getTemplate(String bizType) {
        EduDataExchangeTemplate template = templates.get(normalize(bizType));
        if (template == null) {
            throw exception(ErrorCodeConstants.IMPORT_HANDLER_NOT_FOUND, bizType, "latest");
        }
        template.validatePermission();
        return template;
    }

    private static String normalize(String value) {
        return StrUtil.blankToDefault(value, "").trim().toUpperCase(Locale.ROOT);
    }

}
