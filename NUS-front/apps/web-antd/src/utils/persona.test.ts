import { describe, expect, it } from 'vitest';

import { resolvePersona } from './persona';
import {
  ADMIN_ROLE_CODES,
  IDENTITY_ROLE_CODE_BY_USER_TYPE,
  STUDENT_ROLE_CODE,
  TEACHER_ROLE_CODE,
} from './role-codes';

describe('按角色决定界面形态', () => {
  it('只有学生：学生编排、顶部导航、学习首页、学生资料页', () => {
    expect(resolvePersona(['student'])).toEqual({
      home: 'student',
      kind: 'student',
      layout: 'header-nav',
      menuSet: 'student',
      profileRoute: 'StudentProfile',
    });
  });

  it('只有教师：教师编排、侧边导航、教师工作台、通用资料页', () => {
    expect(resolvePersona(['teacher'])).toEqual({
      home: 'teacher',
      kind: 'teacher',
      layout: 'sidebar-nav',
      menuSet: 'teacher',
      profileRoute: 'Profile',
    });
  });

  it.each(ADMIN_ROLE_CODES)('管理员 %s：原树、侧边导航、管理员看板', (code) => {
    expect(resolvePersona([code])).toEqual({
      home: 'admin',
      kind: 'admin',
      layout: 'sidebar-nav',
      menuSet: null,
      profileRoute: 'Profile',
    });
  });

  it('教师兼学生：按教师（不再按学生编排，也不用顶部导航）', () => {
    const persona = resolvePersona(['student', 'teacher']);
    expect(persona.kind).toBe('teacher');
    expect(persona.menuSet).toBe('teacher');
    expect(persona.layout).toBe('sidebar-nav');
    expect(persona.home).toBe('teacher');
    expect(persona.profileRoute).toBe('Profile');
  });

  it('教师兼学院教务管理员：按管理员（原树，不再套用教师编排）', () => {
    const persona = resolvePersona(['teacher', 'college_admin']);
    expect(persona.kind).toBe('admin');
    expect(persona.menuSet).toBeNull();
    expect(persona.home).toBe('admin');
  });

  it('超级管理员兼教师：按管理员', () => {
    expect(resolvePersona(['teacher', 'super_admin']).kind).toBe('admin');
    expect(resolvePersona(['super_admin', 'teacher']).home).toBe('admin');
  });

  it('学生兼学校教务管理员：按管理员，用侧边导航', () => {
    const persona = resolvePersona(['student', 'school_admin']);
    expect(persona.kind).toBe('admin');
    expect(persona.layout).toBe('sidebar-nav');
    expect(persona.profileRoute).toBe('Profile');
  });

  it('没有可识别的角色：原树、侧边导航、工作台提示分配角色', () => {
    for (const roles of [[], ['guest'], null, undefined]) {
      expect(resolvePersona(roles)).toEqual({
        home: 'none',
        kind: 'none',
        layout: 'sidebar-nav',
        menuSet: null,
        profileRoute: 'Profile',
      });
    }
  });

  it('角色编码区分大小写，与后端一致', () => {
    expect(resolvePersona(['ADMIN']).kind).toBe('none');
  });
});

describe('角色编码常量', () => {
  it('管理员集合与后端 edu.role.admin-codes 默认值一致', () => {
    expect([...ADMIN_ROLE_CODES]).toEqual([
      'super_admin',
      'admin',
      'school_admin',
      'college_admin',
    ]);
  });

  it('账号类型对应身份角色：1 教师、2 学生', () => {
    expect(IDENTITY_ROLE_CODE_BY_USER_TYPE[1]).toBe(TEACHER_ROLE_CODE);
    expect(IDENTITY_ROLE_CODE_BY_USER_TYPE[2]).toBe(STUDENT_ROLE_CODE);
  });
});
