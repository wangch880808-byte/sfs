<script lang="ts" setup>
import type { AttentionItem } from '#/views/dashboard/shared/admin';

import { EmptyState } from '#/components/empty-state';
import { DashboardPanel, DashboardRow } from '#/views/dashboard/components';
import { useAvailableLink } from '#/views/dashboard/shared/links';

/** 管理员看板：需要关注的事项（待审批的公共课申请、成绩预警），状态色只给圆点 */
withDefaults(
  defineProps<{
    error?: boolean;
    items: AttentionItem[];
    loading?: boolean;
  }>(),
  { error: false, loading: false },
);

defineEmits<{ retry: [] }>();

const linkOf = useAvailableLink();

function linkOfItem(item: AttentionItem) {
  return item.to ? linkOf(item.to) : undefined;
}
</script>

<template>
  <DashboardPanel
    :count="items.length > 0 ? `${items.length} 项` : ''"
    :empty="items.length === 0"
    :error="error"
    :loading="loading"
    error-title="需要关注的事项没有读到"
    loading-text="正在检查审批与成绩…"
    title="需要关注"
    @retry="$emit('retry')"
  >
    <template #empty>
      <EmptyState
        compact
        description="待审批的公共课申请和成绩预警会出现在这里。"
        title="目前没有需要关注的事项"
      />
    </template>
    <ul>
      <li v-for="item in items" :key="item.key">
        <DashboardRow
          :action="linkOfItem(item) ? item.action : ''"
          :to="linkOfItem(item)"
        >
          <template #leading>
            <span
              :style="{ backgroundColor: `var(--nus-status-${item.tone})` }"
              aria-hidden="true"
              class="attention-dot"
            ></span>
          </template>
          {{ item.title }}
          <template #meta>{{ item.description }}</template>
        </DashboardRow>
      </li>
    </ul>
  </DashboardPanel>
</template>

<style scoped>
.attention-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
</style>
