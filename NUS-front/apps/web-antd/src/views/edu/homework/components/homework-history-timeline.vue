<script lang="ts" setup>
import type { EduHomeworkRecordApi } from '#/api/edu/homework-record';

import { Alert, Button, Spin } from 'ant-design-vue';

import { EmptyState } from '#/components/empty-state';
import { formatDeadlineTime } from '#/utils/homework-deadline';
import {
  getHomeworkHistoryCommentLabel,
  getHomeworkHistoryEventLabel,
  getHomeworkHistoryScoreLabel,
  getHomeworkRecordStatusLabel,
} from '#/utils/homework-history';

/**
 * 成绩轨迹（V1.0.1.6 界面规范）：教师批改页与学生反馈页共用。
 * 一条事件一行：中性圆点、事件名与时间，下面按“标签 值”对齐写状态变化、成绩、重做截止、评语与操作人。
 */
defineProps<{
  error?: boolean;
  items: EduHomeworkRecordApi.HistoryItem[];
  loading?: boolean;
}>();

defineEmits<{ retry: [] }>();

function hasValue(value: unknown) {
  return value !== null && value !== undefined;
}

function getStatusChange(item: EduHomeworkRecordApi.HistoryItem) {
  return hasValue(item.fromStatus)
    ? `${getHomeworkRecordStatusLabel(item.fromStatus)} → ${getHomeworkRecordStatusLabel(item.toStatus)}`
    : getHomeworkRecordStatusLabel(item.toStatus);
}
</script>

<template>
  <div class="history">
    <div v-if="loading" class="history__loading" role="status">
      <Spin size="small" />
      <span>正在读取成绩轨迹…</span>
    </div>
    <Alert
      v-else-if="error"
      message="成绩轨迹没有读取成功，请稍后重试。"
      show-icon
      type="warning"
    >
      <template #action>
        <Button size="small" @click="$emit('retry')">重试</Button>
      </template>
    </Alert>
    <EmptyState
      v-else-if="items.length === 0"
      compact
      description="提交、批改或打回后会记录在这里。"
      title="还没有成绩轨迹"
    />
    <ol v-else class="history__list">
      <li v-for="(item, index) in items" :key="index" class="history__item">
        <span aria-hidden="true" class="history__dot"></span>
        <div class="history__head">
          <span class="history__event">
            {{ getHomeworkHistoryEventLabel(item.eventType) }}
          </span>
          <span class="history__time">
            {{ formatDeadlineTime(item.createTime) }}
          </span>
        </div>
        <dl class="history__details">
          <div v-if="hasValue(item.toStatus)" class="history__row">
            <dt>状态</dt>
            <dd>{{ getStatusChange(item) }}</dd>
          </div>
          <div v-if="hasValue(item.score)" class="history__row">
            <dt>{{ getHomeworkHistoryScoreLabel(item.eventType) }}</dt>
            <dd class="history__score">{{ item.score }} 分</dd>
          </div>
          <div v-if="item.redoDeadline" class="history__row">
            <dt>重做截止</dt>
            <dd>{{ formatDeadlineTime(item.redoDeadline) }}</dd>
          </div>
          <div v-if="item.comment" class="history__row">
            <dt>{{ getHomeworkHistoryCommentLabel(item.eventType) }}</dt>
            <dd class="history__comment">{{ item.comment }}</dd>
          </div>
          <div v-if="item.operatorName" class="history__row">
            <dt>操作人</dt>
            <dd>{{ item.operatorName }}</dd>
          </div>
        </dl>
      </li>
    </ol>
  </div>
</template>

<style scoped>
.history__loading {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.history__list {
  padding: 0;
  margin: 0;
  list-style: none;
}

/* 圆点之间用 1px 竖线连起来，最后一条不画线 */
.history__item {
  position: relative;
  padding: 0 0 var(--nus-space-4) var(--nus-space-5);
}

.history__item:last-child {
  padding-bottom: 0;
}

.history__item::before {
  position: absolute;
  top: 20px;
  bottom: -4px;
  left: 3px;
  width: 1px;
  content: '';
  background: var(--nus-border);
}

.history__item:last-child::before {
  display: none;
}

.history__dot {
  position: absolute;
  top: 8px;
  left: 0;
  width: 7px;
  height: 7px;
  background: var(--nus-text-tertiary);
  border-radius: 50%;
}

.history__head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-1) var(--nus-space-3);
  align-items: baseline;
}

.history__event {
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.history__time {
  font-size: var(--nus-fs-caption);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.history__details {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: var(--nus-space-1) 0 0;
}

.history__row {
  display: flex;
  gap: var(--nus-space-3);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
}

.history__row dt {
  flex: none;
  width: 4em;
  color: var(--nus-text-tertiary);
}

.history__row dd {
  min-width: 0;
  margin: 0;
  color: var(--nus-text-secondary);
  overflow-wrap: anywhere;
}

.history__score {
  font-variant-numeric: tabular-nums;
}

.history__comment {
  white-space: pre-line;
}
</style>
