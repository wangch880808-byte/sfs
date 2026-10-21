<script lang="ts" setup>
import type { TaskInfo } from '#/views/dashboard/shared/teacher';

import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import { getDeadlineTone } from '#/utils/status-dashboard';
import { DashboardPanel, DashboardRow } from '#/views/dashboard/components';
import {
  TEACHER_LINKS,
  useAvailableLink,
} from '#/views/dashboard/shared/links';
import { formatDeadline, formatFullTime } from '#/views/dashboard/shared/time';

/** 教师工作台：已发布、还没截止的任务按截止时间从近到远，3 天内截止的用状态点提醒 */
withDefaults(
  defineProps<{
    error?: boolean;
    loading?: boolean;
    now: number;
    tasks: TaskInfo[];
  }>(),
  { error: false, loading: false },
);

defineEmits<{ retry: [] }>();

const linkOf = useAvailableLink();

function metaOf(task: TaskInfo) {
  return [task.courseName, task.className].filter(Boolean).join(' · ');
}

function progressOf(task: TaskInfo) {
  if (task.studentCount === undefined || task.submittedCount === undefined) {
    return '';
  }
  return `已提交 ${task.submittedCount}/${task.studentCount}`;
}
</script>

<template>
  <DashboardPanel
    :empty="tasks.length === 0"
    :error="error"
    :loading="loading"
    :more-to="linkOf(TEACHER_LINKS.homeworkList)"
    error-title="任务列表没有读到"
    loading-text="正在读取任务的截止时间…"
    more-text="全部任务"
    title="即将截止"
    @retry="$emit('retry')"
  >
    <template #empty>
      <EmptyState
        compact
        description="已发布任务的截止时间会按先后列在这里。"
        title="近期没有要截止的任务"
      />
    </template>
    <ul>
      <li v-for="task in tasks" :key="task.homeworkId">
        <DashboardRow
          :to="linkOf(TEACHER_LINKS.homeworkDetail(task.homeworkId))"
          action="查看"
        >
          {{ task.homeworkName }}
          <template #meta>{{ metaOf(task) }}</template>
          <template #aside>
            <span v-if="progressOf(task)">{{ progressOf(task) }}</span>
            <span :title="formatFullTime(task.deadline)" class="deadline">
              <StatusTag
                v-if="getDeadlineTone(task.deadline, now) !== 'neutral'"
                :label="formatDeadline(task.deadline, now)"
                :tone="getDeadlineTone(task.deadline, now)"
              />
              <template v-else>{{
                formatDeadline(task.deadline, now)
              }}</template>
            </span>
          </template>
        </DashboardRow>
      </li>
    </ul>
  </DashboardPanel>
</template>

<style scoped>
.deadline {
  min-width: 112px;
  text-align: right;
}
</style>
