<script lang="ts" setup>
import type { StudentHomeworkApi } from '#/api/edu/student/homework';

import { computed, onActivated, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Button,
  Card,
  Segmented,
  Select,
  Table,
  Tooltip,
} from 'ant-design-vue';

import { getStudentHomeworkPage } from '#/api/edu/student/homework';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import {
  formatDeadlineTime,
  resolveEffectiveDeadline,
} from '#/utils/homework-deadline';
import {
  getHomeworkRecordStatusMeta,
  HOMEWORK_RECORD_STATUS,
} from '#/utils/status';
import { isAwaitingSubmissionStatus } from '#/utils/status-student-homework';

import { getDeadlineBadge } from './personal-deadline';
import { buildTaskRouteQuery, getTaskLevelLabel } from './task-display';
import {
  getTaskListAction,
  getTaskListStatusMeta,
  getTaskUnlockPath,
  isTaskLocked,
} from './task-list-row';

const router = useRouter();
const loading = ref(false);
const homeworkList = ref<StudentHomeworkApi.HomeworkItem[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);
const filterStatus = ref<number | undefined>(undefined);
/** 当前任务，或往期任务（班级、课程或班课已封存，只能查看） */
const scope = ref<StudentHomeworkApi.HomeworkScope>('current');
const isHistory = computed(() => scope.value === 'history');
const scopeOptions = [
  { label: '当前任务', value: 'current' },
  { label: '往期任务', value: 'history' },
];
let mounted = false;

/** 状态筛选：文字取自状态映射；待批改分首次提交与重新提交两种 */
const FILTER_SUFFIX: Record<number, string> = {
  [HOMEWORK_RECORD_STATUS.SUBMITTED]: '（首次提交）',
  [HOMEWORK_RECORD_STATUS.RESUBMITTED]: '（重新提交）',
};
const statusOptions = Object.values(HOMEWORK_RECORD_STATUS).map((value) => ({
  label: `${getHomeworkRecordStatusMeta(value, 'student').label}${FILTER_SUFFIX[value] ?? ''}`,
  value,
}));

async function loadData() {
  loading.value = true;
  try {
    const result = await getStudentHomeworkPage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      status: filterStatus.value,
      scope: scope.value,
    });
    homeworkList.value = result.list ?? [];
    total.value = result.total ?? 0;
  } finally {
    loading.value = false;
  }
}

function handleAction(row: StudentHomeworkApi.HomeworkItem) {
  // 所在等级还没解锁：打开课程详情看解锁条件
  if (isTaskLocked(row)) {
    const path = getTaskUnlockPath(row);
    if (path) {
      router.push({ path });
      return;
    }
  }
  // 往期任务只能查看成绩反馈
  if (!row.historyOnly && isAwaitingSubmissionStatus(row.status)) {
    // 带上课程和等级：任务还没解锁时，提交页据此说明还差什么
    router.push({
      path: `/student/homework/submit/${row.homeworkId}`,
      query: buildTaskRouteQuery(row),
    });
  } else {
    router.push({ path: `/student/homework/feedback/${row.homeworkId}` });
  }
}

function handlePageChange(page: number, size: number) {
  pageNo.value = page;
  pageSize.value = size;
  loadData();
}

function handleFilterChange() {
  pageNo.value = 1;
  loadData();
}

function handleScopeChange() {
  pageNo.value = 1;
  loadData();
}

function handleOpenCourse(row: StudentHomeworkApi.HomeworkItem) {
  if (row.courseId) {
    router.push({ path: `/student/course/detail/${row.courseId}` });
  }
}

function handleClearFilter() {
  filterStatus.value = undefined;
  handleFilterChange();
}

const columns = [
  { title: '任务名称', dataIndex: 'homeworkName', key: 'homeworkName' },
  { title: '课程', dataIndex: 'courseName', key: 'courseName', width: 160 },
  {
    title: '等级',
    dataIndex: 'homeworkLevel',
    key: 'homeworkLevel',
    width: 80,
  },
  { title: '截止时间', dataIndex: 'deadline', key: 'deadline', width: 190 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  {
    title: '分数',
    dataIndex: 'myScore',
    key: 'myScore',
    width: 80,
    align: 'right' as const,
  },
  // 放得下「查看解锁条件」
  { title: '操作', key: 'action', width: 130 },
];

onMounted(() => {
  void loadData().then(() => {
    mounted = true;
  });
});

onActivated(() => {
  if (mounted) void loadData();
});
</script>

<template>
  <Page
    auto-content-height
    description="全部课程的实训任务；未提交和已打回的任务可以从这里去提交。"
    title="任务中心"
  >
    <Card>
      <div class="sheet-toolbar mb-4">
        <div class="sheet-toolbar__filters">
          <Segmented
            v-model:value="scope"
            :options="scopeOptions"
            @change="handleScopeChange"
          />
          <span class="text-sm text-muted-foreground">状态</span>
          <Select
            v-model:value="filterStatus"
            :options="statusOptions"
            allow-clear
            placeholder="全部状态"
            style="width: 180px"
            @change="handleFilterChange"
          />
        </div>
      </div>

      <Table
        :columns="columns"
        :data-source="homeworkList"
        :loading="{ spinning: loading, tip: '正在读取任务…' }"
        :pagination="{
          current: pageNo,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t: number) => `共 ${t} 条`,
          onChange: handlePageChange,
        }"
        row-key="homeworkId"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'courseName' && isHistory">
            <Button
              class="px-0"
              size="small"
              type="link"
              @click="
                handleOpenCourse(record as StudentHomeworkApi.HomeworkItem)
              "
            >
              {{ record.courseName || '-' }}
            </Button>
          </template>
          <template v-else-if="column.key === 'homeworkLevel'">
            {{ getTaskLevelLabel(record.homeworkLevel) }}
          </template>
          <template v-else-if="column.key === 'deadline'">
            {{ formatDeadlineTime(resolveEffectiveDeadline(record)) }}
            <span
              v-if="getDeadlineBadge(record)"
              :title="
                getDeadlineBadge(record) === '延期'
                  ? `教师为你单独延期，任务截止时间为 ${formatDeadlineTime(record.deadline)}`
                  : undefined
              "
              class="text-xs text-muted-foreground"
            >
              {{ getDeadlineBadge(record) }}
            </span>
          </template>
          <template v-else-if="column.key === 'status'">
            <StatusTag
              v-bind="
                getTaskListStatusMeta(record as StudentHomeworkApi.HomeworkItem)
              "
            />
          </template>
          <template v-else-if="column.key === 'myScore'">
            {{
              record.status === HOMEWORK_RECORD_STATUS.CORRECTED
                ? record.myScore
                : '-'
            }}
          </template>
          <template v-else-if="column.key === 'action'">
            <Tooltip
              :title="
                getTaskListAction(record as StudentHomeworkApi.HomeworkItem)
                  .tooltip
              "
            >
              <Button
                type="link"
                size="small"
                @click="handleAction(record as StudentHomeworkApi.HomeworkItem)"
              >
                {{
                  getTaskListAction(record as StudentHomeworkApi.HomeworkItem)
                    .label
                }}
              </Button>
            </Tooltip>
          </template>
        </template>

        <template #emptyText>
          <div v-if="loading" class="h-24"></div>
          <EmptyState
            v-else-if="filterStatus !== undefined"
            compact
            description="换一个状态，或清除筛选查看全部任务。"
            kind="filtered"
            title="没有符合筛选条件的任务"
          >
            <template #action>
              <Button @click="handleClearFilter">清除筛选</Button>
            </template>
          </EmptyState>
          <EmptyState
            v-else-if="isHistory"
            compact
            description="班级或课程封存后，你做过的任务会显示在这里，只能查看。"
            title="还没有往期任务"
          />
          <EmptyState
            v-else
            compact
            description="教师发布任务后会显示在这里。"
            title="还没有实训任务"
          />
        </template>
      </Table>
    </Card>
  </Page>
</template>
