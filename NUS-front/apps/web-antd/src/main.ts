import {
  initPreferences,
  preferences,
  updatePreferences,
} from '@vben/preferences';
import { unmountGlobalLoading } from '@vben/utils';

import { overridesPreferences } from './preferences';
import {
  getLegacyPreferenceUpdates,
  getTimezoneStorageKey,
} from './utils/legacy-preferences';

/**
 * 应用初始化完成之后再进行页面加载渲染
 */
async function initApplication() {
  // name用于指定项目唯一标识
  // 用于区分不同项目的偏好设置以及存储数据的key前缀以及其他一些需要隔离的数据
  const env = import.meta.env.PROD ? 'prod' : 'dev';
  const appVersion = import.meta.env.VITE_APP_VERSION;
  const namespace = `${import.meta.env.VITE_APP_NAMESPACE}-${appVersion}-${env}`;

  // app偏好设置初始化
  await initPreferences({
    namespace,
    overrides: overridesPreferences,
  });

  // 旧浏览器偏好会覆盖新默认值：复位语言、语言/时区切换开关、已退役的版权链接、主题配色与偏好设置面板
  const legacyUpdates = getLegacyPreferenceUpdates(
    preferences,
    overridesPreferences.copyright?.companySiteLink ?? '/',
  );
  if (Object.keys(legacyUpdates).length > 0) {
    updatePreferences(legacyUpdates);
  }
  // 平台固定使用默认时区：在初始化 pinia 之前清除旧版本持久化的自选时区
  localStorage.removeItem(getTimezoneStorageKey(namespace));

  // 启动应用并挂载
  // vue应用主要逻辑及视图
  const { bootstrap } = await import('./bootstrap');
  await bootstrap(namespace);

  // 移除并销毁loading
  unmountGlobalLoading();
}

initApplication();
