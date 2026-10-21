import type { Preferences } from '@vben/preferences';

import { PREFERENCE_THEME_COLORS } from './palette';

/**
 * 界面规范固定的主题偏好（深浅色模式除外，由用户切换）：preferences.ts 用作默认值；
 * 浏览器缓存的偏好优先级更高，旧缓存里的不同取值在启动时复位（utils/legacy-preferences.ts）。
 * 圆角 0.5rem 让 Tailwind 的 rounded-sm/md/lg/xl 正好对上规范的 4、6、8、12 四档；字号基准保持 16px（rem 基准，
 * 正文 14px 由组件 token 决定）。
 */
export const PLATFORM_THEME_PREFERENCES = {
  ...PREFERENCE_THEME_COLORS,
  builtinType: 'default',
  fontSize: 16,
  radius: '0.5',
  semiDarkHeader: false,
  semiDarkSidebar: false,
} as const satisfies Partial<Preferences['theme']>;

/**
 * 界面规范固定的外壳偏好：顶栏 48、侧栏 220；页签栏用下划线样式、不显示图标；面包屑不显示图标。
 * 偏好设置面板已关闭，这些取值不再逐人调整，旧缓存里的不同取值启动时复位。
 */
export const PLATFORM_SHELL_PREFERENCES = {
  breadcrumb: { showIcon: false },
  header: { height: 48 },
  sidebar: { width: 220 },
  tabbar: { showIcon: false, styleType: 'brisk' },
} as const satisfies {
  breadcrumb: Partial<Preferences['breadcrumb']>;
  header: Partial<Preferences['header']>;
  sidebar: Partial<Preferences['sidebar']>;
  tabbar: Partial<Preferences['tabbar']>;
};
