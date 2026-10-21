import type { RouteRecordStringComponent } from '@vben/types';

import { resolvePersona } from '#/utils/persona';

/**
 * 导航按角色编排（V1.0.1.6）：菜单管理仍决定“谁能看到什么、叫什么”，这里只把菜单归组、排序，页面地址不变。
 *
 * - 编排登记的是菜单的组件名（菜单管理里的“组件名字”，即路由 name）；
 * - 有标题的组是导航里的分组目录，组里只剩一个可见菜单时直接放到顶层；
 * - 编排里没登记的可见菜单归入“其他”，不会丢；
 * - 隐藏的菜单（菜单管理设为不可见的，以及这里列在 hidden 里的）照常注册路由，只是不在导航里出现；
 * - 管理员等其他角色仍按菜单管理里的原树显示，不经过这里；
 * - 一个账号按哪套编排由 utils/persona.ts 统一决定（管理员 > 教师 > 学生）。
 */
export interface NavGroup {
  /** 分组路由的 name 与 path（/nav/<key>）用到，角色内唯一 */
  key: string;
  /** 分组标题；不填则组内菜单直接放到顶层 */
  title?: string;
  icon?: string;
  /** 归入这一组的菜单组件名，按这里的顺序排 */
  items: string[];
}

export interface RoleNav {
  groups: NavGroup[];
  /** 不在导航里出现但仍可访问的菜单：与工作台重复的、移到头像菜单的 */
  hidden?: string[];
}

/** 未登记菜单的归属分组 */
export const OTHER_GROUP_KEY = 'other';

/**
 * 教师：工作台（静态路由，在最前）｜教学｜实训任务｜学情｜留言互动。
 * 教师工作台与 /workspace 重复，从导航里去掉（地址仍可打开）。
 */
export const TEACHER_NAV: RoleNav = {
  groups: [
    {
      icon: 'ep:notebook',
      items: ['CapacityCourse', 'EduCourseResource', 'EduQuestionBank'],
      key: 'teaching',
      title: '教学',
    },
    { items: ['EduHomework'], key: 'homework' },
    {
      icon: 'ep:data-analysis',
      items: ['TeachingProcess', 'TeachingIssue', 'CapacityIndicator'],
      key: 'insight',
      title: '学情',
    },
    { items: ['EduMessage'], key: 'message' },
  ],
  hidden: ['TeacherDashboard'],
};

/**
 * 学生：工作台（静态路由，即学习首页）｜我的课程｜任务中心｜能力｜我的留言｜校园资讯。
 * 学习首页与工作台重复，个人中心移到头像菜单，二者都从导航里去掉（地址仍可打开）。
 */
export const STUDENT_NAV: RoleNav = {
  groups: [
    { items: ['StudentCourse'], key: 'course' },
    { items: ['StudentHomework'], key: 'homework' },
    {
      icon: 'ep:data-analysis',
      items: ['StudentCapacity', 'SkillAssessment'],
      key: 'capacity',
      title: '能力',
    },
    { items: ['StudentMessage'], key: 'message' },
    { items: ['StudentNews'], key: 'news' },
  ],
  hidden: ['StudentHome', 'StudentProfile'],
};

/** 把菜单树拍平成叶子（有页面的菜单），地址补全为绝对路径；目录本身不再保留 */
export function flattenMenuLeaves(
  records: RouteRecordStringComponent[],
): RouteRecordStringComponent[] {
  const leaves: RouteRecordStringComponent[] = [];
  const walk = (items: RouteRecordStringComponent[]) => {
    for (const item of items) {
      if (item.children && item.children.length > 0) {
        walk(item.children);
      } else {
        const { children: _children, ...leaf } = item;
        leaves.push(leaf);
      }
    }
  };
  walk(records);
  return leaves;
}

const isVisible = (record: RouteRecordStringComponent) =>
  !record.meta?.hideInMenu;

/** 复制一条路由记录并合并 meta（RouteRecordRaw 是联合类型，展开后要断言回来） */
const withMeta = (
  record: RouteRecordStringComponent,
  meta: { hideInMenu?: boolean; order?: number },
): RouteRecordStringComponent =>
  ({
    ...record,
    meta: { ...record.meta, ...meta },
  }) as RouteRecordStringComponent;

/**
 * 按角色编排导航。records 是已转换好的路由记录（地址都是绝对路径），返回编排后的顶层记录。
 */
export function composeRoleNavigation(
  records: RouteRecordStringComponent[],
  nav: RoleNav,
): RouteRecordStringComponent[] {
  const leaves = flattenMenuLeaves(records);
  const hiddenNames = new Set(nav.hidden);
  const registered = new Set(nav.groups.flatMap((group) => group.items));
  const byName = new Map(leaves.map((leaf) => [String(leaf.name), leaf]));
  const result: RouteRecordStringComponent[] = [];

  const hide = (leaf: RouteRecordStringComponent) =>
    withMeta(leaf, { hideInMenu: true });

  const pushGroup = (
    group: NavGroup,
    items: RouteRecordStringComponent[],
    order: number,
  ) => {
    const visible = items.filter((item) => isVisible(item));
    if (visible.length === 0) {
      // 组里没有可见菜单：路由照常注册
      result.push(...items);
      return;
    }
    if (!group.title || visible.length === 1) {
      // 无标题或只剩一个可见菜单：放到顶层，按分组顺序排
      for (const item of items) {
        result.push(isVisible(item) ? withMeta(item, { order }) : item);
      }
      return;
    }
    result.push({
      children: items,
      component: '',
      meta: { icon: group.icon, order, title: group.title },
      name: `Nav${group.key.charAt(0).toUpperCase()}${group.key.slice(1)}`,
      path: `/nav/${group.key}`,
    });
  };

  nav.groups.forEach((group, index) => {
    const items = group.items
      .map((name) => byName.get(name))
      .filter((item): item is RouteRecordStringComponent => !!item)
      .map((item) => (hiddenNames.has(String(item.name)) ? hide(item) : item));
    pushGroup(group, items, (index + 1) * 10);
  });

  // 没登记的菜单：隐藏的照常注册；可见的归入“其他”
  const others: RouteRecordStringComponent[] = [];
  for (const leaf of leaves) {
    const name = String(leaf.name);
    if (registered.has(name)) continue;
    if (hiddenNames.has(name)) {
      result.push(hide(leaf));
    } else if (isVisible(leaf)) {
      others.push(leaf);
    } else {
      result.push(leaf);
    }
  }
  pushGroup(
    { icon: 'ep:more', items: [], key: OTHER_GROUP_KEY, title: '其他' },
    others,
    (nav.groups.length + 1) * 10,
  );
  return result;
}

/** 按角色取导航编排（规则见 utils/persona.ts）：教师、学生按编排；管理员等其他角色返回 null，按原树显示 */
export function getRoleNav(roles: readonly string[]): null | RoleNav {
  const { menuSet } = resolvePersona(roles);
  if (menuSet === 'student') return STUDENT_NAV;
  if (menuSet === 'teacher') return TEACHER_NAV;
  return null;
}

/** 只有学生（没有管理员、教师角色）用顶部导航，其余用侧边导航（规则见 utils/persona.ts） */
export function getRoleLayout(
  roles: readonly string[],
): 'header-nav' | 'sidebar-nav' {
  return resolvePersona(roles).layout;
}
