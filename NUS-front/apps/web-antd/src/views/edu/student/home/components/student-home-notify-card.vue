<script lang="ts" setup>
import type { SystemNotifyMessageApi } from '#/api/system/notify/message';

import { computed } from 'vue';

import { EmptyState } from '#/components/empty-state';
import { getNotifyTargetUrl, getNotifyTitle } from '#/utils/notify-message';
import { DashboardPanel, DashboardRow } from '#/views/dashboard/components';
import {
  STUDENT_LINKS,
  useAvailableLink,
} from '#/views/dashboard/shared/links';
import { formatFullTime, formatPastTime } from '#/views/dashboard/shared/time';

/**
 * 学生首页：通知（系统推送的提醒）。未读的标题前有主色圆点；点一条标为已读，有对应页面时同时打开。
 * 通知类别（批改、提醒、任务、通知）是中性分类，显示为普通文字。
 */
const props = withDefaults(
  defineProps<{
    error?: boolean;
    loading?: boolean;
    notifications: SystemNotifyMessageApi.NotifyMessage[];
    now: number;
    unreadCount?: number;
  }>(),
  { error: false, loading: false, unreadCount: 0 },
);

const emit = defineEmits<{
  readNotify: [item: SystemNotifyMessageApi.NotifyMessage];
  retry: [];
}>();

const VISIBLE = 6;
const linkOf = useAvailableLink();
const visibleItems = computed(() => props.notifications.slice(0, VISIBLE));

function typeOf(templateCode: string) {
  if (templateCode.includes('corrected')) return '批改';
  if (templateCode.includes('redo') || templateCode.includes('remind')) {
    return '提醒';
  }
  if (templateCode.includes('homework')) return '任务';
  return '通知';
}

function targetOf(item: SystemNotifyMessageApi.NotifyMessage) {
  const url = getNotifyTargetUrl(item);
  return url ? linkOf(url) : undefined;
}

function handleClick(item: SystemNotifyMessageApi.NotifyMessage) {
  emit('readNotify', item);
}
</script>

<template>
  <DashboardPanel
    :count="unreadCount > 0 ? `${unreadCount} 条未读` : ''"
    :empty="notifications.length === 0"
    :error="error"
    :loading="loading"
    :more-to="linkOf(STUDENT_LINKS.notifications)"
    error-title="通知没有读到"
    loading-text="正在读取通知…"
    more-text="全部通知"
    title="通知"
    @retry="$emit('retry')"
  >
    <template #empty>
      <EmptyState
        compact
        description="任务发布、批改结果和老师回复会出现在这里。"
        title="还没有通知"
      />
    </template>
    <ul>
      <li v-for="item in visibleItems" :key="item.id">
        <DashboardRow
          :clickable="!item.readStatus"
          :to="targetOf(item)"
          @click="handleClick(item)"
        >
          <template #leading>
            <span
              :class="{ 'is-unread': !item.readStatus }"
              aria-hidden="true"
              class="notify-dot"
            ></span>
          </template>
          <span :class="{ 'is-read': item.readStatus }">
            <span v-if="!item.readStatus" class="sr-only">未读：</span>
            {{ getNotifyTitle(item) }}
          </span>
          <template #meta>{{ item.templateContent }}</template>
          <template #aside>
            <span class="notify-type">{{ typeOf(item.templateCode) }}</span>
            <span :title="formatFullTime(item.createTime)" class="notify-time">
              {{ formatPastTime(item.createTime, now) }}
            </span>
          </template>
        </DashboardRow>
      </li>
    </ul>
  </DashboardPanel>
</template>

<style scoped>
.notify-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.notify-dot.is-unread {
  background-color: var(--nus-primary);
}

.is-read {
  font-weight: var(--nus-fw-regular);
  color: var(--nus-text-secondary);
}

.notify-type,
.notify-time {
  color: var(--nus-text-tertiary);
}
</style>
