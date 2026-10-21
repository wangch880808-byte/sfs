<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { IFrame, Page } from '@vben/common-ui';
import { usePreferences } from '@vben/preferences';

import { Spin } from 'ant-design-vue';

import { createMonitorAccessUrl } from '#/api/infra/monitor-access';
import { EmptyState } from '#/components/empty-state';

const { isDark } = usePreferences();
const loading = ref(true); // 是否加载中
const src = ref('');
const failed = ref(false); // 访问会话没有建立成功

/** 初始化 */
onMounted(async () => {
  try {
    src.value = await createMonitorAccessUrl(
      'DRUID',
      isDark.value ? 'DARK' : 'LIGHT',
    );
  } catch {
    // 失败原因已由全局请求提示；页面内显示「无法打开 SQL 监控」的说明
    failed.value = true;
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <Page
    auto-content-height
    description="查看数据库连接池与 SQL 执行情况，数据来自 Druid 监控台。"
    title="SQL 与连接池监控"
  >
    <div class="monitor-frame" :class="{ 'is-dark': isDark }">
      <div v-if="loading" class="monitor-frame__state">
        <Spin tip="正在建立 SQL 监控访问会话…" />
      </div>
      <EmptyState
        v-else-if="failed || !src"
        description="监控服务可能没有启动，或当前账号没有访问权限。刷新页面重试，仍然失败请联系管理员。"
        kind="blocked"
        title="无法打开 SQL 监控"
      />
      <IFrame v-else :src="src" />
    </div>
  </Page>
</template>

<style scoped>
/* 第三方监控页嵌在面板里；深色下用滤镜反色，底色跟随面板 token */
.monitor-frame {
  height: 100%;
  overflow: hidden;
  background: var(--nus-bg-surface);
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius-lg);
}

.monitor-frame__state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.monitor-frame.is-dark :deep(iframe) {
  background: var(--nus-bg-surface);
  filter: invert(0.92) hue-rotate(180deg) saturate(0.88) contrast(0.95);
}
</style>
