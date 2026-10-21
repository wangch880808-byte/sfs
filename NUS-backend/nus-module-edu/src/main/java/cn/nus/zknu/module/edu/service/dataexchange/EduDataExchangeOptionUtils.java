package cn.nus.zknu.module.edu.service.dataexchange;

import cn.hutool.core.util.StrUtil;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EduDataExchangeOptionUtils {

    private static final Pattern CODE_SUFFIX_PATTERN = Pattern.compile(".*\\[([^\\[\\]]+)]$");

    private EduDataExchangeOptionUtils() {
    }

    public static List<String> departmentOptions(Collection<DeptDO> departments, String requiredType) {
        Map<Long, DeptDO> departmentsById = new HashMap<>();
        for (DeptDO department : departments) {
            if (department.getId() != null) {
                departmentsById.put(department.getId(), department);
            }
        }
        return departments.stream()
                .filter(department -> StrUtil.isNotBlank(department.getDeptCode()))
                .filter(department -> requiredType == null || requiredType.equals(department.getDeptType()))
                .map(department -> departmentOption(department, departmentsById))
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(EduDataExchangeOptionUtils::optionPath)
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    public static String parseSelectedCode(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String normalized = value.trim();
        Matcher matcher = CODE_SUFFIX_PATTERN.matcher(normalized);
        return matcher.matches() ? matcher.group(1).trim() : normalized;
    }

    private static String departmentOption(DeptDO department, Map<Long, DeptDO> departmentsById) {
        LinkedList<String> names = new LinkedList<>();
        Set<Long> visited = new HashSet<>();
        DeptDO current = department;
        while (current != null && current.getId() != null && visited.add(current.getId())) {
            names.addFirst(StrUtil.blankToDefault(current.getName(), current.getDeptCode()));
            current = departmentsById.get(current.getParentId());
        }
        if (names.size() <= 1) {
            return null;
        }
        names.removeFirst();
        return String.join(" / ", names) + " [" + department.getDeptCode() + "]";
    }

    private static String optionPath(String option) {
        int codeStart = option.lastIndexOf(" [");
        return codeStart < 0 ? option : option.substring(0, codeStart);
    }

}
