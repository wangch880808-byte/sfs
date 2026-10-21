import { defineOverridesPreferences } from '@vben/preferences';

import {
  PLATFORM_SHELL_PREFERENCES,
  PLATFORM_THEME_PREFERENCES,
} from './styles/theme/platform';

/**
 * @description 项目配置文件
 * 只需要覆盖项目中的一部分配置，不需要的配置不用覆盖，会自动使用默认配置
 * !!! 更改配置后请清空缓存，否则可能不生效
 */
export const overridesPreferences = defineOverridesPreferences({
  // overrides
  app: {
    /** 后端路由模式 */
    accessMode: 'backend',
    name: import.meta.env.VITE_APP_TITLE,
    enableRefreshToken: true,
    defaultHomePath: '/workspace',
    defaultAvatar: '/logo.png',
    /** 配色与布局由界面规范统一（styles/theme），不提供逐人调整的偏好设置面板；深浅色切换仍在顶栏 */
    enablePreferences: false,
  },
  breadcrumb: PLATFORM_SHELL_PREFERENCES.breadcrumb,
  footer: {
    /** 默认关闭 footer 页脚，因为有一定遮挡 */
    enable: false,
    fixed: false,
  },
  header: PLATFORM_SHELL_PREFERENCES.header,
  logo: {
    source: '/logo.png',
    sourceDark: '/logo.png',
  },
  copyright: {
    companyName: import.meta.env.VITE_APP_TITLE,
    companySiteLink: '/',
    date: '2026',
  },
  sidebar: PLATFORM_SHELL_PREFERENCES.sidebar,
  tabbar: PLATFORM_SHELL_PREFERENCES.tabbar,
  theme: {
    ...PLATFORM_THEME_PREFERENCES,
    /** 默认深色 */
    mode: 'dark',
  },
  widget: {
    /** 平台只提供简体中文，并统一使用默认时区 */
    languageToggle: false,
    timezone: false,
  },
});
