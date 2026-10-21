/**
 * 角色编码：前端只在这里写一次。
 *
 * 取值与后端参数配置 edu.role.* 的默认值一致（管理员 edu.role.admin-codes、教师 edu.role.teacher-code、
 * 学生 edu.role.student-code）。前端只用它们决定界面形态（导航编排、布局、工作台、个人中心）和新建账号时预选的身份角色；
 * 接口能不能调用一律由后端按权限判断，按钮显隐用权限码（hasAccessByCodes），不要再按角色编码判断。
 */

/** 超级管理员 */
export const SUPER_ADMIN_ROLE_CODE = 'super_admin';

/** 管理员：超级管理员、学校教务管理员、学院教务管理员，以及早期库里的 admin */
export const ADMIN_ROLE_CODES: readonly string[] = [
  SUPER_ADMIN_ROLE_CODE,
  'admin',
  'school_admin',
  'college_admin',
];

/** 教师身份角色 */
export const TEACHER_ROLE_CODE = 'teacher';

/** 学生身份角色 */
export const STUDENT_ROLE_CODE = 'student';

/** 账号类型（system_users.user_type）对应的身份角色：1 教师、2 学生 */
export const IDENTITY_ROLE_CODE_BY_USER_TYPE: Readonly<Record<number, string>> =
  {
    1: TEACHER_ROLE_CODE,
    2: STUDENT_ROLE_CODE,
  };
