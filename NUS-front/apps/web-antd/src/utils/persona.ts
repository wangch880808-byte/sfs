import {
  ADMIN_ROLE_CODES,
  STUDENT_ROLE_CODE,
  TEACHER_ROLE_CODE,
} from './role-codes';

/**
 * 按角色决定界面形态（persona）：导航编排、布局、工作台首页、个人中心。
 *
 * 一个账号可以同时有多个角色，按“管理员 > 教师 > 学生”取最高的一个，四件事都按它来，不再各处各判：
 * - 管理员（任一管理员角色）：导航按菜单管理里的原树，侧边导航，工作台显示管理员看板，个人中心用通用资料页；
 * - 教师（没有管理员角色）：教师导航编排，侧边导航，教师工作台，通用资料页；
 * - 学生（只有学生身份，没有管理员和教师角色）：学生导航编排，顶部导航（菜单少、内容区更宽），学习首页，学生资料页；
 * - 以上都不是：原树、侧边导航，工作台提示联系管理员分配角色，通用资料页。
 *
 * 兼任多个角色时，低优先级角色的菜单仍按菜单管理授权显示（教师兼学生时，学生菜单归入导航的“其他”）。
 * 这里只决定界面形态，接口权限由后端判断。
 */
export type PersonaKind = 'admin' | 'none' | 'student' | 'teacher';

export interface Persona {
  kind: PersonaKind;
  /** 导航编排：teacher/student 对应 router/nav-composition 里的编排，null 表示按菜单管理的原树 */
  menuSet: 'student' | 'teacher' | null;
  /** 学生用顶部导航，其余用侧边导航 */
  layout: 'header-nav' | 'sidebar-nav';
  /** 工作台显示的首页 */
  home: PersonaKind;
  /** 头像菜单“个人中心”跳转的路由名 */
  profileRoute: 'Profile' | 'StudentProfile';
}

const PERSONAS: Record<PersonaKind, Persona> = {
  admin: {
    home: 'admin',
    kind: 'admin',
    layout: 'sidebar-nav',
    menuSet: null,
    profileRoute: 'Profile',
  },
  none: {
    home: 'none',
    kind: 'none',
    layout: 'sidebar-nav',
    menuSet: null,
    profileRoute: 'Profile',
  },
  student: {
    home: 'student',
    kind: 'student',
    layout: 'header-nav',
    menuSet: 'student',
    profileRoute: 'StudentProfile',
  },
  teacher: {
    home: 'teacher',
    kind: 'teacher',
    layout: 'sidebar-nav',
    menuSet: 'teacher',
    profileRoute: 'Profile',
  },
};

export function resolvePersona(
  roles: null | readonly string[] | undefined,
): Persona {
  const owned = new Set(roles);
  if (ADMIN_ROLE_CODES.some((code) => owned.has(code))) {
    return PERSONAS.admin;
  }
  if (owned.has(TEACHER_ROLE_CODE)) {
    return PERSONAS.teacher;
  }
  if (owned.has(STUDENT_ROLE_CODE)) {
    return PERSONAS.student;
  }
  return PERSONAS.none;
}
