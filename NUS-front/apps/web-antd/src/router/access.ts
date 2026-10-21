import type {
  AppRouteRecordRaw,
  ComponentRecordType,
  GenerateMenuAndRoutesOptions,
} from '@vben/types';

import { generateAccessible } from '@vben/access';
import { preferences } from '@vben/preferences';
import { useAccessStore } from '@vben/stores';
import { convertServerMenuToRouteRecordStringComponent } from '@vben/utils';

import { BasicLayout, IFrameView } from '#/layouts';

import { composeRoleNavigation, getRoleNav } from './nav-composition';

const forbiddenComponent = () => import('#/views/_core/fallback/forbidden.vue');
const localHiddenRouteNames = new Set([
  'CapacityCourseClassDetail',
  'CapacityCourseDetail',
  'EduAiAnalysis',
  'EduAiChat',
  'EduHomeworkCorrect',
  'EduHomeworkDetail',
  'EduStatisticsStudentList',
  'StudentCapacityReport',
  'StudentCourseDetail',
  'StudentCourseIndicatorDetail',
  'StudentHomeworkFeedback',
  'StudentHomeworkSubmit',
  'StudentNewsDetail',
]);

function filterConflictingServerMenus(
  menus: AppRouteRecordRaw[],
): AppRouteRecordRaw[] {
  return menus
    .filter(
      (menu) =>
        !localHiddenRouteNames.has(
          String(menu.componentName || menu.name || ''),
        ),
    )
    .map((menu) => ({
      ...menu,
      children: menu.children
        ? filterConflictingServerMenus(menu.children)
        : menu.children,
    }));
}

async function generateAccess(options: GenerateMenuAndRoutesOptions) {
  const pageMap: ComponentRecordType = import.meta.glob('../views/**/*.vue');
  const accessStore = useAccessStore();

  const layoutMap: ComponentRecordType = {
    BasicLayout,
    IFrameView,
  };

  return await generateAccessible(preferences.app.accessMode, {
    ...options,
    fetchMenuListAsync: async () => {
      const accessMenus = filterConflictingServerMenus(
        accessStore.accessMenus as AppRouteRecordRaw[],
      );
      const records =
        convertServerMenuToRouteRecordStringComponent(accessMenus);
      // 教师、学生的导航按角色编排（router/nav-composition.ts）：只归组排序，地址与权限不变
      const nav = getRoleNav(options.roles ?? []);
      return nav ? composeRoleNavigation(records, nav) : records;
    },
    // 可以指定没有权限跳转403页面
    forbiddenComponent,
    // 如果 route.meta.menuVisibleWithForbidden = true
    layoutMap,
    pageMap,
  });
}

export { generateAccess };
