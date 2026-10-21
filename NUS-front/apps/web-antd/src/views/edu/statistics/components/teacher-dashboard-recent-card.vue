<script lang="ts" setup>
import type { RecentSubmission } from '#/views/dashboard/shared/teacher';

import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import { getHomeworkRecordStatusMeta } from '#/utils/status';
import { DashboardPanel, DashboardRow } from '#/views/dashboard/components';
import {
  TEACHER_LINKS,
  useAvailableLink,
} from '#/views/dashboard/shared/links';
import { formatFullTime, formatPastTime } from '#/views/dashboard/shared/time';

/** 教师工作台：最近的学生提交，状态按教师视角显示，整行进入该任务的批改页 */
withDefaults(
  defineProps<{
    error?: boolean;
    items: RecentSubmission[];
    loading?: boolean;
    now: number;
  }>(),
  { error: false, loading: false },
);

defineEmits<{ retry: [] }>();

const linkOf = useAvailableLink();
</script>

<template>
  <DashboardPanel
    :empty="items.length === 0"
    :error="error"
    :loading="loading"
    error-title="最近的提交没有读到"
    loading-text="正在读取最近的提交…"
    title="最近提交"
    @retry="$emit('retry')"
  >
    <template #empty>
      <EmptyState
        compact
        description="学生提交任务后，最新的几份会出现在这里。"
        title="还没有学生提交"
      />
    </template>
    <ul>
      <li v-for="item in items" :key="item.recordId">
        <DashboardRow :to="linkOf(TEACHER_LINKS.correct(item.homeworkId))">
          {{ item.studentName }}
          <template #meta>{{ item.homeworkName }}</template>
          <template #aside>
            <StatusTag
              v-bind="getHomeworkRecordStatusMeta(item.status, 'teacher')"
            />
            <span :title="formatFullTime(item.submitTime)" class="submit-time">
              {{ formatPastTime(item.submitTime, now) }}
            </span>
          </template>
        </DashboardRow>
      </li>
    </ul>
  </DashboardPanel>
</template>

<style scoped>
.submit-time {
  min-width: 80px;
  color: var(--nus-text-tertiary);
  text-align: right;
}
</style>
