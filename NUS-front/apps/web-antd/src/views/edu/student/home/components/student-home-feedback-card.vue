<script lang="ts" setup>
import type { FeedbackItem } from '#/views/dashboard/shared/student';

import { EmptyState } from '#/components/empty-state';
import { DashboardPanel, DashboardRow } from '#/views/dashboard/components';
import {
  STUDENT_LINKS,
  useAvailableLink,
} from '#/views/dashboard/shared/links';
import {
  formatFullTime,
  formatPastAction,
} from '#/views/dashboard/shared/time';

/** 学生首页：最新的批改结果（分数用正文色），能对上任务时整行打开成绩反馈 */
withDefaults(
  defineProps<{
    error?: boolean;
    items: FeedbackItem[];
    loading?: boolean;
    now: number;
  }>(),
  { error: false, loading: false },
);

defineEmits<{ retry: [] }>();

const linkOf = useAvailableLink();

function linkOfItem(item: FeedbackItem) {
  return item.homeworkId === undefined
    ? undefined
    : linkOf(STUDENT_LINKS.feedback(item.homeworkId));
}
</script>

<template>
  <DashboardPanel
    :empty="items.length === 0"
    :error="error"
    :loading="loading"
    :more-to="linkOf(STUDENT_LINKS.homeworkList)"
    error-title="批改结果没有读到"
    loading-text="正在读取最新的批改结果…"
    more-text="全部任务"
    title="最新反馈"
    @retry="$emit('retry')"
  >
    <template #empty>
      <EmptyState
        compact
        description="老师批改后，分数和评语会出现在这里。"
        title="还没有批改结果"
      />
    </template>
    <ul>
      <li v-for="item in items" :key="item.key">
        <DashboardRow
          :action="linkOfItem(item) ? '查看反馈' : ''"
          :to="linkOfItem(item)"
        >
          {{ item.homeworkName }}
          <template v-if="item.correctTime" #meta>
            <span :title="formatFullTime(item.correctTime)">
              {{ formatPastAction(item.correctTime, '批改', now) }}
            </span>
          </template>
          <template #aside>
            <span class="score">{{ item.score }}<small>分</small></span>
          </template>
        </DashboardRow>
      </li>
    </ul>
  </DashboardPanel>
</template>

<style scoped>
.score {
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.score small {
  margin-left: 2px;
  font-size: var(--nus-fs-caption);
  font-weight: var(--nus-fw-regular);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-secondary);
}
</style>
