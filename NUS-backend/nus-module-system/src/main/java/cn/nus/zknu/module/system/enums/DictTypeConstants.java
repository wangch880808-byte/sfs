package cn.nus.zknu.module.system.enums;

/**
 * System 字典类型的枚举类
 *
 * @author hyh
 */
public interface DictTypeConstants {

    String USER_TYPE = "user_type"; // 用户类型
    String COMMON_STATUS = "common_status"; // 系统状态

    // ========== SYSTEM 模块 ==========

    String SYSTEM_USER_TYPE = "system_user_type"; // 系统用户身份：教务管理、教师、学生
    String USER_SEX = "system_user_sex"; // 用户性别
    String DATA_SCOPE = "system_data_scope"; // 数据范围

    String LOGIN_TYPE = "system_login_type"; // 登录日志的类型
    String LOGIN_RESULT = "system_login_result"; // 登录结果

}
