<script lang="ts" setup>
import type { EduCourseManageApi } from '#/api/edu/course-manage';

import { computed, onActivated, onMounted } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { Alert, Button } from 'ant-design-vue';

import { getCourseManageDetail } from '#/api/edu/course-manage';
import { getHomeworkPage } from '#/api/edu/homework';
import {
  getHomeworkRecordCorrectPage,
  getHomeworkRecordPage,
} from '#/api/edu/homework-record';
import { getCourseOptions, getTeacherDashboard } from '#/api/edu/statistics';
import { getMyNotifyMessagePage } from '#/api/system/notify/message';
import { HOMEWORK_RECORD_STATUS } from '#/utils/status';
import { MetricTile } from '#/views/dashboard/components';
import { getGreetingTitle } from '#/views/dashboard/shared/greeting';
import {
  TEACHER_LINKS,
  useAvailableLink,
} from '#/views/dashboard/shared/links';
import {
  buildTaskIndex,
  buildTeacherSummary,
  countDueSoon,
  groupPendingRecords,
  pickRecentSubmissions,
  pickStudentMessages,
  pickUpcomingTasks,
  summarizeTeacherCourses,
} from '#/views/dashboard/shared/teacher';
import { formatPastTime, formatWaiting } from '#/views/dashboard/shared/time';
import {
  combinePanelState,
  useAsyncData,
} from '#/views/dashboard/shared/use-async-data';
import { useNow } from '#/views/dashboard/shared/use-now';

import TeacherCourseCard from './components/teacher-dashboard-course-card.vue';
import TeacherDeadlineCard from './components/teacher-dashboard-deadline-card.vue';
import TeacherMessageCard from './components/teacher-dashboard-message-card.vue';
import TeacherPendingCard from './components/teacher-dashboard-pending-card.vue';
import TeacherRecentCard from './components/teacher-dashboard-recent-card.vue';

/**
 * 教师工作台（V1.0.1.6 驾驶舱）：页头问候与今天要处理的事，四个指标，
 * 下面是待批改、即将截止、我的课程（左），学生留言与最近提交（右）。各块分别读取，哪块没读到只影响那一块。
 */

/** 任务与提交记录一次读的条数（接口上限 200） */
const PAGE_SIZE = 200;
/** 最多读几门课程的详情（班级名、提交人数） */
const COURSE_DETAIL_LIMIT = 8;

const router = useRouter();
const userStore = useUserStore();
const { hasAccessByCodes } = useAccess();
const linkOf = useAvailableLink();
const now = useNow();

const summary = useAsyncData(() => getTeacherDashboard());
const homeworks = useAsyncData(
  () =>
    getHomeworkPage({ pageNo: 1, pageSize: PAGE_SIZE }).then(
      (result) => result.list ?? [],
    ),
  { enabled: () => hasAccessByCodes(['edu:homework:query']) },
);
const pendingRecords = useAsyncData(
  () =>
    getHomeworkRecordCorrectPage({
      pageNo: 1,
      pageSize: PAGE_SIZE,
      // 接口按“待批改”查时包含重新提交
      status: HOMEWORK_RECORD_STATUS.SUBMITTED,
    }).then((result) => result.list ?? []),
  { enabled: () => hasAccessByCodes(['edu:homework-record:correct']) },
);
const recentRecords = useAsyncData(
  () =>
    getHomeworkRecordPage({ pageNo: 1, pageSize: 20 }).then(
      (result) => result.list ?? [],
    ),
  { enabled: () => hasAccessByCodes(['edu:homework-record:query']) },
);
const notifications = useAsyncData(() =>
  getMyNotifyMessagePage({ pageNo: 1, pageSize: 20 }).then(
    (result) => result.list ?? [],
  ),
);
const courses = useAsyncData(
  async () => {
    const options = await getCourseOptions();
    const details = hasAccessByCodes(['capacity:indicator:query'])
      ? await Promise.all(
          options
            .slice(0, COURSE_DETAIL_LIMIT)
            .map((option) =>
              getCourseManageDetail(option.id).catch(() => undefined),
            ),
        )
      : [];
    return {
      details: details.filter(
        (item): item is EduCourseManageApi.CourseDetail => !!item,
      ),
      options,
    };
  },
  { enabled: () => hasAccessByCodes(['edu:statistics:query']) },
);

const sources = [
  summary,
  homeworks,
  pendingRecords,
  recentRecords,
  notifications,
  courses,
];

function loadAll() {
  return Promise.all(sources.map((source) => source.load()));
}

function retryPending() {
  return Promise.all([homeworks.load(), pendingRecords.load()]);
}

function retryRecent() {
  return Promise.all([homeworks.load(), recentRecords.load()]);
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

// ---- 整理 ----
const taskIndex = computed(() =>
  buildTaskIndex({
    courseDetails: courses.data.value?.details,
    courseOptions: courses.data.value?.options,
    homeworks: homeworks.data.value,
  }),
);
const pendingGroups = computed(() =>
  groupPendingRecords(pendingRecords.data.value ?? [], taskIndex.value),
);
const upcomingTasks = computed(() =>
  pickUpcomingTasks(taskIndex.value, now.value),
);
const recentSubmissions = computed(() =>
  pickRecentSubmissions(recentRecords.data.value ?? [], taskIndex.value),
);
const studentMessages = computed(() =>
  pickStudentMessages(notifications.data.value ?? []),
);
const courseItems = computed(() =>
  summarizeTeacherCourses(
    courses.data.value?.options ?? [],
    courses.data.value?.details ?? [],
    pendingRecords.loaded.value && homeworks.loaded.value
      ? pendingGroups.value
      : undefined,
  ),
);

// ---- 面板状态：任务名称来自任务列表，待批改与最近提交都要等它读到 ----
const pendingState = computed(() =>
  combinePanelState([pendingRecords, homeworks]),
);
const deadlineState = computed(() => combinePanelState([homeworks]));
const recentState = computed(() =>
  combinePanelState([recentRecords, homeworks]),
);
const messageState = computed(() => combinePanelState([notifications]));
const courseState = computed(() => combinePanelState([courses]));

const showPending = computed(
  () => pendingRecords.enabled.value && homeworks.enabled.value,
);
const showRecent = computed(
  () => recentRecords.enabled.value && homeworks.enabled.value,
);

// ---- 页头与指标 ----
const title = computed(() => getGreetingTitle(userStore.userInfo?.nickname));
const description = computed(() => {
  if (!summary.loaded.value) {
    return summary.error.value
      ? '待批改、即将截止和学生留言都在这里。'
      : '正在读取今天要处理的事…';
  }
  return buildTeacherSummary({
    dueSoon: homeworks.loaded.value
      ? countDueSoon(taskIndex.value, now.value)
      : 0,
    pending: summary.data.value?.pendingCorrections,
    unread: summary.data.value?.unreadMessages,
  });
});

const homeworkListLink = computed(() => linkOf(TEACHER_LINKS.homeworkList));
const messagesLink = computed(() => linkOf(TEACHER_LINKS.messages));

const metrics = computed(() => {
  const data = summary.data.value;
  const oldest = pendingGroups.value[0]?.oldestSubmitTime;
  const latestMessage = studentMessages.value[0]?.createTime;
  const classCount = courseItems.value.reduce(
    (sum, item) => sum + (item.classCount ?? 0),
    0,
  );
  const studentCount = courseItems.value.reduce(
    (sum, item) => sum + (item.studentCount ?? 0),
    0,
  );
  return [
    {
      key: 'pending',
      label: '待批改',
      meta:
        data?.pendingCorrections && oldest
          ? `最早一份${formatWaiting(oldest, now.value)}`
          : '',
      to: homeworkListLink.value,
      unit: '份',
      value: data?.pendingCorrections,
    },
    {
      key: 'unread',
      label: '未读留言',
      meta: latestMessage
        ? `最新一条 ${formatPastTime(latestMessage, now.value)}`
        : '',
      to: messagesLink.value,
      unit: '条',
      value: data?.unreadMessages,
    },
    {
      key: 'week',
      label: '本周提交',
      meta: '从本周一算起',
      to: homeworkListLink.value,
      unit: '份',
      value: data?.weekSubmissions,
    },
    {
      key: 'courses',
      label: '我的课程',
      meta:
        classCount > 0 ? `${classCount} 个班级 · ${studentCount} 名学生` : '',
      to: linkOf(TEACHER_LINKS.courseList),
      unit: '门',
      value: data?.myCourses,
    },
  ];
});
</script>

<template>
  <Page :description="description" :title="title">
    <template #extra>
      <Button v-if="messagesLink" @click="router.push(messagesLink)">
        查看留言
      </Button>
      <Button
        v-if="homeworkListLink"
        type="primary"
        @click="router.push(homeworkListLink)"
      >
        去批改
      </Button>
    </template>

    <Alert
      v-if="summary.error.value && !summary.loaded.value"
      class="mb-4"
      message="统计数字没有读到，可能是网络或服务暂时不可用。"
      show-icon
      type="warning"
    >
      <template #action>
        <Button size="small" @click="summary.load()">重试</Button>
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
      宽屏两列：左边是要处理的事与我的课程，右边是学生留言与最近提交；
      窄屏一列时按要紧程度排：待批改、学生留言、即将截止、最近提交、我的课程。
    -->
    <div class="mt-6 grid items-start gap-4 xl:grid-cols-3">
      <div
        class="contents xl:col-span-2 xl:flex xl:min-w-0 xl:flex-col xl:gap-4"
      >
        <TeacherPendingCard
          v-if="showPending"
          :error="pendingState.error"
          :groups="pendingGroups"
          :loading="pendingState.loading"
          :now="now"
          :total="summary.data.value?.pendingCorrections"
          class="order-1 xl:order-none"
          @retry="retryPending"
        />
        <TeacherDeadlineCard
          v-if="homeworks.enabled.value"
          :error="deadlineState.error"
          :loading="deadlineState.loading"
          :now="now"
          :tasks="upcomingTasks"
          class="order-3 xl:order-none"
          @retry="homeworks.load()"
        />
        <TeacherCourseCard
          v-if="courses.enabled.value"
          :courses="courseItems"
          :error="courseState.error"
          :loading="courseState.loading"
          class="order-5 xl:order-none"
          @retry="courses.load()"
        />
      </div>
      <div class="contents xl:flex xl:min-w-0 xl:flex-col xl:gap-4">
        <TeacherMessageCard
          :error="messageState.error"
          :items="studentMessages"
          :loading="messageState.loading"
          :now="now"
          :unread="summary.data.value?.unreadMessages"
          class="order-2 xl:order-none"
          @retry="notifications.load()"
        />
        <TeacherRecentCard
          v-if="showRecent"
          :error="recentState.error"
          :items="recentSubmissions"
          :loading="recentState.loading"
          :now="now"
          class="order-4 xl:order-none"
          @retry="retryRecent"
        />
      </div>
    </div>
  </Page>
</template>
