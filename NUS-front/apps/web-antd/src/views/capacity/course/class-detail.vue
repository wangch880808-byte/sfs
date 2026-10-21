<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { EduCourseManageApi } from '#/api/edu/course-manage';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Avatar,
  Button,
  Card,
  Input,
  message,
  Select,
  Spin,
  TabPane,
  Tabs,
} from 'ant-design-vue';

import { ACTION_ICON, TableAction } from '#/adapter/vxe-table';
import {
  getCourseManageClassDetail,
  sendTeacherClassMessage,
} from '#/api/edu/course-manage';
import DetailSheetGrid from '#/components/detail-sheet-grid.vue';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import { getTaskProgressMeta } from '#/utils/status';
import { getClassCourseStatusMeta } from '#/utils/status-course-detail';

import { isClassOperable } from './class-claim';

interface ClassProfile {
  classCourseId: number;
  classId: number;
  className: string;
  semester: string;
  semesterStart: string;
  semesterEnd: string;
  status: string;
  studentCount: number;
  assistantTeacher: string;
  courseId: number;
  courseName: string;
  trainingCategory: string;
  teacherName: string;
  indicatorName: string;
}

defineOptions({ name: 'CapacityCourseClassDetail' });

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const activeSheet = ref<'members' | 'messages' | 'scores' | 'tasks'>('tasks');
const detailData = ref<EduCourseManageApi.ClassDetail>();

const newQuestion = ref('');
const targetStudentId = ref<number>();
const taskGridRef = ref<{ recalculate?: () => void }>();
const memberGridRef = ref<{ recalculate?: () => void }>();
const scoreGridRef = ref<{ recalculate?: () => void }>();

const classCourseId = computed(() => Number(route.params.classCourseId || 0));

const profile = computed<ClassProfile>(() => {
  const detail = detailData.value;
  return {
    classCourseId: Number(detail?.classCourseId || classCourseId.value || 0),
    classId: Number(detail?.classId || 0),
    className: detail?.className || '未命名班级',
    semester: detail?.semesterName || '-',
    semesterStart: detail?.semesterStartDate || '-',
    semesterEnd: detail?.semesterEndDate || '-',
    status: detail?.classStatus || '进行中',
    studentCount: Number(detail?.studentCount || 0),
    assistantTeacher: detail?.assistantTeacherNames || '-',
    courseId: Number(detail?.courseId || 0),
    courseName: detail?.courseName || '实训课程',
    trainingCategory: '实训课程',
    teacherName: detail?.teacherName || '-',
    indicatorName: detail?.indicatorName || '-',
  };
});

/** 页头：标题是班级与课程；说明第一行是状态、指标与教师，第二行是学期与起止日期 */
const pageTitle = computed(() =>
  detailData.value
    ? `${profile.value.className} · ${profile.value.courseName}`
    : '班级详情',
);
const classStatusMeta = computed(() =>
  getClassCourseStatusMeta(profile.value.status),
);
const pageMetaItems = computed(() => {
  const detail = detailData.value;
  if (!detail) {
    return [];
  }
  // 后端没有助教时返回“-”，不显示
  const assistants = detail.assistantTeacherNames?.trim();
  return [
    detail.indicatorName
      ? `一级指标：${detail.indicatorName}`
      : '未关联一级指标',
    detail.teacherName ? `授课教师：${detail.teacherName}` : '',
    assistants && assistants !== '-' ? `助教：${assistants}` : '',
  ].filter(Boolean);
});

/**
 * 能否操作这个班（发送留言等）：公共课里负责人、管理员只读查看别人认领的班级时后端返回 false；
 * 旧版后端没有这个字段，按能操作处理。
 */
const canOperateClass = computed(() => isClassOperable(detailData.value));
const taskList = computed(() => detailData.value?.tasks || []);
const memberList = computed(() => detailData.value?.members || []);
const messageList = computed(() => detailData.value?.messages || []);
const scoreSummary = computed(
  () =>
    detailData.value?.scoreAnalysis || {
      avgScore: 0,
      highestScore: 0,
      lowestScore: 0,
      passRate: 0,
      scoreRanges: [],
    },
);

const studentOptions = computed(() =>
  memberList.value.map((item) => ({
    label: `${item.studentName || '-'}（${item.studentNo || '-'}）`,
    value: Number(item.studentId),
  })),
);

const taskColumns: VxeTableGridOptions<EduCourseManageApi.ClassTask>['columns'] =
  [
    {
      field: 'homeworkName',
      title: '任务名称',
      minWidth: 220,
      align: 'left',
      slots: { default: 'taskNameCell' },
    },
    {
      field: 'publishTime',
      title: '发布时间',
      width: 160,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      field: 'deadline',
      title: '截止时间',
      width: 160,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      field: 'status',
      title: '状态',
      width: 110,
      slots: { default: 'taskStatusCell' },
    },
    // 数字列右对齐，放在文字列之后，不与左对齐的文字挤在一起
    {
      field: 'submittedCount',
      title: '提交人数',
      width: 100,
      align: 'right',
      formatter: ({ cellValue }) => Number(cellValue || 0),
    },
    {
      field: 'correctedCount',
      title: '已批改',
      width: 100,
      align: 'right',
      formatter: ({ cellValue }) => Number(cellValue || 0),
    },
    {
      field: 'avgScore',
      title: '均分',
      width: 100,
      align: 'right',
      formatter: ({ cellValue }) => Number(cellValue || 0).toFixed(1),
    },
    {
      title: '操作',
      width: 130,
      fixed: 'right',
      slots: { default: 'taskActions' },
    },
  ];

const memberColumns: VxeTableGridOptions<EduCourseManageApi.ClassMember>['columns'] =
  [
    {
      field: 'studentNo',
      title: '学号',
      width: 160,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      field: 'studentName',
      title: '姓名',
      minWidth: 200,
      slots: { default: 'memberNameCell' },
    },
    {
      field: 'taskFinished',
      title: '任务完成',
      width: 120,
      align: 'right',
      formatter: ({ row }) =>
        `${row.finishedTaskCount || 0}/${row.totalTaskCount || 0}`,
    },
    {
      field: 'avgScore',
      title: '平均分',
      width: 120,
      align: 'right',
      formatter: ({ cellValue }) => Number(cellValue || 0).toFixed(1),
    },
  ];

const scoreDistributionColumns: VxeTableGridOptions<
  Record<string, any>
>['columns'] = [
  { field: 'range', title: '分数段', minWidth: 160 },
  { field: 'count', title: '人数', width: 120, align: 'right' },
  { field: 'percent', title: '占比', width: 120, align: 'right' },
];

/** 成员表：行悬停与点选高亮（点选的行用选中底，便于横向对照） */
const memberRowConfig = { isCurrent: true, isHover: true };

const submittedRate = computed(() => {
  const submitted = taskList.value.reduce(
    (sum, item) => sum + Number(item.submittedCount || 0),
    0,
  );
  const total = profile.value.studentCount * (taskList.value.length || 1);
  return total > 0 ? Number(((submitted / total) * 100).toFixed(1)) : 0;
});

const correctedRate = computed(() => {
  const corrected = taskList.value.reduce(
    (sum, item) => sum + Number(item.correctedCount || 0),
    0,
  );
  const submitted = taskList.value.reduce(
    (sum, item) => sum + Number(item.submittedCount || 0),
    0,
  );
  return submitted > 0 ? Number(((corrected / submitted) * 100).toFixed(1)) : 0;
});

const scoreDistribution = computed(() =>
  (scoreSummary.value.scoreRanges || []).map((item) => ({
    range: item.range,
    count: item.count,
    percent: `${Number(item.percent || 0).toFixed(1)}%`,
  })),
);

function formatSemesterRange() {
  return `${profile.value.semesterStart} 至 ${profile.value.semesterEnd}`;
}

async function loadDetail() {
  if (!classCourseId.value) {
    message.warning(
      '班级详情没有打开：链接里缺少班级信息。从课程详情重新进入。',
    );
    return;
  }
  loading.value = true;
  try {
    detailData.value = await getCourseManageClassDetail(classCourseId.value);
    if (!targetStudentId.value && detailData.value?.members?.length) {
      targetStudentId.value = Number(detailData.value.members[0]!.studentId);
    }
  } finally {
    loading.value = false;
  }
}

function handleOpenHomeworkDetail(task: Record<string, any>) {
  const homeworkId = Number(task.homeworkId || 0);
  if (!homeworkId) {
    message.warning('这个任务的数据不完整，无法查看详情。刷新后重试。');
    return;
  }
  router.push({
    path: `/capacity/homework_manager/homework/detail/${homeworkId}`,
  });
}

async function handleSendQuestion() {
  const content = newQuestion.value.trim();
  if (!content) {
    message.warning('请输入留言内容');
    return;
  }
  if (!targetStudentId.value) {
    message.warning('请选择接收学生');
    return;
  }
  await sendTeacherClassMessage({
    courseId: profile.value.courseId,
    classId: profile.value.classId,
    studentId: targetStudentId.value,
    content,
  });
  newQuestion.value = '';
  message.success('留言已发送');
  await loadDetail();
}

onMounted(async () => {
  await loadDetail();
});

watch(activeSheet, async (value) => {
  await nextTick();
  switch (value) {
    case 'members': {
      memberGridRef.value?.recalculate?.();
      break;
    }
    case 'scores': {
      scoreGridRef.value?.recalculate?.();
      break;
    }
    case 'tasks': {
      taskGridRef.value?.recalculate?.();
      break;
    }
  }
});
</script>

<template>
  <Page :title="pageTitle" auto-content-height>
    <template #description>
      <div class="class-detail__meta">
        <p class="class-detail__meta-line">
          <template v-if="detailData">
            <StatusTag v-bind="classStatusMeta" />
            <span v-for="item in pageMetaItems" :key="item">{{ item }}</span>
          </template>
        </p>
        <p class="class-detail__meta-line">
          <span v-if="detailData">
            {{ profile.semester }} 学期 · {{ formatSemesterRange() }}
          </span>
        </p>
      </div>
    </template>
    <template #extra>
      <Button @click="router.back()">返回课程详情</Button>
    </template>

    <div v-if="!detailData" class="class-detail__state">
      <Spin v-if="loading" tip="正在读取班级信息…" />
      <EmptyState
        v-else
        kind="blocked"
        title="没有读取到班级信息"
        description="可能是网络中断或班级已封存。可以重新读取，或返回课程详情。"
      >
        <template #action>
          <Button @click="loadDetail">重新读取</Button>
        </template>
      </EmptyState>
    </div>

    <Spin v-else :spinning="loading">
      <div class="class-detail">
        <div class="metric-grid">
          <div class="metric-card">
            <div class="metric-card__label">班级人数</div>
            <div class="metric-card__value">{{ profile.studentCount }}</div>
            <div class="metric-card__meta">当前参与本课程的学生总数</div>
          </div>
          <div class="metric-card">
            <div class="metric-card__label">实训任务</div>
            <div class="metric-card__value">{{ taskList.length }}</div>
            <div class="metric-card__meta">当前班级已参与的任务数</div>
          </div>
          <div class="metric-card">
            <div class="metric-card__label">任务提交率</div>
            <div class="metric-card__value">{{ submittedRate }}%</div>
            <div class="metric-card__meta">按班级任务整体提交情况统计</div>
          </div>
          <div class="metric-card">
            <div class="metric-card__label">批改完成率</div>
            <div class="metric-card__value">{{ correctedRate }}%</div>
            <div class="metric-card__meta">按已提交记录的批改完成情况统计</div>
          </div>
        </div>

        <Card class="class-detail__sections">
          <Tabs
            v-model:active-key="activeSheet"
            :animated="{ inkBar: true, tabPane: true }"
          >
            <TabPane key="tasks" tab="任务">
              <div class="class-detail__toolbar">
                <span class="class-detail__count">
                  共 {{ taskList.length }} 个任务
                </span>
              </div>
              <DetailSheetGrid
                v-if="taskList.length > 0"
                ref="taskGridRef"
                :columns="taskColumns"
                :data="taskList"
                row-key="homeworkId"
              >
                <template #taskNameCell="{ row }">
                  <Button
                    class="px-0"
                    type="link"
                    @click="handleOpenHomeworkDetail(row)"
                  >
                    {{ row.homeworkName || '-' }}
                  </Button>
                </template>
                <template #taskStatusCell="{ row }">
                  <StatusTag v-bind="getTaskProgressMeta(row)" />
                </template>
                <template #taskActions="{ row }">
                  <TableAction
                    :actions="[
                      {
                        label: '任务详情',
                        type: 'link',
                        icon: ACTION_ICON.VIEW,
                        onClick: handleOpenHomeworkDetail.bind(null, row),
                      },
                    ]"
                  />
                </template>
              </DetailSheetGrid>
              <EmptyState
                v-else
                description="在课程详情的「实训任务」里给本班发布任务后，会显示在这里。"
                title="本班还没有实训任务"
              />
            </TabPane>

            <TabPane key="members" tab="班级成员">
              <div class="class-detail__toolbar">
                <span class="class-detail__count">
                  共 {{ memberList.length }} 名成员 · 班级平均分
                  {{ Number(scoreSummary.avgScore || 0).toFixed(1) }}
                </span>
              </div>
              <DetailSheetGrid
                v-if="memberList.length > 0"
                ref="memberGridRef"
                :columns="memberColumns"
                :data="memberList"
                :row-config="memberRowConfig"
                row-key="studentId"
              >
                <template #memberNameCell="{ row }">
                  <div class="class-detail__member">
                    <Avatar
                      :src="row.avatar"
                      class="class-detail__avatar"
                      size="small"
                    >
                      {{ (row.studentName || '-').slice(0, 1) }}
                    </Avatar>
                    <span>{{ row.studentName || '-' }}</span>
                  </div>
                </template>
              </DetailSheetGrid>
              <EmptyState
                v-else
                description="在班级管理里给班级添加学生后，会显示在这里。"
                title="班级里还没有学生"
              />
            </TabPane>

            <TabPane key="scores" tab="成绩分析">
              <div class="class-scores">
                <div class="stat-strip">
                  <div class="stat-strip__item">
                    <div class="stat-strip__label">班级均分</div>
                    <div class="stat-strip__value">
                      {{ Number(scoreSummary.avgScore || 0).toFixed(1) }}
                    </div>
                    <div class="stat-strip__meta">全体成员平均成绩</div>
                  </div>
                  <div class="stat-strip__item">
                    <div class="stat-strip__label">最高分</div>
                    <div class="stat-strip__value">
                      {{ Number(scoreSummary.highestScore || 0).toFixed(1) }}
                    </div>
                    <div class="stat-strip__meta">当前班级最好成绩</div>
                  </div>
                  <div class="stat-strip__item">
                    <div class="stat-strip__label">最低分</div>
                    <div class="stat-strip__value">
                      {{ Number(scoreSummary.lowestScore || 0).toFixed(1) }}
                    </div>
                    <div class="stat-strip__meta">当前班级最低成绩</div>
                  </div>
                  <div class="stat-strip__item">
                    <div class="stat-strip__label">及格率</div>
                    <div class="stat-strip__value">
                      {{ Number(scoreSummary.passRate || 0).toFixed(1) }}%
                    </div>
                    <div class="stat-strip__meta">已达到及格线的学生占比</div>
                  </div>
                </div>

                <section class="class-scores__section">
                  <h3 class="section-title">分数段分布</h3>
                  <DetailSheetGrid
                    v-if="scoreDistribution.length > 0"
                    ref="scoreGridRef"
                    :columns="scoreDistributionColumns"
                    :data="scoreDistribution"
                    row-key="range"
                  />
                  <EmptyState
                    v-else
                    compact
                    description="批改学生的提交后，这里会按分数段统计人数。"
                    title="还没有成绩数据"
                  />
                </section>
              </div>
            </TabPane>

            <TabPane key="messages" tab="留言问答">
              <div class="class-messages">
                <div v-if="canOperateClass" class="class-messages__composer">
                  <p class="class-messages__intro">
                    向指定学生发起提醒、答疑或跟进当前实训任务。
                  </p>
                  <Select
                    v-model:value="targetStudentId"
                    :options="studentOptions"
                    class="w-full"
                    placeholder="请选择接收学生"
                  />
                  <Input.TextArea
                    v-model:value="newQuestion"
                    :rows="4"
                    placeholder="请输入留言或答疑内容"
                  />
                  <div class="class-messages__actions">
                    <Button type="primary" @click="handleSendQuestion">
                      发送留言
                    </Button>
                  </div>
                </div>

                <Alert
                  v-else
                  message="你在只读查看这个班级（由其他老师认领或还没有人认领），只能查看留言记录，不能发送留言。"
                  show-icon
                  type="info"
                />

                <section class="class-messages__history">
                  <div class="class-messages__head">
                    <h3 class="section-title">留言记录</h3>
                    <span class="class-detail__count">
                      共 {{ messageList.length }} 条留言
                    </span>
                  </div>
                  <ul
                    v-if="messageList.length > 0"
                    class="class-messages__list"
                  >
                    <li
                      v-for="item in messageList"
                      :key="item.id"
                      class="class-message"
                    >
                      <Avatar
                        :src="item.senderAvatar"
                        class="class-detail__avatar"
                      >
                        {{ (item.senderName || '-').slice(0, 1) }}
                      </Avatar>
                      <div class="class-message__body">
                        <div class="class-message__head">
                          <span class="class-message__name">
                            {{ item.senderName || '-' }}
                          </span>
                          <span>{{ item.type === 1 ? '教师' : '学生' }}</span>
                          <span v-if="item.createTime">
                            {{ item.createTime }}
                          </span>
                        </div>
                        <p class="class-message__content">
                          {{ item.content }}
                        </p>
                      </div>
                    </li>
                  </ul>
                  <EmptyState
                    v-else
                    compact
                    description="学生的提问和你发出的留言都会显示在这里。"
                    title="还没有留言"
                  />
                </section>
              </div>
            </TabPane>
          </Tabs>
        </Card>
      </div>
    </Spin>
  </Page>
</template>

<style scoped>
.class-detail {
  display: flex;
  flex-direction: column;
  gap: var(--nus-section-gap);
}

.class-detail__meta {
  /* 页头说明两行：状态、指标与教师；学期与起止日期（辅助色）。读取完成前留出两行，页头高度不跳 */
  margin-top: 2px;
}

.class-detail__meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--nus-space-3);
  align-items: center;
  min-height: var(--nus-lh-dense);
  margin: 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.class-detail__meta-line:first-child {
  gap: 0;
  color: var(--nus-text-secondary);
}

.class-detail__meta-line:first-child > span + span::before {
  margin: 0 6px;
  color: var(--nus-text-tertiary);
  content: '·';
}

.class-detail__state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
}

.class-detail__toolbar {
  display: flex;
  align-items: center;
  min-height: 32px;
  margin-bottom: var(--nus-space-4);
}

.class-detail__count {
  font-size: var(--nus-fs-dense);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.class-detail__member {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
}

.class-detail__avatar {
  /* 头像用中性底，不再写死品牌色 */
  flex: none;
  color: var(--nus-text-secondary);
  background: var(--nus-bg-hover);
}

.class-scores {
  display: flex;
  flex-direction: column;
  gap: var(--nus-section-gap);
}

.class-scores__section {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-3);
}

.stat-strip {
  /* 卡片里的数字放进一块凹陷底，用分隔线隔开，不再套卡片 */
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius-lg);
}

.stat-strip__item {
  min-width: 0;
  padding: var(--nus-space-4) var(--nus-space-5);
}

.stat-strip__item + .stat-strip__item {
  border-left: 1px solid var(--nus-border-subtle);
}

.stat-strip__label {
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.stat-strip__value {
  margin-top: var(--nus-space-1);
  font-size: var(--nus-fs-display);
  font-weight: var(--nus-fw-semibold);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-display);
  color: var(--nus-text);
}

.stat-strip__meta {
  margin-top: 2px;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.class-messages {
  display: flex;
  flex-direction: column;
  gap: var(--nus-section-gap);
}

.class-messages__composer {
  /* 写留言放在凹陷块里，与下面的留言记录分开 */
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-3);
  padding: var(--nus-space-4);
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius-lg);
}

.class-messages__intro {
  margin: 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.class-messages__actions {
  display: flex;
  justify-content: flex-end;
}

.class-messages__head {
  display: flex;
  gap: var(--nus-space-3);
  align-items: baseline;
  margin-bottom: var(--nus-space-2);
}

.class-messages__list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.class-message {
  display: flex;
  gap: var(--nus-space-3);
  align-items: flex-start;
  padding: var(--nus-space-3) 0;
}

.class-message + .class-message {
  border-top: 1px solid var(--nus-border-subtle);
}

.class-message__body {
  flex: 1;
  min-width: 0;
}

.class-message__head {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--nus-space-2);
  align-items: baseline;
  font-size: var(--nus-fs-caption);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.class-message__name {
  font-size: var(--nus-fs-dense);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text);
}

.class-message__content {
  margin: var(--nus-space-1) 0 0;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

@media (width <= 768px) {
  .stat-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stat-strip__item:nth-child(odd) {
    border-left: 0;
  }

  .stat-strip__item:nth-child(n + 3) {
    border-top: 1px solid var(--nus-border-subtle);
  }
}
</style>
