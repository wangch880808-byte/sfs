package cn.nus.zknu.module.edu.util;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 解析逗号分隔的用户 ID 串，例如班课绑定请求里的助教 ID 串。
 * <p>
 * 可能有空格、全角逗号、空段或非数字片段：逐段去掉空白后只保留正整数，按出现顺序去重。
 * 课程团队与班课助教自 V1.0.1.7 起存放在 capacity_course_member、edu_class_course_assistant，
 * 读取一律走关联表，不再解析旧的逗号串列；V18 回填旧列时使用相同的规则。
 */
public final class EduIdCsvUtils {

    private static final Pattern SEPARATOR = Pattern.compile("[,，]");
    private static final Pattern DIGITS = Pattern.compile("\\d{1,18}");

    private EduIdCsvUtils() {
    }

    public static Set<Long> parseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptySet();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String token : SEPARATOR.split(csv)) {
            String value = token.strip();
            if (!DIGITS.matcher(value).matches()) {
                continue;
            }
            long id = Long.parseLong(value);
            if (id > 0) {
                ids.add(id);
            }
        }
        return ids;
    }

}
