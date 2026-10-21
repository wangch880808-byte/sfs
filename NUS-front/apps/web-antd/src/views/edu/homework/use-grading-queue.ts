import type { Ref } from 'vue';

import type { EduHomeworkRecordApi } from '#/api/edu/homework-record';

import { computed, ref } from 'vue';

import { getHomeworkRecordGradingQueue } from '#/api/edu/homework-record';
import { HOMEWORK_RECORD_STATUS } from '#/utils/status';
import { isPendingReviewStatus } from '#/utils/status-grading';

/**
 * 批改队列：任务下全部可批改的提交，一次取全（后端不分页、不截断），只带列表需要的字段。
 * 保存后只改当前一行，不整表重读；筛选、搜索与“下一份”都在这里算。
 */
export type QueueRecord = EduHomeworkRecordApi.QueueItem;

/** 列表筛选：all 全部；1 待批改（含已重新提交）；2 已批改 */
export type QueueStatusFilter = 'all' | number;

export const QUEUE_STATUS_FILTER_OPTIONS: {
  label: string;
  value: QueueStatusFilter;
}[] = [
  { label: '全部', value: 'all' },
  { label: '待批改', value: HOMEWORK_RECORD_STATUS.SUBMITTED },
  { label: '已批改', value: HOMEWORK_RECORD_STATUS.CORRECTED },
];

export function filterQueue(
  records: QueueRecord[],
  statusFilter: QueueStatusFilter,
  keyword: string,
): QueueRecord[] {
  let list = records;
  if (statusFilter !== 'all') {
    list =
      statusFilter === HOMEWORK_RECORD_STATUS.SUBMITTED
        ? list.filter((r) => isPendingReviewStatus(r.status))
        : list.filter((r) => r.status === statusFilter);
  }
  const kw = keyword.trim().toLowerCase();
  if (kw) {
    list = list.filter(
      (r) =>
        String(r.studentName ?? '')
          .toLowerCase()
          .includes(kw) ||
        String(r.studentNo ?? '')
          .toLowerCase()
          .includes(kw),
    );
  }
  return list;
}

/** 当前筛选顺序里排在 currentId 后面的一份；已是最后一份或不在列表里时为空 */
export function getNextRecordId(
  list: QueueRecord[],
  currentId: number | undefined,
): number | undefined {
  const index = list.findIndex((r) => r.id === currentId);
  return index === -1 ? undefined : list[index + 1]?.id;
}

/** 键盘切换：step 为 1 往下、-1 往上；不在列表里时取第一份 */
export function getAdjacentRecordId(
  list: QueueRecord[],
  currentId: number | undefined,
  step: -1 | 1,
): number | undefined {
  if (list.length === 0) return undefined;
  const index = list.findIndex((r) => r.id === currentId);
  if (index === -1) return list[0]?.id;
  return list[index + step]?.id;
}

export function countQueue(records: QueueRecord[]) {
  let corrected = 0;
  let pending = 0;
  for (const record of records) {
    if (record.status === HOMEWORK_RECORD_STATUS.CORRECTED) corrected++;
    else if (isPendingReviewStatus(record.status)) pending++;
  }
  return { corrected, pending };
}

export function useGradingQueue(homeworkId: Ref<number>) {
  const records = ref<QueueRecord[]>([]);
  /** 服务端给的总数，不按前端列表长度猜 */
  const total = ref(0);
  const loading = ref(false);
  const loadError = ref(false);
  const statusFilter = ref<QueueStatusFilter>('all');
  const keyword = ref('');
  let requestId = 0;

  const filteredRecords = computed(() =>
    filterQueue(records.value, statusFilter.value, keyword.value),
  );
  const counts = computed(() => countQueue(records.value));

  /** 读取队列；返回 false 表示结果已过期（换了任务或又发起了一次读取） */
  async function load(): Promise<boolean> {
    const currentHomeworkId = homeworkId.value;
    if (!currentHomeworkId) return false;
    const current = ++requestId;
    loading.value = true;
    loadError.value = false;
    try {
      const result = await getHomeworkRecordGradingQueue(currentHomeworkId);
      if (current !== requestId) return false;
      records.value = result?.list ?? [];
      total.value = result?.total ?? records.value.length;
      return true;
    } catch {
      if (current === requestId) loadError.value = true;
      return false;
    } finally {
      if (current === requestId) loading.value = false;
    }
  }

  function findRecord(id: number | undefined) {
    return id === undefined
      ? undefined
      : records.value.find((r) => r.id === id);
  }

  /** 保存、撤销后只改这一行 */
  function patch(id: number, fields: Partial<QueueRecord>) {
    const index = records.value.findIndex((r) => r.id === id);
    const existing = records.value[index];
    if (!existing) return;
    records.value.splice(index, 1, { ...existing, ...fields });
  }

  function reset() {
    ++requestId;
    records.value = [];
    total.value = 0;
    loading.value = false;
    loadError.value = false;
  }

  function resetFilters() {
    statusFilter.value = 'all';
    keyword.value = '';
  }

  return {
    counts,
    filteredRecords,
    findRecord,
    keyword,
    load,
    loadError,
    loading,
    patch,
    records,
    reset,
    resetFilters,
    statusFilter,
    total,
  };
}
