<script setup lang="ts">
import { ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { ExternalLink } from '@vben/icons';
import { usePreferences } from '@vben/preferences';

import { Button, message } from 'ant-design-vue';

import { createMonitorAccessUrl } from '#/api/infra/monitor-access';

const loading = ref(false);
const { isDark } = usePreferences();
const SKYWALKING_WINDOW_NAME = 'nus-skywalking-monitor';
let skywalkingWindow: null | Window = null;

async function createThemedUrl(options?: { silentError?: boolean }) {
  const ticketUrl = await createMonitorAccessUrl(
    'SKYWALKING',
    isDark.value ? 'DARK' : 'LIGHT',
    options,
  );
  return new URL(ticketUrl, window.location.origin).toString();
}

async function openSkyWalking() {
  const popup = window.open('about:blank', SKYWALKING_WINDOW_NAME);
  if (!popup) {
    message.warning('浏览器阻止了新窗口，请允许本站打开弹窗');
    return;
  }
  skywalkingWindow = popup;
  loading.value = true;
  try {
    popup.location.href = await createThemedUrl();
  } catch {
    // 失败原因已由全局请求提示，这里只关掉空白窗口
    popup.close();
  } finally {
    loading.value = false;
  }
}

watch(isDark, async () => {
  if (!skywalkingWindow) {
    return;
  }
  // Reuse the named window while the theme click still carries user activation.
  const popup = window.open('', SKYWALKING_WINDOW_NAME);
  if (!popup) {
    message.warning(
      'SkyWalking 控制台没有跟着切换主题：浏览器阻止了窗口。可以重新打开控制台。',
    );
    return;
  }
  try {
    // 切换主题失败由下面自己提示（说明控制台还是旧主题、怎么办），不再弹全局提示
    popup.location.href = await createThemedUrl({ silentError: true });
    skywalkingWindow = popup;
  } catch {
    message.error('SkyWalking 控制台没有跟着切换主题，可以重新打开控制台。');
  }
});
</script>

<template>
  <Page
    auto-content-height
    description="链路追踪在 SkyWalking 控制台里查看，会在新窗口打开。"
    title="链路追踪"
  >
    <div class="flex h-full min-h-80 items-center justify-center">
      <Button type="primary" :loading="loading" @click="openSkyWalking">
        <template #icon>
          <ExternalLink class="size-4" />
        </template>
        打开 SkyWalking 控制台
      </Button>
    </div>
  </Page>
</template>
