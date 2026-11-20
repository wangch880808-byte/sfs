<script lang="ts" setup>
import type { SystemNotifyMessageApi } from '#/api/system/notify/message';

import { computed, onActivated, onMounted } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { Alert, Button } from 'ant-design-vue';

import {
  getStudentCourseList,
  getUnlockStatus,
} from '#/api/edu/student/course';
import { getOverview } from '#/api/edu/student/home';
import { getStudentHomeworkPage } from '#/api/edu/student/homework';
import { getStudentConversations } from '#/api/edu/student/message';
import {
  getMyNotifyMessagePage,
  getUnreadNotifyMessageCount,
  updateNotifyMessageRead,
} from '#/api/system/notify/message';
import { emitNotifyUnreadChanged } from '#/utils/notify-unread';
import { DUE_SOON_HOURS } from '#/utils/status-dashboard';
import { MetricTile } from '#/views/dashboard/components';
import { getGreetingTitle } from '#/views/dashboard/shared/greeting';
import {
  STUDENT_LINKS,
  useAvailableLink,
} from '#/views/dashboard/shared/links';
import {
  buildFeedbackItems,
  buildStudentSummary,
  collectLockedLevels,
  collectTodoCourseIds,
  countDueSoonTodos,
  pickTodoTasks,
  sumUnread,
} from '#/views/dashboard/shared/student';
import { formatDeadline } from '#/views/dashboard/shared/time';
import {
  combinePanelState,
  useAsyncData,
} from '#/views/dashboard/shared/use-async-data';
import { useNow } from '#/views/dashboard/shared/use-now';

import StudentCourseCard from './components/student-home-course-card.vue';
import StudentFeedbackCard from './components/student-home-feedback-card.vue';
import StudentNotifyCard from './components/student-home-notify-card.vue';
import StudentTodoCard from './components/student-home-todo-card.vue';

/**
 * 学生首页（V1.0.1.6 驾驶舱）：页头问候与要做的事，四个指标，
 * 下面是待完成、最新反馈、我的课程（左），消息提醒（右）。各块分别读取，哪块没读到只影响那一块。
 */

/** 任务列表一次读的条数（接口上限 200） */
const PAGE_SIZE = 200;

const userStore = useUserStore();
const { hasAccessByCodes } = useAccess();
const linkOf = useAvailableLink();
const now = useNow();

const overview = useAsyncData(() => getOverview());
const notify = useAsyncData(async () => {
  const [page, unread] = await Promise.all([
    getMyNotifyMessagePage({ pageNo: 1, pageSize: 10 }),
    getUnreadNotifyMessageCount(),
  ]);
  return { list: page.list ?? [], unread: Number(unread) || 0 };
});
const courses = useAsyncData(async () => (await getStudentCourseList()) ?? []);
const homeworkList = useAsyncData(
  () =>
    getStudentHomeworkPage({ pageNo: 1, pageSize: PAGE_SIZE }).then(
      (result) => result.list ?? [],
    ),
  { enabled: () => hasAccessByCodes(['edu:student-homework:query']) },
);
/** 有待完成任务的课程读一下等级解锁状态；读不到的课程按已解锁处理（提交页会再拦一次） */
const lockedLevels = useAsyncData(async () => {
  const courseIds = collectTodoCourseIds(homeworkList.data.value ?? []);
  const entries = await Promise.all(
    courseIds.map(
      async (courseId) =>
        [
          courseId,
          await getUnlockStatus(courseId).catch(() => undefined),
        ] as const,
    ),
  );
  return collectLockedLevels(entries);
});
const conversations = useAsyncData(() => getStudentConversations(), {
  enabled: () => hasAccessByCodes(['edu:student-message:query']),
});

function loadAll() {
  return Promise.all([
    overview.load(),
    notify.load(),
    courses.load(),
    conversations.load(),
    loadTodo(),
  ]);
}

async function loadTodo() {
  await homeworkList.load();
  if (homeworkList.loaded.value) {
    await lockedLevels.load();
  }
}

let mounted = false;
onMounted(() => {
  void loadAll().then(() => {
    mounted = true;
  });
});
onActivated(() => {
  if (mounted) void loadAll();
});

async function handleReadNotify(item: SystemNotifyMessageApi.NotifyMessage) {
  if (item.readStatus) return;
  await updateNotifyMessageRead([item.id]);
  // 顶栏铃铛立即重新读取未读数
  emitNotifyUnreadChanged();
  const current = notify.data.value;
  if (!current) return;
  notify.data.value = {
    list: current.list.map((message) =>
      message.id === item.id ? { ...message, readStatus: true } : message,
    ),
    unread: Math.max(0, current.unread - 1),
  };
}

// ---- 整理 ----
const todo = computed(() =>
  // locked 由后端按“课程 + 技能 + 等级”匹配好，直接用任务自带的字段
  pickTodoTasks(homeworkList.data.value ?? [], now.value),
);
const openTodos = computed(() =>
  todo.value.items.filter((item) => !item.locked),
);
const feedbackItems = computed(() =>
  buildFeedbackItems(
    overview.data.value?.recentScores ?? [],
    homeworkList.data.value ?? [],
  ),
);
const unreadMessages = computed(() =>
  conversations.loaded.value
    ? sumUnread(conversations.data.value ?? [])
    : undefined,
);

const todoState = computed(() =>
  combinePanelState([homeworkList, lockedLevels]),
);
const feedbackState = computed(() => combinePanelState([overview]));
const notifyState = computed(() => combinePanelState([notify]));
const courseState = computed(() => combinePanelState([courses]));

// ---- 页头与指标 ----
const title = computed(() => getGreetingTitle(userStore.userInfo?.nickname));
const description = computed(() => {
  // 读不了或没读到任务列表时不下“没有待完成”的结论
  if (!homeworkList.enabled.value || todoState.value.error) {
    return '你的任务、反馈、通知与留言都在这里。';
  }
  if (todoState.value.loading) {
    return '正在读取你的任务与通知…';
  }
  return buildStudentSummary(
    { todo: todo.value, unread: unreadMessages.value },
    now.value,
  );
});

const homeworkLink = computed(() => linkOf(STUDENT_LINKS.homeworkList));

const metrics = computed(() => {
  const data = overview.data.value;
  const nearest = openTodos.value[0]?.deadline;
  const lockedCount = todo.value.items.length - openTodos.value.length;
  const todoLoaded = homeworkList.loaded.value;
  const hasScores = (data?.recentScores.length ?? 0) > 0;
  let todoMeta = '';
  if (todoLoaded && nearest) {
    todoMeta = `最近一项 ${formatDeadline(nearest, now.value)}`;
  } else if (todoLoaded && lockedCount > 0) {
    todoMeta = `${lockedCount} 项等待解锁`;
  }
  return [
    {
      key: 'todo',
      label: '待完成',
      meta: todoMeta,
      to: homeworkLink.value,
      unit: '项',
      value: data?.pendingCount,
    },
    {
      key: 'due-soon',
      label: '即将截止',
      meta: `${DUE_SOON_HOURS / 24} 天内要交`,
      to: homeworkLink.value,
      unit: '项',
      value: todoLoaded ? countDueSoonTodos(todo.value, now.value) : undefined,
    },
    {
      key: 'average',
      label: '平均分',
      meta: data ? `已提交 ${data.submittedCount} 项` : '',
      to: homeworkLink.value,
      unit: hasScores ? '分' : '',
      value: hasScores ? data?.averageScore.toFixed(1) : undefined,
    },
    {
      key: 'unread',
      label: '未读留言',
      meta: '老师的回复',
      to: linkOf(STUDENT_LINKS.messages),
      unit: '条',
      value: unreadMessages.value,
    },
  ];
});
</script>

<template>
  <Page :description="description" :title="title">
    <Alert
      v-if="overview.error.value && !overview.loaded.value"
      class="mb-4"
      message="任务与成绩概览没有读到，可能是网络或服务暂时不可用。"
      show-icon
      type="warning"
    >
      <template #action>
        <Button size="small" @click="overview.load()">重试</Button>
      </template>
    </Alert>

    <div class="metric-grid lg:grid-cols-4">
      <MetricTile
        v-for="metric in metrics"
        :key="metric.key"
        :label="metric.label"
        :meta="metric.meta"
        :to="metric.to"
        :unit="metric.unit"
        :value="metric.value"
      />
    </div>

    <!--
      宽屏两列：左边是要做的事、反馈与课程进度，右边是消息提醒；
      窄屏一列时按要紧程度排：待完成、最新反馈、消息提醒、我的课程。
    -->
    <div class="mt-6 grid items-start gap-4 xl:grid-cols-3">
      <div
        class="contents xl:col-span-2 xl:flex xl:min-w-0 xl:flex-col xl:gap-4"
      >
        <StudentTodoCard
          v-if="homeworkList.enabled.value"
          :error="todoState.error"
          :items="todo.items"
          :loading="todoState.loading"
          :now="now"
          :overdue-count="todo.overdueCount"
          class="order-1 xl:order-none"
          @retry="loadTodo()"
        />
        <StudentFeedbackCard
          :error="feedbackState.error"
          :items="feedbackItems"
          :loading="feedbackState.loading"
          :now="now"
          class="order-2 xl:order-none"
          @retry="overview.load()"
        />
        <StudentCourseCard
          :courses="courses.data.value ?? []"
          :error="courseState.error"
          :loading="courseState.loading"
          class="order-4 xl:order-none"
          @retry="courses.load()"
        />
      </div>
      <div class="contents xl:flex xl:min-w-0 xl:flex-col xl:gap-4">
        <StudentNotifyCard
          :error="notifyState.error"
          :loading="notifyState.loading"
          :notifications="notify.data.value?.list ?? []"
          :now="now"
          :unread-count="notify.data.value?.unread ?? 0"
          class="order-3 xl:order-none"
          @read-notify="handleReadNotify"
          @retry="notify.load()"
        />
      </div>
    </div>
  </Page>
</template>
