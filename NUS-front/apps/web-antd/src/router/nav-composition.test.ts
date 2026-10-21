import type { RouteRecordStringComponent } from '@vben/types';

import { describe, expect, it } from 'vitest';

import {
  composeRoleNavigation,
  getRoleLayout,
  getRoleNav,
  STUDENT_NAV,
  TEACHER_NAV,
} from './nav-composition';

const page = (
  name: string,
  path: string,
  extra: Partial<RouteRecordStringComponent['meta']> = {},
): RouteRecordStringComponent => ({
  component: `views/${name}`,
  meta: { title: name, ...extra },
  name,
  path,
});

const dir = (
  name: string,
  path: string,
  children: RouteRecordStringComponent[],
): RouteRecordStringComponent => ({
  children,
  component: '',
  meta: { title: name },
  name,
  path,
});

/** 本地演示库里教师能看到的菜单（已转换成路由记录，地址是绝对路径），外加一个没登记的班级管理 */
const teacherRecords = () => [
  dir('教务组织', '/edu', [
    page('EduClass', '/edu/class'),
    page('EduMessage', '/edu/message'),
  ]),
  dir('能力培养', '/capacity', [
    page('CapacityIndicator', '/capacity/indicator'),
    dir('实训课程', '/capacity/course_manager', [
      page('CapacityCourse', '/capacity/course_manager/course'),
      page('EduCourseResource', '/capacity/course_manager/course-resource'),
    ]),
    dir('实训任务', '/capacity/homework_manager', [
      page('EduHomework', '/capacity/homework_manager/homework'),
      page('AI评阅', '/capacity/homework_manager/ai-review', {
        hideInMenu: true,
      }),
      page('EduQuestionBank', '/capacity/homework_manager/question-bank'),
    ]),
    page('TeacherDashboard', '/capacity/teacher-dashboard'),
  ]),
];

const names = (records: RouteRecordStringComponent[]) =>
  records.map((record) => String(record.name));

const allLeafPaths = (records: RouteRecordStringComponent[]): string[] =>
  records.flatMap((record) =>
    record.children?.length ? allLeafPaths(record.children) : [record.path],
  );

describe('教师导航编排', () => {
  const result = composeRoleNavigation(teacherRecords(), TEACHER_NAV);

  it('教学是分组目录，组内按编排顺序；只剩一项的组直接放顶层', () => {
    const teaching = result.find((record) => record.name === 'NavTeaching');
    expect(teaching?.path).toBe('/nav/teaching');
    expect(teaching?.meta?.title).toBe('教学');
    expect(names(teaching?.children ?? [])).toEqual([
      'CapacityCourse',
      'EduCourseResource',
      'EduQuestionBank',
    ]);
    // 学情组在演示库里只有评价指标管理一项：放顶层
    expect(
      result.find((record) => record.name === 'NavInsight'),
    ).toBeUndefined();
    const indicator = result.find(
      (record) => record.name === 'CapacityIndicator',
    );
    expect(indicator?.meta?.order).toBe(30);
  });

  it('页面地址不变，隐藏菜单照常注册路由', () => {
    const before = allLeafPaths(teacherRecords()).toSorted();
    expect(allLeafPaths(result).toSorted()).toEqual(before);
    const aiReview = result.find((record) => record.name === 'AI评阅');
    expect(aiReview?.meta?.hideInMenu).toBe(true);
  });

  it('教师工作台与工作台重复：仍可访问，但不在导航里', () => {
    const dashboard = result.find(
      (record) => record.name === 'TeacherDashboard',
    );
    expect(dashboard?.path).toBe('/capacity/teacher-dashboard');
    expect(dashboard?.meta?.hideInMenu).toBe(true);
  });

  it('没登记的菜单归入“其他”，只有一项时放顶层，排在最后', () => {
    const cls = result.find((record) => record.name === 'EduClass');
    expect(cls?.path).toBe('/edu/class');
    const visibleOrders = result
      .filter((record) => !record.meta?.hideInMenu)
      .map((record) => [String(record.name), record.meta?.order]);
    expect(visibleOrders).toEqual([
      ['NavTeaching', 10],
      ['EduHomework', 20],
      ['CapacityIndicator', 30],
      ['EduMessage', 40],
      ['EduClass', 50],
    ]);
  });

  it('多个没登记的菜单收进“其他”分组', () => {
    const records = [
      ...teacherRecords(),
      dir('学生学习', '/student', [
        page('StudentNews', '/student/news'),
        page('StudentCourse', '/student/course'),
      ]),
    ];
    const other = composeRoleNavigation(records, TEACHER_NAV).find(
      (record) => record.name === 'NavOther',
    );
    expect(other?.meta?.title).toBe('其他');
    expect(names(other?.children ?? [])).toEqual([
      'EduClass',
      'StudentNews',
      'StudentCourse',
    ]);
  });
});

describe('学生导航编排', () => {
  const studentRecords = () => [
    dir('学生学习', '/student', [
      page('StudentNews', '/student/news'),
      page('StudentHome', '/student/home'),
      page('StudentCourse', '/student/course'),
      page('StudentHomework', '/student/homework'),
      page('SkillAssessment', '/student/skill-assessment'),
      page('StudentCapacity', '/student/capacity'),
      page('StudentProfile', '/student/profile'),
      page('StudentMessage', '/student/message'),
    ]),
  ];

  it('课程、任务、能力（分组）、留言、资讯依次排列；学习首页与个人中心不在导航里但可访问', () => {
    const result = composeRoleNavigation(studentRecords(), STUDENT_NAV);
    const visible = result.filter((record) => !record.meta?.hideInMenu);
    expect(names(visible)).toEqual([
      'StudentCourse',
      'StudentHomework',
      'NavCapacity',
      'StudentMessage',
      'StudentNews',
    ]);
    expect(
      names(
        visible.find((record) => record.name === 'NavCapacity')?.children ?? [],
      ),
    ).toEqual(['StudentCapacity', 'SkillAssessment']);
    for (const name of ['StudentHome', 'StudentProfile']) {
      const record = result.find((item) => item.name === name);
      expect(record?.meta?.hideInMenu, name).toBe(true);
    }
    expect(allLeafPaths(result).toSorted()).toEqual(
      allLeafPaths(studentRecords()).toSorted(),
    );
  });
});

describe('按角色取编排与布局', () => {
  it('学生与教师按角色编排，管理员按原树', () => {
    expect(getRoleNav(['student'])).toBe(STUDENT_NAV);
    expect(getRoleNav(['teacher'])).toBe(TEACHER_NAV);
    expect(getRoleNav(['teacher', 'super_admin'])).toBeNull();
    expect(getRoleNav(['super_admin'])).toBeNull();
    expect(getRoleNav(['college_admin'])).toBeNull();
  });

  it('兼任多个角色按“管理员 > 教师 > 学生”取编排', () => {
    expect(getRoleNav(['student', 'teacher'])).toBe(TEACHER_NAV);
    expect(getRoleNav(['teacher', 'college_admin'])).toBeNull();
    expect(getRoleNav(['teacher', 'school_admin'])).toBeNull();
    expect(getRoleNav(['student', 'school_admin'])).toBeNull();
  });

  it('只有学生用顶部导航', () => {
    expect(getRoleLayout(['student'])).toBe('header-nav');
    expect(getRoleLayout(['teacher'])).toBe('sidebar-nav');
    expect(getRoleLayout(['student', 'teacher'])).toBe('sidebar-nav');
    expect(getRoleLayout(['student', 'school_admin'])).toBe('sidebar-nav');
    expect(getRoleLayout([])).toBe('sidebar-nav');
  });
});
