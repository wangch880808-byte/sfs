<script lang="ts" setup>
import type { QueueRecord, QueueStatusFilter } from './use-grading-queue';

import { computed, nextTick, watch } from 'vue';

import { Search } from '@vben/icons';

import { Button, Checkbox, Input, Segmented } from 'ant-design-vue';

import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import { getHomeworkRecordStatusMeta } from '#/utils/status';
import {
  hasVisibleScoreStatus,
  isPreviousScoreStatus,
} from '#/utils/status-grading';
import { getRecordListMeta } from '#/views/edu/homework/components/grading-display';

import { QUEUE_STATUS_FILTER_OPTIONS } from './use-grading-queue';

/**
 * 批改工作台左栏：筛选、搜索、勾选打回与学生记录。有未保存草稿的记录加“草稿未保存”标记。
 * 只读（不是布置者）时不显示勾选框与批量打回。
 */
const props = defineProps<{
  checkedIds: number[];
  draftIds: Set<number>;
  loading: boolean;
  /** 只读：不能勾选打回 */
  readOnly?: boolean;
  records: QueueRecord[];
  selectedId?: number;
  submitting: boolean;
}>();

const emit = defineEmits<{
  check: [id: number, checked: boolean];
  redoBatch: [];
  resetFilters: [];
  select: [id: number];
}>();

const statusFilter = defineModel<QueueStatusFilter>('statusFilter', {
  required: true,
});
const keyword = defineModel<string>('keyword', { required: true });

const DRAFT_TAG = { label: '草稿未保存', tone: 'waiting' } as const;

const checkedSet = computed(() => new Set(props.checkedIds));

/** 姓名缺失时用学号，不在界面上显示内部编号 */
function getDisplayName(record: QueueRecord) {
  return record.studentName || record.studentNo || '未命名学生';
}

function getVisibleScore(record: QueueRecord) {
  return hasVisibleScoreStatus(record.status) &&
    record.score !== null &&
    record.score !== undefined
    ? record.score
    : undefined;
}

/** 键盘切换后让选中的一行留在可见区域 */
watch(
  () => props.selectedId,
  async (id) => {
    if (id === undefined) return;
    await nextTick();
    document
      .querySelector(`[data-grading-row="${id}"]`)
      ?.scrollIntoView({ block: 'nearest' });
  },
);
</script>

<template>
  <aside aria-label="学生记录" class="surface-panel grading-list">
    <div class="grading-list__toolbar">
      <Segmented
        v-model:value="statusFilter"
        :options="QUEUE_STATUS_FILTER_OPTIONS"
        block
      />
      <Input v-model:value="keyword" allow-clear placeholder="搜索姓名或学号">
        <template #prefix>
          <Search aria-hidden="true" class="grading-list__search-icon" />
        </template>
      </Input>
    </div>

    <div
      v-if="!readOnly && checkedIds.length > 0"
      class="grading-list__selection"
    >
      <span>已选 {{ checkedIds.length }} 份</span>
      <Button
        danger
        size="small"
        :loading="submitting"
        @click="emit('redoBatch')"
      >
        打回所选 {{ checkedIds.length }} 份
      </Button>
    </div>

    <ul v-if="records.length > 0" class="grading-list__body">
      <li
        v-for="item in records"
        :key="item.id"
        :class="{ 'is-selected': selectedId === item.id }"
        :data-grading-row="item.id"
        class="grading-row"
        @click="emit('select', item.id)"
      >
        <Checkbox
          v-if="!readOnly"
          :checked="checkedSet.has(item.id)"
          class="grading-row__check"
          @click.stop
          @change="(e: any) => emit('check', item.id, e.target.checked)"
        />
        <button
          :aria-current="selectedId === item.id ? 'true' : undefined"
          class="grading-row__main"
          type="button"
        >
          <span class="grading-row__line">
            <span class="grading-row__name">{{ getDisplayName(item) }}</span>
            <StatusTag
              v-bind="getHomeworkRecordStatusMeta(item.status, 'teacher')"
            />
          </span>
          <span class="grading-row__line grading-row__meta">
            <span :title="getRecordListMeta(item)" class="grading-row__sub">
              {{ getRecordListMeta(item) }}
            </span>
            <StatusTag v-if="draftIds.has(item.id)" v-bind="DRAFT_TAG" />
            <span
              v-else-if="getVisibleScore(item) !== undefined"
              class="grading-row__score"
            >
              <span
                v-if="isPreviousScoreStatus(item.status)"
                class="grading-row__score-label"
              >
                原
              </span>
              {{ getVisibleScore(item) }}
            </span>
          </span>
        </button>
      </li>
    </ul>
    <EmptyState
      v-else-if="!loading"
      class="grading-list__empty"
      compact
      description="换个筛选条件，或清空搜索。"
      kind="filtered"
      title="没有符合条件的记录"
    >
      <template #action>
        <Button size="small" @click="emit('resetFilters')">清除筛选</Button>
      </template>
    </EmptyState>
  </aside>
</template>

<style scoped>
@media (width < 768px) {
  .grading-list .grading-list__body {
    max-height: 360px;
  }
}

.grading-list {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.grading-list__empty {
  flex: 1;
}

.grading-list__toolbar {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: var(--nus-space-2);
  padding: var(--nus-space-3);
  border-bottom: 1px solid var(--nus-border-subtle);
}

.grading-list__search-icon {
  width: 14px;
  height: 14px;
  color: var(--nus-text-tertiary);
}

.grading-list__selection {
  display: flex;
  flex: none;
  gap: var(--nus-space-2);
  align-items: center;
  justify-content: space-between;
  padding: var(--nus-space-2) var(--nus-space-3);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
  background: var(--nus-bg-sunken);
  border-bottom: 1px solid var(--nus-border-subtle);
}

.grading-list__body {
  flex: 1;
  min-height: 0;
  padding: var(--nus-space-2);
  margin: 0;
  overflow-y: auto;
  list-style: none;
}

.grading-row {
  display: flex;
  gap: var(--nus-space-3);
  align-items: flex-start;
  padding: var(--nus-space-2) var(--nus-space-3);
  cursor: pointer;
  border-radius: var(--nus-radius);
  transition: var(--nus-transition-colors);
}

.grading-row + .grading-row {
  margin-top: 2px;
}

.grading-row:hover {
  background: var(--nus-bg-hover);
}

.grading-row.is-selected {
  background: var(--nus-bg-selected);
}

.grading-row__check {
  margin-top: 3px;
}

.grading-row__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: 0;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: var(--nus-radius-sm);
}

.grading-row__main:focus-visible {
  outline: 2px solid var(--nus-primary);
  outline-offset: 2px;
}

.grading-row__line {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  justify-content: space-between;
  min-width: 0;
}

.grading-row__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  white-space: nowrap;
}

.grading-row__meta {
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.grading-row__sub {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.grading-row__score {
  flex: none;
  font-size: var(--nus-fs-dense);
  font-weight: var(--nus-fw-medium);
  font-variant-numeric: tabular-nums;
  color: var(--nus-text);
}

.grading-row__score-label {
  margin-right: 2px;
  font-size: var(--nus-fs-caption);
  font-weight: var(--nus-fw-regular);
  color: var(--nus-text-tertiary);
}
</style>
