<script lang="ts" setup>
import type { EduHomeworkApi } from '#/api/edu/homework';

import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { confirm, Page } from '@vben/common-ui';
import { getDictLabel } from '@vben/hooks';

import { Alert, Button, message, Spin } from 'ant-design-vue';

import { getHomework, getHomeworkStatistics } from '#/api/edu/homework';
import {
  getHomeworkExtensionList,
  remindHomeworkUnsubmitted,
} from '#/api/edu/homework/deadline';
import { DictTag } from '#/components/dict-tag';
import { EmptyState } from '#/components/empty-state';
import { RichHtml } from '#/components/rich-html';

import { describeRemindResult } from './components/extension-display';
import HomeworkExtensionModal from './components/homework-extension-modal.vue';
import { getClassNameMap, getCourseNameMap, getIndicatorNameMap } from './data';
import { resolveHomeworkDetailActions } from './detail-actions';
import {
  CONFIRM_CANCEL_TEXT,
  formatDateTime,
  formatRate,
  gradingStrategyLabel,
} from './display';

const HOMEWORK_LIST_PATH = '/capacity/homework_manager/homework';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const homework = ref<EduHomeworkApi.Homework>();
const statistics = ref<EduHomeworkApi.HomeworkStatistics>();
const courseName = ref('-');
const className = ref('-');
const indicatorFirstName = ref('-');
const indicatorSecondName = ref('-');
/** 个别延期人数；没读到时为 undefined，不显示 */
const extensionCount = ref<number>();
const extensionOpen = ref(false);
const reminding = ref(false);

function resolveHomeworkTypeLabel(value?: number) {
  const label = getDictLabel('edu_homework_type', value);
  if (label) {
    return label;
  }
  if (value === 1) {
    return '平时任务';
  }
  return value === 2 ? '考核任务' : '-';
}

function resolveHomeworkLevelLabel(value?: number) {
  return getDictLabel('edu_homework_level', value) || '-';
}

const pageTitle = computed(
  () => homework.value?.homeworkName || '实训任务详情',
);

/** 页头说明：课程、班级与截止时间，状态在最前面 */
const metaItems = computed(() => {
  if (!homework.value) {
    return [];
  }
  return [
    courseName.value,
    className.value,
    `截止 ${formatDateTime(homework.value.deadline)}`,
  ].filter((item) => item && item !== '-');
});

/**
 * 只读（任务或它所在的班级、课程已封存）时只留查看，不显示批改、延期与催交；
 * 不是布置者时同样不显示这些操作，改为“查看提交”
 */
const actions = computed(() =>
  resolveHomeworkDetailActions(homework.value, statistics.value),
);

const infoItems = computed(() => [
  { label: '所属课程', value: courseName.value || '-' },
  { label: '关联班级', value: className.value || '-' },
  { label: '学期', value: homework.value?.semesterName || '-' },
  {
    label: '任务类型',
    value: resolveHomeworkTypeLabel(homework.value?.homeworkType),
  },
  {
    label: '任务等级',
    value: resolveHomeworkLevelLabel(homework.value?.homeworkLevel),
  },
  {
    label: '批改方式',
    value: gradingStrategyLabel(
      homework.value?.autoGrade,
      homework.value?.aiReviewMode,
    ),
  },
  { label: '满分值', value: `${homework.value?.score ?? 0} 分` },
  { label: '及格线', value: `${homework.value?.passScore ?? 0} 分` },
  { label: '一级指标', value: indicatorFirstName.value || '-' },
  { label: '二级指标', value: indicatorSecondName.value || '-' },
  { label: '截止时间', value: formatDateTime(homework.value?.deadline) },
  ...(extensionCount.value === undefined
    ? []
    : [
        {
          label: '个别延期',
          value:
            extensionCount.value > 0 ? `${extensionCount.value} 名学生` : '无',
        },
      ]),
  { label: '发布时间', value: formatDateTime(homework.value?.createTime) },
]);

const statItems = computed(() => {
  const totalCount = Number(statistics.value?.totalCount || 0);
  const submittedCount = Number(statistics.value?.submittedCount || 0);
  const correctedCount = Number(statistics.value?.correctedCount || 0);
  const passCount = Number(statistics.value?.passCount || 0);
  return [
    {
      key: 'total',
      label: '任务覆盖人数',
      value: totalCount,
      extra: '班级当前成员',
    },
    {
      key: 'submit',
      label: '已提交',
      value: submittedCount,
      extra: `提交率 ${formatRate(submittedCount, totalCount)}`,
    },
    {
      key: 'correct',
      label: '已批改',
      value: correctedCount,
      extra: `批改率 ${formatRate(correctedCount, totalCount)}`,
    },
    {
      key: 'pass',
      label: '达标人数',
      value: passCount,
      extra: `达标率 ${formatRate(passCount, totalCount)}`,
    },
  ];
});

async function loadData() {
  const id = Number(route.params.id);
  if (!id) {
    router.back();
    return;
  }
  loading.value = true;
  try {
    const [data, stats, courseMap, classMap, indicatorMap] = await Promise.all([
      getHomework(id),
      getHomeworkStatistics(id),
      getCourseNameMap(),
      getClassNameMap(),
      getIndicatorNameMap(),
    ]);
    homework.value = data;
    statistics.value = stats;
    courseName.value = courseMap.get(Number(data.courseId)) ?? '-';
    className.value = classMap.get(Number(data.classId)) ?? '-';
    indicatorFirstName.value =
      indicatorMap.get(Number(data.indicatorFirstId)) ?? '-';
    indicatorSecondName.value =
      indicatorMap.get(Number(data.indicatorSecondId)) ?? '-';
    void loadExtensionCount(id);
  } finally {
    loading.value = false;
  }
}

/** 个别延期人数只作补充信息，读取失败时不显示这一项 */
async function loadExtensionCount(id: number) {
  try {
    const list = await getHomeworkExtensionList(id, true);
    extensionCount.value = list?.length ?? 0;
  } catch {
    extensionCount.value = undefined;
  }
}

function handleExtensionChanged(count: number) {
  extensionCount.value = count;
}

/** 催交：给还没提交、也还没过实际截止时间的学生各发一条通知 */
async function handleRemind() {
  const current = homework.value;
  if (!current?.id || !actions.value.canRemind) {
    return;
  }
  try {
    await confirm({
      cancelText: CONFIRM_CANCEL_TEXT,
      confirmText: '催交',
      content:
        '会给还没提交、也还没过截止时间的学生各发一条通知。同一任务短时间内只能催交一次。',
      title: `催交「${current.homeworkName}」的未提交学生？`,
    });
  } catch {
    return;
  }
  reminding.value = true;
  try {
    const feedback = describeRemindResult(
      await remindHomeworkUnsubmitted(current.id),
    );
    message[feedback.type](feedback.text);
  } finally {
    reminding.value = false;
  }
}

/** 进入批改 */
function handleCorrect() {
  router.push({
    path: `/capacity/homework_manager/homework/correct/${route.params.id}`,
  });
}

function handleBackToList() {
  router.push({ path: HOMEWORK_LIST_PATH });
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <Page :title="pageTitle">
    <template v-if="homework" #description>
      <div class="homework-detail__meta">
        <DictTag :value="homework.status" type="edu_homework_status" />
        <span v-for="(item, index) in metaItems" :key="index">{{ item }}</span>
      </div>
    </template>
    <template v-if="homework" #extra>
      <div class="homework-detail__actions">
        <Button
          v-if="actions.canExtend"
          v-access:code="['edu:homework:extend']"
          @click="extensionOpen = true"
        >
          个别延期
        </Button>
        <Button
          v-if="actions.canRemind"
          v-access:code="['edu:homework:remind']"
          :loading="reminding"
          @click="handleRemind"
        >
          催交未提交学生
        </Button>
        <Button
          v-if="actions.canCorrect"
          v-access:code="['edu:homework-record:correct']"
          type="primary"
          @click="handleCorrect"
        >
          进入批改
        </Button>
        <Button
          v-if="actions.canViewSubmissions"
          v-access:code="['edu:homework-record:correct']"
          type="primary"
          @click="handleCorrect"
        >
          查看提交
        </Button>
      </div>
    </template>

    <Spin :spinning="loading" tip="正在读取任务详情…">
      <div v-if="homework" class="homework-detail">
        <Alert
          v-if="actions.notice"
          :message="actions.notice"
          show-icon
          type="info"
        />
        <div class="metric-grid">
          <div v-for="item in statItems" :key="item.key" class="metric-card">
            <div class="metric-card__label">{{ item.label }}</div>
            <div class="metric-card__value">{{ item.value }}</div>
            <div class="metric-card__meta">{{ item.extra }}</div>
          </div>
        </div>

        <div class="homework-detail__body">
          <section class="surface-panel homework-detail__panel">
            <h2 class="section-title">任务内容</h2>
            <RichHtml
              :html="homework.requirement"
              class="homework-detail__content"
            >
              <template #empty>
                <EmptyState
                  :description="
                    actions.readOnly || actions.viewOnly
                      ? ''
                      : '可以在任务列表点「修改」补充任务内容。'
                  "
                  compact
                  kind="empty"
                  title="还没有填写任务内容"
                />
              </template>
            </RichHtml>
          </section>

          <section class="surface-panel homework-detail__panel">
            <h2 class="section-title">任务信息</h2>
            <dl class="homework-detail__props">
              <div
                v-for="item in infoItems"
                :key="item.label"
                class="homework-detail__prop"
              >
                <dt>{{ item.label }}</dt>
                <dd>{{ item.value }}</dd>
              </div>
            </dl>
          </section>
        </div>
      </div>

      <div v-else-if="!loading" class="surface-panel">
        <EmptyState
          description="任务可能已删除，或网络暂时不通。可以重新读取，或回到任务列表。"
          kind="empty"
          title="没有读取到这个实训任务"
        >
          <template #action>
            <Button type="primary" @click="loadData">重新读取</Button>
            <Button @click="handleBackToList">返回任务列表</Button>
          </template>
        </EmptyState>
      </div>

      <div v-else class="homework-detail__placeholder"></div>
    </Spin>

    <HomeworkExtensionModal
      v-if="actions.canExtend"
      v-model:open="extensionOpen"
      :homework-deadline="homework?.deadline"
      :homework-id="homework?.id"
      :homework-name="homework?.homeworkName"
      @changed="handleExtensionChanged"
    />
  </Page>
</template>

<style scoped>
.homework-detail {
  display: flex;
  flex-direction: column;
  gap: var(--nus-section-gap);
}

/* 页头说明：状态、课程、班级、截止时间，用“·”隔开 */
.homework-detail__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  margin-top: var(--nus-space-1);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.homework-detail__meta > * + *::before {
  margin: 0 var(--nus-space-2);
  color: var(--nus-text-tertiary);
  content: '·';
}

/* 页头操作：一个主按钮，其余默认按钮 */
.homework-detail__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2);
  justify-content: flex-end;
}

/* 宽屏两栏：左边任务内容，右边任务信息 */
.homework-detail__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--nus-space-4);
  align-items: start;

  @media (width >= 1280px) {
    grid-template-columns: minmax(0, 1fr) 360px;
  }
}

.homework-detail__panel {
  padding: var(--nus-card-padding);
}

.homework-detail__content {
  margin-top: var(--nus-space-3);
}

.homework-detail__props {
  display: grid;
  row-gap: var(--nus-space-3);
  margin: var(--nus-space-4) 0 0;
}

.homework-detail__prop {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  column-gap: var(--nus-space-3);
}

.homework-detail__prop dt {
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-body);
  color: var(--nus-text-secondary);
}

.homework-detail__prop dd {
  margin: 0;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  overflow-wrap: anywhere;
}

/* 首次读取时给加载提示留出位置 */
.homework-detail__placeholder {
  min-height: 240px;
}
</style>
