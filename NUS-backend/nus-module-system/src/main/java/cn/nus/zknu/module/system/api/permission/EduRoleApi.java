package cn.nus.zknu.module.system.api.permission;

import java.util.Set;

/**
 * 教学业务用到的角色编码，统一从参数配置（infra_config 的 edu.role.*）读取。
 * <p>
 * 业务代码判断“是不是管理员、超级管理员、教师、学生”一律走这里，不再各自写死角色编码。
 * 放在 system 模块而不是 edu 模块：capacity 和 system 自身也要用，而模块依赖方向是 infra ← system ← capacity ← edu。
 * <p>
 * 只决定“哪些角色编码算作某一类”，不改变各处原有的判断方式：
 * 当前登录用户的判断经 {@code @ss}（小程序令牌只认学生角色），按用户编号的判断直接查用户的开启角色。
 */
public interface EduRoleApi {

    /** 视为全局管理员（数据范围不受限）的角色编码，逗号分隔 */
    String ADMIN_CODES_KEY = "edu.role.admin-codes";
    /** 仅超级管理员可做的事（查看所有人的导入批次、提交附件）认的角色编码，逗号分隔 */
    String SUPER_ADMIN_CODES_KEY = "edu.role.super-admin-codes";
    /** 教师身份角色编码 */
    String TEACHER_CODE_KEY = "edu.role.teacher-code";
    /** 学生身份角色编码 */
    String STUDENT_CODE_KEY = "edu.role.student-code";

    /** 学院教务管理员（college_admin）自 V1.0.1.8 起按学院限定数据范围，不再是全局管理员 */
    String DEFAULT_ADMIN_CODES = "super_admin,admin,school_admin";
    String DEFAULT_SUPER_ADMIN_CODES = "super_admin";
    String DEFAULT_TEACHER_CODE = "teacher";
    String DEFAULT_STUDENT_CODE = "student";

    /** 管理员角色编码；总是包含超级管理员角色编码 */
    Set<String> getAdminRoleCodes();

    Set<String> getSuperAdminRoleCodes();

    String getTeacherRoleCode();

    String getStudentRoleCode();

    /** 当前登录用户是否管理员（经 {@code @ss} 判断） */
    boolean isCurrentUserAdmin();

    /** 当前登录用户是否超级管理员（经 {@code @ss} 判断） */
    boolean isCurrentUserSuperAdmin();

    /** 当前登录用户是否教师（经 {@code @ss} 判断） */
    boolean isCurrentUserTeacher();

    /** 指定用户是否超级管理员（只看开启的角色） */
    boolean isSuperAdmin(Long userId);

    /** 指定用户是否教师（只看开启的角色） */
    boolean isTeacher(Long userId);

    /** 指定用户是否学生（只看开启的角色） */
    boolean isStudent(Long userId);

}
