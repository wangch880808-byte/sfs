<script lang="ts" setup>
import type { StudentLoadError } from './load-error';
import type { FactItem } from './task-display';

import type { StudentHomeworkApi } from '#/api/edu/student/homework';

import { computed, onActivated, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Alert, Button, Card, Spin } from 'ant-design-vue';

import {
  getStudentHomeworkDetail,
  getStudentHomeworkHistory,
} from '#/api/edu/student/homework';
import { StatusTag } from '#/components/status-tag';
import {
  canSubmitHomework,
  formatDeadlineTime,
  isRedoDeadlineEffective,
  resolveEffectiveDeadline,
} from '#/utils/homework-deadline';
import {
  getHomeworkRecordStatusMeta,
  HOMEWORK_RECORD_STATUS,
} from '#/utils/status';
import { getHomeworkResultMeta } from '#/utils/status-student-homework';
import AttachmentPreview from '#/views/edu/homework/components/attachment-preview.vue';
import HomeworkHistoryTimeline from '#/views/edu/homework/components/homework-history-timeline.vue';

import FactList from './components/fact-list.vue';
import RichText from './components/rich-text.vue';
import TaskLoadState from './components/task-load-state.vue';
import { resolveStudentLoadError } from './load-error';
import { buildTaskMeta } from './task-display';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
/** 拿到任务后再渲染带页头的页面：页面按页头的实际高度计算内容区 */
const detail = ref<StudentHomeworkApi.HomeworkDetail>();
/** 任务没有读取成功时的原因（等级未解锁、不存在或其他失败） */
const loadError = ref<null | StudentLoadError>(null);
const historyItems = ref<StudentHomeworkApi.HistoryItem[]>([]);
const historyLoading = ref(false);
const historyError = ref(false);
let historyRequestId = 0;
let mounted = false;

const homeworkId = computed(() => Number(route.params.homeworkId));
const isGraded = computed(
  () => Number(detail.value?.status) === HOMEWORK_RECORD_STATUS.CORRECTED,
);
const isReturned = computed(
  () => Number(detail.value?.status) === HOMEWORK_RECORD_STATUS.REDO,
);
const isResubmitted = computed(
  () => Number(detail.value?.status) === HOMEWORK_RECORD_STATUS.RESUBMITTED,
);
const myScore = computed(() =>
  (isGraded.value || isReturned.value || isResubmitted.value) &&
  detail.value?.myScore !== undefined &&
  detail.value.myScore !== null
    ? Number(detail.value.myScore)
    : undefined,
);
const passScore = computed(() => Number(detail.value?.passScore));

/** 提交记录的状态（已批改、已打回、待批改） */
const statusMeta = computed(() =>
  getHomeworkRecordStatusMeta(detail.value?.status, 'student'),
);

/** 达标情况：已批改按及格线判断；已打回看还能不能重新提交；其余等教师批改 */
const resultMeta = computed(() =>
  getHomeworkResultMeta({
    status: detail.value?.status,
    myScore: myScore.value,
    passScore: detail.value?.passScore,
    canResubmit: !!detail.value && canSubmitHomework(detail.value),
  }),
);

const pageDescription = computed(() =>
  detail.value ? buildTaskMeta(detail.value) : '',
);

/** 打回或重新提交后，分数与评语是上一次批改留下的 */
const isPreviousResult = computed(
  () => isReturned.value || isResubmitted.value,
);

const commentPlaceholder = computed(() =>
  isGraded.value || isReturned.value
    ? '教师没有填写评语。'
    : '教师批改后，评语会显示在这里。',
);

/** 时间信息：提交、批改，以及截止时间（打回时为重做截止） */
const timeFacts = computed<FactItem[]>(() => {
  const task = detail.value;
  if (!task) return [];
  return [
    { label: '提交时间', value: formatDeadlineTime(task.submitTime) },
    { label: '批改时间', value: formatDeadlineTime(task.correctTime) },
    {
      label: isRedoDeadlineEffective(task) ? '重做截止' : '截止时间',
      value: formatDeadlineTime(resolveEffectiveDeadline(task)),
    },
  ];
});

/** 已上传附件的个数；没有附件时不展开附件预览 */
const attachmentCount = computed(() => {
  const raw = detail.value?.attachmentUrls;
  if (!raw) return 0;
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed)
      ? parsed.filter((item) => typeof item === 'string' && item.trim()).length
      : 0;
  } catch {
    return 0;
  }
});

async function loadHistory() {
  const requestId = ++historyRequestId;
  historyLoading.value = true;
  historyError.value = false;
  historyItems.value = [];
  try {
    // 记录区有自己的读取失败状态，不再弹全局提示
    const items = await getStudentHomeworkHistory(homeworkId.value, {
      silentError: true,
    });
    if (requestId === historyRequestId) historyItems.value = items ?? [];
  } catch {
    if (requestId === historyRequestId) historyError.value = true;
  } finally {
    if (requestId === historyRequestId) historyLoading.value = false;
  }
}

async function loadData() {
  if (!homeworkId.value) {
    router.back();
    return;
  }
  loading.value = true;
  loadError.value = null;
  void loadHistory();
  try {
    // 读取失败由页面写明原因与下一步（含等级未解锁），不再弹全局提示
    const d = await getStudentHomeworkDetail(homeworkId.value, {
      silentError: true,
    });
    detail.value = d;
  } catch (error) {
    loadError.value = resolveStudentLoadError(error);
  } finally {
    loading.value = false;
  }
}

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
    v-if="detail"
    :description="pageDescription"
    :title="detail.homeworkName"
    auto-content-height
  >
    <template #extra>
      <Button @click="router.back()">返回</Button>
    </template>

    <Spin :spinning="loading" tip="正在刷新批改结果…">
      <div class="page-stack">
        <Alert
          v-if="detail.historyOnly"
          message="这是往期任务：班级或课程已封存，成绩和评语只能查看。"
          show-icon
          type="info"
        />
        <Card title="批改结果">
          <template #extra>
            <StatusTag :label="statusMeta.label" :tone="statusMeta.tone" />
          </template>

          <div class="result-stats">
            <div class="result-stat">
              <div class="result-stat__label">
                {{ isPreviousResult ? '原成绩' : '我的成绩' }}
              </div>
              <div class="result-stat__value">
                <template v-if="myScore === undefined">—</template>
                <template v-else>
                  {{ myScore }}
                  <span class="result-stat__unit">/ {{ detail.score }} 分</span>
                </template>
              </div>
            </div>
            <div class="result-stat">
              <div class="result-stat__label">达标情况</div>
              <div class="result-stat__value result-stat__value--status">
                <StatusTag
                  :label="resultMeta.label"
                  :tone="resultMeta.tone"
                  emphasis
                />
              </div>
            </div>
            <div class="result-stat">
              <div class="result-stat__label">及格线</div>
              <div class="result-stat__value">
                <template v-if="Number.isFinite(passScore)">
                  {{ passScore }}
                  <span class="result-stat__unit">分</span>
                </template>
                <template v-else>—</template>
              </div>
            </div>
          </div>

          <section class="result-section">
            <h3 class="result-section__label">
              {{ isResubmitted ? '原评语' : '教师评语' }}
            </h3>
            <RichText v-if="detail.comment" :html="detail.comment" plain />
            <p v-else class="result-placeholder">{{ commentPlaceholder }}</p>
          </section>

          <FactList :items="timeFacts" class="result-section" inline />
        </Card>

        <Card title="成绩轨迹">
          <HomeworkHistoryTimeline
            :error="historyError"
            :items="historyItems"
            :loading="historyLoading"
            @retry="loadHistory"
          />
        </Card>

        <Card title="我的提交">
          <p v-if="detail.content" class="submission-text">
            {{ detail.content }}
          </p>
          <p v-else class="result-placeholder">没有填写文字内容。</p>

          <section class="result-section">
            <AttachmentPreview
              v-if="attachmentCount > 0"
              :attachment-urls="detail.attachmentUrls"
            />
            <template v-else>
              <h3 class="result-section__label">提交附件</h3>
              <p class="result-placeholder">没有上传附件。</p>
            </template>
          </section>
        </Card>
      </div>
    </Spin>
  </Page>

  <Page v-else auto-content-height title="成绩反馈">
    <TaskLoadState
      :error="loadError"
      :loading="loading"
      loading-text="正在读取成绩反馈…"
      @retry="loadData"
    />
  </Page>
</template>

<style scoped>
.result-stats {
  /* 成绩、达标情况、及格线并排，用竖线分隔；数字用正文色 */
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px 0;
}

.result-stat {
  min-width: 0;
  padding: 0 20px;
  border-left: 1px solid var(--nus-border-subtle);
}

.result-stat:first-child {
  padding-left: 0;
  border-left: 0;
}

.result-stat__label {
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.result-stat__value {
  display: flex;
  gap: 6px;
  align-items: baseline;
  min-height: var(--nus-lh-display);
  margin-top: 4px;
  font-size: var(--nus-fs-display);
  font-weight: var(--nus-fw-semibold);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-display);
  color: var(--nus-text);
}

.result-stat__value--status {
  align-items: center;
}

.result-stat__unit {
  font-size: var(--nus-fs-dense);
  font-weight: var(--nus-fw-regular);
  color: var(--nus-text-tertiary);
}

.result-section {
  padding-top: 16px;
  margin-top: 20px;
  border-top: 1px solid var(--nus-border-subtle);
}

.result-section__label {
  margin: 0 0 8px;
  font-size: var(--nus-fs-dense);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.result-placeholder {
  margin: 0;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text-tertiary);
}

.submission-text {
  margin: 0;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

@media (width < 640px) {
  .result-stats {
    grid-template-columns: minmax(0, 1fr);
  }

  .result-stat {
    padding: 0;
    border-left: 0;
  }
}
</style>
