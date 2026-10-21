package cn.nus.zknu.module.system.enums.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户类型的枚举值
 *
 * @author hyh
 */
@Getter
@AllArgsConstructor
public enum SystemUserTypeEnum {

    /**
     * 管理员
     */
    ADMIN(0),
    /**
     * 教师
     */
    TEACHER(1),
    /**
     * 学生
     */
    STUDENT(2);

    /**
     * 性别
     */
    private final Integer userType;
}
