<script lang="ts" setup>
import type { EduHomeworkRecordApi } from '#/api/edu/homework-record';

import { computed } from 'vue';

import { History } from '@vben/icons';

import { Button } from 'ant-design-vue';

import { StatusTag } from '#/components/status-tag';
import { formatDeadlineTime } from '#/utils/homework-deadline';
import {
  getHomeworkRecordStatusMeta,
  HOMEWORK_RECORD_STATUS,
} from '#/utils/status';
import { hasVisibleScoreStatus, isRedoStatus } from '#/utils/status-grading';
import { getScoreLabel } from '#/views/edu/homework/components/grading-display';

import { getStudentName } from './use-grading-actions';

/** 右栏页眉：学生、状态与一行元信息（学号、提交时间、重做截止、成绩、班级排名），右侧是成绩轨迹开关 */
const props = defineProps<{
  canViewHistory: boolean;
  record: EduHomeworkRecordApi.HomeworkRecord;
}>();

const historyOpen = defineModel<boolean>('historyOpen', { required: true });

const showScore = computed(
  () =>
    hasVisibleScoreStatus(props.record.status) &&
    props.record.score !== null &&
    props.record.score !== undefined,
);

const rankText = computed(() => {
  const { classRank, rankTotalStudents, status } = props.record;
  return status === HOMEWORK_RECORD_STATUS.CORRECTED &&
    classRank &&
    rankTotalStudents
    ? `${classRank} / ${rankTotalStudents}`
    : '待批改后生成';
});
</script>

<template>
  <header class="grading-header">
    <div class="grading-header__identity">
      <div class="grading-header__heading">
        <h2 class="grading-header__name">{{ getStudentName(record) }}</h2>
        <StatusTag
          v-bind="getHomeworkRecordStatusMeta(record.status, 'teacher')"
        />
      </div>
      <!-- 元信息换行时，外层裁掉每行开头的分隔点 -->
      <div class="grading-meta">
        <div class="grading-meta__items">
          <span v-if="record.studentNo" class="grading-meta__item">
            {{ record.studentNo }}
          </span>
          <span class="grading-meta__item">
            {{
              record.submitTime
                ? `提交于 ${formatDeadlineTime(record.submitTime)}`
                : '未提交'
            }}
          </span>
          <span
            v-if="isRedoStatus(record.status) && record.redoDeadline"
            class="grading-meta__item"
          >
            {{ `重做截止 ${formatDeadlineTime(record.redoDeadline)}` }}
          </span>
          <span v-if="showScore" class="grading-meta__item">
            {{ getScoreLabel(record.status) }}
            <span class="grading-meta__value">{{ record.score }}</span>
          </span>
          <span class="grading-meta__item">{{ `班级排名 ${rankText}` }}</span>
        </div>
      </div>
    </div>
    <Button
      v-if="canViewHistory"
      :aria-expanded="historyOpen"
      size="small"
      @click="historyOpen = !historyOpen"
    >
      <template #icon>
        <History aria-hidden="true" class="size-4" />
      </template>
      {{ historyOpen ? '收起成绩轨迹' : '查看成绩轨迹' }}
    </Button>
  </header>
</template>

<style scoped>
.grading-header {
  display: flex;
  flex: none;
  gap: var(--nus-space-4);
  align-items: flex-start;
  justify-content: space-between;
  padding: var(--nus-space-4) var(--nus-space-5);
  border-bottom: 1px solid var(--nus-border-subtle);
}

.grading-header__identity {
  min-width: 0;
}

.grading-header__heading {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-1) var(--nus-space-3);
  align-items: center;
}

.grading-header__name {
  margin: 0;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
}

.grading-meta {
  margin-top: var(--nus-space-1);
  overflow: hidden;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

/* 每项前面带一个分隔点，整行左移一个点的宽度，被外层裁掉，换行后行首也不会出现分隔点 */
.grading-meta__items {
  display: flex;
  flex-wrap: wrap;
  margin-left: calc(var(--nus-space-5) * -1);
}

.grading-meta__item::before {
  display: inline-block;
  width: var(--nus-space-5);
  text-align: center;
  content: '·';
}

.grading-meta__value {
  font-weight: var(--nus-fw-medium);
  font-variant-numeric: tabular-nums;
  color: var(--nus-text);
}
</style>
