package cn.nus.zknu.module.edu.service.dataexchange;

import cn.hutool.json.JSONUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EduImportSnapshotUtils {

    private EduImportSnapshotUtils() {
    }

    public static boolean matches(String expectedJson, Object currentSnapshot) {
        if (expectedJson == null || currentSnapshot == null) {
            return expectedJson == null && currentSnapshot == null;
        }
        return deepEquals(JSONUtil.parse(expectedJson), JSONUtil.parse(JSONUtil.toJsonStr(currentSnapshot)));
    }

    private static boolean deepEquals(Object expected, Object current) {
        if (expected instanceof Number expectedNumber && current instanceof Number currentNumber) {
            return new BigDecimal(expectedNumber.toString()).compareTo(new BigDecimal(currentNumber.toString())) == 0;
        }
        if (expected instanceof Map<?, ?> expectedMap && current instanceof Map<?, ?> currentMap) {
            if (!expectedMap.keySet().equals(currentMap.keySet())) {
                return false;
            }
            return expectedMap.entrySet().stream().allMatch(entry ->
                    deepEquals(entry.getValue(), currentMap.get(entry.getKey())));
        }
        if (expected instanceof List<?> expectedList && current instanceof List<?> currentList) {
            if (expectedList.size() != currentList.size()) {
                return false;
            }
            for (int index = 0; index < expectedList.size(); index++) {
                if (!deepEquals(expectedList.get(index), currentList.get(index))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(expected, current);
    }

}
