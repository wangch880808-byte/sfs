package cn.nus.zknu.module.system.enums.dept;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 教育组织节点类型。
 */
@Getter
@AllArgsConstructor
public enum DeptTypeEnum {

    SCHOOL("SCHOOL"),
    COLLEGE("COLLEGE"),
    MAJOR("MAJOR");

    private final String type;

    public static boolean isValid(String type) {
        return Arrays.stream(values()).anyMatch(item -> item.type.equals(type));
    }

}
