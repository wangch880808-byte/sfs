import type { Preferences } from '@vben/preferences';
import type { DeepPartial } from '@vben/types';

import {
  PLATFORM_SHELL_PREFERENCES,
  PLATFORM_THEME_PREFERENCES,
} from '#/styles/theme/platform';

/** 平台只提供简体中文界面 */
export const PLATFORM_LOCALE = 'zh-CN';

type ShellSection = keyof typeof PLATFORM_SHELL_PREFERENCES;

type PreferenceSnapshot = Pick<
  Preferences,
  'app' | 'copyright' | 'theme' | 'widget' | ShellSection
>;

/** 取出与平台取值不同的字段（只比较平台规定的字段） */
function diffFields<T extends object>(
  current: object,
  platform: T,
): Partial<T> {
  const values = current as Record<string, unknown>;
  const diff: Partial<T> = {};
  for (const key of Object.keys(platform) as (keyof T & string)[]) {
    if (values[key] !== platform[key]) {
      diff[key] = platform[key];
    }
  }
  return diff;
}

/**
 * 计算需要复位的已持久化偏好。
 *
 * 浏览器缓存的偏好优先级高于 overridesPreferences，旧版本留下的值不会被新配置覆盖：
 * - 语言复位为简体中文，语言切换与时区切换入口保持关闭；
 * - 版权链接统一使用平台配置值（旧缓存里可能残留已退役的模板/部署站点）；
 * - 主题的主色、状态色、圆角、字号基准，以及顶栏、侧栏、页签栏、面包屑的尺寸与样式复位为界面规范的取值
 *   （styles/theme/platform.ts），深浅色模式与侧栏折叠等个人状态保留；
 * - 偏好设置面板保持关闭，登录页布局复位为右侧面板：配色与布局由界面规范统一，不再逐人调整。
 */
export function getLegacyPreferenceUpdates(
  current: PreferenceSnapshot,
  companySiteLink: string,
): DeepPartial<Preferences> {
  const updates: DeepPartial<Preferences> = {};
  const app: DeepPartial<Preferences['app']> = {};
  if (current.app.locale !== PLATFORM_LOCALE) {
    app.locale = PLATFORM_LOCALE;
  }
  if (current.app.enablePreferences) {
    app.enablePreferences = false;
  }
  // 登录页布局切换入口已去掉，旧缓存里切换过的布局复位为右侧面板
  if (
    current.app.authPageLayout &&
    current.app.authPageLayout !== 'panel-right'
  ) {
    app.authPageLayout = 'panel-right';
  }
  if (Object.keys(app).length > 0) {
    updates.app = app;
  }
  if (current.copyright.companySiteLink !== companySiteLink) {
    updates.copyright = { companySiteLink };
  }
  if (current.widget.languageToggle || current.widget.timezone) {
    updates.widget = { languageToggle: false, timezone: false };
  }
  const theme = diffFields(current.theme, PLATFORM_THEME_PREFERENCES);
  if (Object.keys(theme).length > 0) {
    updates.theme = theme as DeepPartial<Preferences['theme']>;
  }
  for (const section of Object.keys(
    PLATFORM_SHELL_PREFERENCES,
  ) as ShellSection[]) {
    const diff = diffFields(
      current[section],
      PLATFORM_SHELL_PREFERENCES[section],
    );
    if (Object.keys(diff).length > 0) {
      Object.assign(updates, { [section]: diff });
    }
  }
  return updates;
}

/** 时区 store 的持久化 key，与 @vben/stores 中 pinia 持久化的命名规则一致 */
export function getTimezoneStorageKey(namespace: string) {
  return `${namespace}-core-timezone`;
}
