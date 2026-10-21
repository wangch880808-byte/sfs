<script lang="ts" setup>
import type { TodoTask } from '#/views/dashboard/shared/student';

import { computed } from 'vue';

import { getDictLabel } from '@vben/hooks';

import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import { HOMEWORK_RECORD_STATUS } from '#/utils/status';
import {
  getDeadlineTone,
  getStudentTodoStatusMeta,
} from '#/utils/status-dashboard';
import { DashboardPanel, DashboardRow } from '#/views/dashboard/components';
import {
  STUDENT_LINKS,
  useAvailableLink,
} from '#/views/dashboard/shared/links';
import { formatDeadline, formatFullTime } from '#/views/dashboard/shared/time';

/**
 * 学生首页：待完成的任务（未提交、已打回），能做的按截止时间从近到远；
 * 所在等级还没解锁的标为“未解锁”，打开课程详情看解锁条件。
 */
const props = withDefaults(
  defineProps<{
    error?: boolean;
    items: TodoTask[];
    loading?: boolean;
    now: number;
    /** 没提交或被打回、已过截止时间的任务数 */
    overdueCount?: number;
  }>(),
  { error: false, loading: false, overdueCount: 0 },
);

defineEmits<{ retry: [] }>();

const VISIBLE = 6;
const LEVEL_FALLBACK: Record<number, string> = {
  1: '初级',
  2: '中级',
  3: '高级',
};

const linkOf = useAvailableLink();
const visibleItems = computed(() => props.items.slice(0, VISIBLE));
const hiddenCount = computed(() => props.items.length - VISIBLE);
const homeworkListLink = computed(() => linkOf(STUDENT_LINKS.homeworkList));

function levelOf(level: number) {
  return (
    getDictLabel('edu_homework_level', level) || LEVEL_FALLBACK[level] || ''
  );
}

function metaOf(item: TodoTask) {
  return [item.courseName, levelOf(item.homeworkLevel)]
    .filter(Boolean)
    .join(' · ');
}

function linkOfItem(item: TodoTask) {
  return item.locked
    ? linkOf(STUDENT_LINKS.courseDetail(item.courseId))
    : linkOf(STUDENT_LINKS.submit(item.homeworkId, item));
}

function actionOf(item: TodoTask) {
  if (item.locked) return '查看解锁条件';
  return item.status === HOMEWORK_RECORD_STATUS.REDO ? '重新提交' : '去提交';
}

/** 未提交是这里的默认状态，不再重复标出；已打回与未解锁要标出来 */
function showStatus(item: TodoTask) {
  return item.locked || item.status === HOMEWORK_RECORD_STATUS.REDO;
}
</script>

<template>
  <DashboardPanel
    :count="items.length > 0 ? `${items.length} 项` : ''"
    :empty="items.length === 0"
    :error="error"
    :loading="loading"
    :more-to="homeworkListLink"
    error-title="待完成的任务没有读到"
    loading-text="正在读取待完成的任务…"
    more-text="全部任务"
    title="待完成"
    @retry="$emit('retry')"
  >
    <template #empty>
      <EmptyState
        compact
        description="老师发布新任务后会出现在这里，截止早的排在前面。"
        title="没有待完成的任务"
      />
    </template>
    <ul>
      <li v-for="item in visibleItems" :key="item.homeworkId">
        <DashboardRow :action="actionOf(item)" :to="linkOfItem(item)">
          {{ item.homeworkName }}
          <template #meta>{{ metaOf(item) }}</template>
          <template #aside>
            <StatusTag
              v-if="showStatus(item)"
              v-bind="getStudentTodoStatusMeta(item)"
            />
            <span
              v-if="item.deadline"
              :title="formatFullTime(item.deadline)"
              class="deadline"
            >
              <StatusTag
                v-if="
                  !item.locked &&
                  getDeadlineTone(item.deadline, now) !== 'neutral'
                "
                :label="formatDeadline(item.deadline, now)"
                :tone="getDeadlineTone(item.deadline, now)"
              />
              <template v-else>{{
                formatDeadline(item.deadline, now)
              }}</template>
            </span>
          </template>
        </DashboardRow>
      </li>
    </ul>
    <template v-if="hiddenCount > 0 || overdueCount > 0" #footer>
      <template v-if="hiddenCount > 0">
        还有 {{ hiddenCount }} 项待完成，可在全部任务里查看。
      </template>
      <template v-if="overdueCount > 0">
        另有 {{ overdueCount }} 项已过截止时间，不能再提交。
      </template>
    </template>
  </DashboardPanel>
</template>

<style scoped>
.deadline {
  min-width: 96px;
  text-align: right;
}
</style>
