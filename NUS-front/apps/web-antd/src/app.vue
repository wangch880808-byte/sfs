<script lang="ts" setup>
import { computed } from 'vue';

import { preferences, usePreferences } from '@vben/preferences';

import { usePreferredReducedMotion } from '@vueuse/core';
import { App, ConfigProvider, theme } from 'ant-design-vue';

import { antdLocale } from '#/locales';
import { buildAntdTheme } from '#/styles/theme/antd';

defineOptions({ name: 'App' });

const { isDark } = usePreferences();
const reducedMotion = usePreferredReducedMotion();

const tokenTheme = computed(() => {
  const algorithm = isDark.value
    ? [theme.darkAlgorithm]
    : [theme.defaultAlgorithm];

  // antd 紧凑模式算法
  if (preferences.app.compact) {
    algorithm.push(theme.compactAlgorithm);
  }

  // 颜色与尺寸取界面规范（styles/theme），与 vben 外壳、设计 token 是同一套
  const { components, token } = buildAntdTheme(
    isDark.value ? 'dark' : 'light',
    { reducedMotion: reducedMotion.value === 'reduce' },
  );

  return {
    algorithm,
    components,
    token,
  };
});
</script>

<template>
  <!-- 两个汉字的按钮不自动插入空格（“返 回”“提 交”） -->
  <ConfigProvider
    :auto-insert-space-in-button="false"
    :locale="antdLocale"
    :theme="tokenTheme"
  >
    <App>
      <RouterView />
    </App>
  </ConfigProvider>
</template>
