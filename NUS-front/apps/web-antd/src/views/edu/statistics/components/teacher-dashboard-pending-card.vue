<script lang="ts" setup>
import type { PendingGroup } from '#/views/dashboard/shared/teacher';

import { computed } from 'vue';

import { EmptyState } from '#/components/empty-state';
import { DashboardPanel, DashboardRow } from '#/views/dashboard/components';
import {
  TEACHER_LINKS,
  useAvailableLink,
} from '#/views/dashboard/shared/links';
import { formatFullTime, formatWaiting } from '#/views/dashboard/shared/time';

/** 教师工作台：待批改的提交按任务归组，等得最久的在前，整行进入该任务的批改页 */
const props = withDefaults(
  defineProps<{
    error?: boolean;
    groups: PendingGroup[];
    loading?: boolean;
    now: number;
    /** 待批改总份数（工作台统计口径） */
    total?: number;
  }>(),
  { error: false, loading: false, total: undefined },
);

defineEmits<{ retry: [] }>();

const VISIBLE = 5;
const linkOf = useAvailableLink();

const visibleGroups = computed(() => props.groups.slice(0, VISIBLE));
const hiddenCount = computed(() => props.groups.length - VISIBLE);
const countText = computed(() => (props.total ? `${props.total} 份` : ''));

function metaOf(group: PendingGroup) {
  return [
    group.courseName,
    group.className,
    group.resubmittedCount > 0 ? `含 ${group.resubmittedCount} 份重新提交` : '',
  ]
    .filter(Boolean)
    .join(' · ');
}
</script>

<template>
  <DashboardPanel
    :count="countText"
    :empty="groups.length === 0"
    :error="error"
    :loading="loading"
    :more-to="linkOf(TEACHER_LINKS.homeworkList)"
    error-title="待批改的提交没有读到"
    loading-text="正在读取待批改的提交…"
    more-text="全部任务"
    title="待批改"
    @retry="$emit('retry')"
  >
    <template #empty>
      <EmptyState
        compact
        description="学生提交后会按任务列在这里，等得最久的排在最前面。"
        title="没有待批改的提交"
      />
    </template>
    <ul>
      <li v-for="group in visibleGroups" :key="group.homeworkId">
        <DashboardRow
          :to="linkOf(TEACHER_LINKS.correct(group.homeworkId))"
          action="批改"
        >
          {{ group.homeworkName }}
          <template #meta>{{ metaOf(group) }}</template>
          <template #aside>
            <span
              v-if="group.oldestSubmitTime"
              :title="`最早一份提交于 ${formatFullTime(group.oldestSubmitTime)}`"
            >
              {{ formatWaiting(group.oldestSubmitTime, now) }}
            </span>
            <span class="pending-count">{{ group.count }} 份</span>
          </template>
        </DashboardRow>
      </li>
    </ul>
    <template v-if="hiddenCount > 0" #footer>
      另有 {{ hiddenCount }} 项任务有待批改的提交，可在全部任务里查看。
    </template>
  </DashboardPanel>
</template>

<style scoped>
.pending-count {
  min-width: 40px;
  font-weight: var(--nus-fw-medium);
  color: var(--nus-text);
  text-align: right;
}
</style>
