<script lang="ts" setup>
import type { EduHomeworkApi } from '#/api/edu/homework';
import type { AiPollingHandle } from '#/utils/ai-polling';
import type { GradingStrategy } from '#/views/edu/homework/data';

import { computed, onUnmounted, ref, watch } from 'vue';

import { getDictOptions } from '@vben/hooks';
import { IconifyIcon } from '@vben/icons';

import {
  Alert,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  RadioGroup,
  Select,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import {
  confirmAiHomeworkTasks,
  getAiHomeworkPreviewItem,
  getAiHomeworkPreviewTaskStatus,
  regenerateAiHomeworkTaskQuestion,
  startAiHomeworkPreviewTask,
} from '#/api/edu/homework';
import { StatusTag } from '#/components/status-tag';
import { useDirtyGuard } from '#/composables/use-dirty-guard';
import { startAiPolling } from '#/utils/ai-polling';
import { discardChangesConfirm } from '#/utils/confirm-copy';
import { getAiTaskPreviewItemStatusMeta } from '#/utils/status-course';
import {
  getSecondIndicatorOptions,
  GRADING_STRATEGY_OPTIONS,
  resolveGradingStrategyConfig,
  SUBMISSION_MODE_OPTIONS,
} from '#/views/edu/homework/data';

import { CLAIM_CLASS_FIRST_HINT, filterOperableClasses } from '../class-claim';
import { createTolerantRequest, mergeRegeneratedItem } from './ai-regenerate';
import { createClientRequestId } from './client-request-id';

interface ClassRelation {
  /** 公共课里能否给这个班布置任务（认领人）；旧版后端没有时按能 */
  canOperate?: boolean;
  classId: number;
  className?: string;
  semesterName?: string;
  status?: string;
}

interface Props {
  courseId: number;
  indicatorFirstId?: number;
  open: boolean;
  classRelations: ClassRelation[];
}

interface AutoTaskFormState {
  indicatorSecondId?: number;
  questionCount: number;
  classIds: number[];
  homeworkType: number;
  homeworkLevel?: number;
  gradingStrategy: GradingStrategy;
  submissionMode?: string;
  deadline?: dayjs.Dayjs;
  passScore: number;
  score: number;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  success: [];
  'update:open': [value: boolean];
}>();

const loading = ref(false);
const confirming = ref(false);
const regeneratingItemId = ref<string>();
const secondIndicatorOptions = ref<Array<{ label: string; value: number }>>([]);
const quickDeadlineDays = [1, 2, 3, 4, 5, 6, 7];
const activeQuickDeadline = ref<number>(3);
const homeworkTypeOptions = getDictOptions('edu_homework_type', 'number');
const homeworkLevelOptions = getDictOptions('edu_homework_level', 'number');
const formState = ref<AutoTaskFormState>(createInitialState());
const preview = ref<EduHomeworkApi.HomeworkAiCreatePreviewRespVO>();
const previewTaskId = ref<number>();
const previewTaskProgress = ref(0);
const previewTaskStatus =
  ref<EduHomeworkApi.HomeworkAiCreateTaskStatusRespVO['status']>();
let previewPollingTimer: ReturnType<typeof setTimeout> | undefined;
let regeneratePolling: AiPollingHandle | undefined;
const normalizedHomeworkLevelOptions = computed(() =>
  homeworkLevelOptions.map((item) => ({
    ...item,
    value: typeof item.value === 'boolean' ? Number(item.value) : item.value,
  })),
);

// 公共课只能给自己认领的班级布置任务：课程详情已按 canOperate 过滤，这里再兜底一次
const classOptions = computed(() =>
  filterOperableClasses(props.classRelations).map((item) => ({
    label: `${item.className || `班级 ${item.classId}`}（${item.status || '未知状态'}）`,
    value: item.classId,
  })),
);

const selectedClassCount = computed(() => formState.value.classIds.length);
// 重新生成在后台进行，关闭弹窗不影响；再次打开会继续等结果
const isBusy = computed(() => loading.value || confirming.value);
const modalTitle = computed(() =>
  preview.value ? 'AI 新建任务预览' : 'AI 新建任务',
);
const okText = computed(() => (preview.value ? '新建任务' : '生成预览'));

function createInitialState(): AutoTaskFormState {
  return {
    classIds: [],
    deadline: buildQuickDeadline(3),
    gradingStrategy: 'manual',
    homeworkLevel: 1,
    homeworkType: 1,
    passScore: 60,
    questionCount: 1,
    score: 100,
  };
}

function buildQuickDeadline(days: number) {
  return dayjs().add(days, 'day').hour(23).minute(59).second(59);
}

/** 预览已生成但还没新建成任务：关闭、点遮罩、离开页面前确认 */
const hasUnconfirmedPreview = computed(() => props.open && !!preview.value);
const dirtyGuard = useDirtyGuard(hasUnconfirmedPreview, {
  copy: discardChangesConfirm(
    '预览还没有新建成任务，关闭后你对题目的修改不会保留。',
  ),
});

async function handleClose() {
  if (isBusy.value) {
    return;
  }
  if (!(await dirtyGuard.confirmDiscard())) {
    return;
  }
  emit('update:open', false);
}

async function loadSecondIndicators() {
  secondIndicatorOptions.value = await getSecondIndicatorOptions(
    props.indicatorFirstId,
  );
}

function resetForm() {
  formState.value = createInitialState();
  preview.value = undefined;
  stopRegeneratePolling();
  regeneratingItemId.value = undefined;
  confirming.value = false;
  activeQuickDeadline.value = 3;
  stopPreviewPolling();
  previewTaskId.value = undefined;
  previewTaskProgress.value = 0;
  previewTaskStatus.value = undefined;
}

async function handleOpenChanged(open: boolean) {
  if (!open) {
    resetForm();
    secondIndicatorOptions.value = [];
    return;
  }
  await loadSecondIndicators();
  await recoverPreviewTask();
}

function handlePickQuickDeadline(days: number) {
  activeQuickDeadline.value = days;
  formState.value.deadline = buildQuickDeadline(days);
}

function validateForm() {
  if (!props.courseId) {
    message.warning('课程数据不完整，无法使用 AI 新建任务。请刷新后重试。');
    return false;
  }
  if (!props.indicatorFirstId) {
    message.warning(
      '这门课程还没有关联指标。先编辑课程选择关联指标，再使用 AI 新建任务。',
    );
    return false;
  }
  if (!formState.value.indicatorSecondId) {
    message.warning('请选择二级指标');
    return false;
  }
  if (!formState.value.questionCount || formState.value.questionCount < 1) {
    message.warning('题目数量至少为 1');
    return false;
  }
  if (formState.value.classIds.length === 0) {
    message.warning('请选择班级');
    return false;
  }
  if (!formState.value.homeworkLevel && formState.value.homeworkLevel !== 0) {
    message.warning('请选择任务等级');
    return false;
  }
  if (!formState.value.deadline) {
    message.warning('请选择截止时间');
    return false;
  }
  if (!formState.value.submissionMode) {
    message.warning('请选择提交模式');
    return false;
  }
  if (formState.value.passScore > formState.value.score) {
    message.warning('及格分数线不能超过满分值');
    return false;
  }
  return true;
}

function buildPreviewPayload(
  clientRequestId: string,
): EduHomeworkApi.HomeworkAiCreatePreviewReqVO {
  const gradingConfig = resolveGradingStrategyConfig(
    formState.value.gradingStrategy,
  );
  return {
    aiReviewMode: gradingConfig.aiReviewMode,
    autoGrade: gradingConfig.autoGrade,
    classIds: [
      ...new Set(formState.value.classIds.map(Number).filter(Boolean)),
    ],
    clientRequestId,
    courseId: props.courseId,
    deadline: formState.value.deadline!.format('YYYY-MM-DD HH:mm:ss'),
    homeworkLevel: Number(formState.value.homeworkLevel),
    homeworkType: Number(formState.value.homeworkType),
    indicatorSecondId: Number(formState.value.indicatorSecondId),
    passScore: Number(formState.value.passScore),
    questionCount: Number(formState.value.questionCount),
    score: Number(formState.value.score),
    submissionMode: formState.value.submissionMode!,
  };
}

async function handleGeneratePreview() {
  if (!validateForm()) {
    return;
  }
  loading.value = true;
  try {
    const payloadWithoutRequestId = buildPreviewPayload('');
    const fingerprint = JSON.stringify({
      ...payloadWithoutRequestId,
      clientRequestId: undefined,
    });
    const stored = readStoredPreviewTask();
    const clientRequestId =
      stored?.fingerprint === fingerprint
        ? stored.clientRequestId
        : createClientRequestId();
    const result = await startAiHomeworkPreviewTask(
      buildPreviewPayload(clientRequestId),
    );
    previewTaskId.value = result.taskId;
    previewTaskStatus.value = 'PENDING';
    previewTaskProgress.value = 0;
    storePreviewTask({ clientRequestId, fingerprint, taskId: result.taskId });
    await pollPreviewTask();
  } finally {
    loading.value = false;
  }
}

interface StoredPreviewTask {
  clientRequestId: string;
  fingerprint: string;
  taskId: number;
}

function previewStorageKey() {
  return `edu:ai-homework-preview:${props.courseId}`;
}

function readStoredPreviewTask(): StoredPreviewTask | undefined {
  try {
    const value = sessionStorage.getItem(previewStorageKey());
    return value ? JSON.parse(value) : undefined;
  } catch {
    return undefined;
  }
}

function storePreviewTask(task: StoredPreviewTask) {
  sessionStorage.setItem(previewStorageKey(), JSON.stringify(task));
}

function clearStoredPreviewTask() {
  sessionStorage.removeItem(previewStorageKey());
}

function stopPreviewPolling() {
  if (previewPollingTimer) {
    clearTimeout(previewPollingTimer);
    previewPollingTimer = undefined;
  }
}

async function recoverPreviewTask() {
  const stored = readStoredPreviewTask();
  if (!stored) return;
  previewTaskId.value = stored.taskId;
  await pollPreviewTask();
}

async function pollPreviewTask() {
  if (!previewTaskId.value || !props.open) return;
  stopPreviewPolling();
  try {
    const status = await getAiHomeworkPreviewTaskStatus(previewTaskId.value);
    previewTaskStatus.value = status.status;
    previewTaskProgress.value = status.progress;
    if (status.status === 'SUCCEEDED' && status.preview) {
      preview.value = status.preview;
      message.success(`已生成 ${status.preview.questionCount} 道 AI 新题预览`);
      const pending = status.preview.items.find((item) => item.regenerating);
      if (pending) {
        watchRegeneration(pending);
      }
      return;
    }
    if (status.status === 'FAILED') {
      clearStoredPreviewTask();
      message.error(
        status.errorMessage || 'AI 新题预览没有生成成功，请稍后重试。',
      );
      return;
    }
    previewPollingTimer = setTimeout(() => void pollPreviewTask(), 1500);
  } catch {
    previewPollingTimer = setTimeout(() => void pollPreviewTask(), 3000);
  }
}

function stopRegeneratePolling() {
  regeneratePolling?.stop();
  regeneratePolling = undefined;
}

function finishRegeneration(
  item: EduHomeworkApi.HomeworkAiCreatePreviewItem,
  latest: EduHomeworkApi.HomeworkAiCreatePreviewItem,
) {
  regeneratingItemId.value = undefined;
  if (mergeRegeneratedItem(item, latest)) {
    message.success('题目已重新生成');
    return;
  }
  message.warning(
    latest.errorMessage || '重新生成没有成功，可以再试一次或直接修改题目。',
  );
}

/** 重新生成在后台进行，轮询任务状态直到这道题有结果。 */
function watchRegeneration(item: EduHomeworkApi.HomeworkAiCreatePreviewItem) {
  const taskId = previewTaskId.value;
  if (!taskId) {
    return;
  }
  stopRegeneratePolling();
  regeneratingItemId.value = item.previewItemId;
  regeneratePolling = startAiPolling<
    EduHomeworkApi.HomeworkAiCreatePreviewItem | undefined
  >({
    request: createTolerantRequest(() =>
      getAiHomeworkPreviewItem(taskId, item.previewItemId),
    ),
    isPending: (latest) => !latest || !!latest.regenerating,
    onResult: (latest) => {
      if (latest && !latest.regenerating) {
        finishRegeneration(item, latest);
      }
    },
    onError: () => {
      regeneratingItemId.value = undefined;
      message.error(
        '读取重新生成结果没有成功：网络或服务暂时不可用。稍后重新打开预览查看，已改的内容还在。',
      );
    },
    onTimeout: () => {
      regeneratingItemId.value = undefined;
      message.warning(
        '重新生成还没有结果：AI 响应较慢。稍后重新打开预览查看，已改的内容还在。',
      );
    },
    initialIntervalMs: 1500,
    maxIntervalMs: 5000,
    maxDurationMs: 11 * 60_000,
  });
}

async function handleRegenerate(
  item: EduHomeworkApi.HomeworkAiCreatePreviewItem,
) {
  if (!preview.value || regeneratingItemId.value) {
    return;
  }
  regeneratingItemId.value = item.previewItemId;
  try {
    const result = await regenerateAiHomeworkTaskQuestion({
      previewId: preview.value.previewId,
      previewItemId: item.previewItemId,
    });
    if (result.regenerating) {
      item.regenerating = true;
      watchRegeneration(item);
      return;
    }
    finishRegeneration(item, result);
  } catch {
    // 全局已提示失败原因
    regeneratingItemId.value = undefined;
  }
}

async function handleConfirm() {
  if (!preview.value) {
    return;
  }
  if (regeneratingItemId.value) {
    message.info('有题目正在重新生成，等它完成后再新建任务。');
    return;
  }
  const invalidItem = preview.value.items.find(
    (item) => !item.homeworkName?.trim() || !item.requirement?.trim(),
  );
  if (invalidItem) {
    message.warning('请补全所有预览题目的标题和任务要求');
    return;
  }
  confirming.value = true;
  try {
    const resp = await confirmAiHomeworkTasks({
      previewId: preview.value.previewId,
      items: preview.value.items.map((item) => ({
        homeworkName: item.homeworkName!.trim(),
        previewItemId: item.previewItemId,
        requirement: item.requirement!.trim(),
      })),
    });
    message.success(
      `已确认 ${resp.questionCount} 道题，为 ${resp.classCount} 个班级新建 ${resp.createdCount} 个实训任务`,
    );
    clearStoredPreviewTask();
    emit('success');
    emit('update:open', false);
  } finally {
    confirming.value = false;
  }
}

async function handleOk() {
  if (preview.value) {
    await handleConfirm();
    return;
  }
  await handleGeneratePreview();
}

watch(
  () => props.open,
  (open) => {
    void handleOpenChanged(open);
  },
  { immediate: true },
);

onUnmounted(() => {
  stopPreviewPolling();
  stopRegeneratePolling();
});
</script>

<template>
  <Modal
    :open="open"
    :title="modalTitle"
    width="920px"
    :ok-text="okText"
    cancel-text="取消"
    :confirm-loading="loading || confirming"
    :ok-button-props="{ disabled: !!regeneratingItemId }"
    :mask-closable="!hasUnconfirmedPreview"
    @cancel="handleClose"
    @ok="handleOk"
  >
    <Form class="qb-task" layout="vertical">
      <template v-if="!preview">
        <p class="qb-task__hint">
          按课程关联的一级指标和你选的二级指标、任务等级从题库抽题，先生成可以修改的
          AI 新题预览；确认后才新建实训任务，新题不会写回题库。
        </p>

        <Alert
          v-if="
            previewTaskStatus === 'PENDING' || previewTaskStatus === 'RUNNING'
          "
          show-icon
          type="info"
          class="qb-task__progress"
          :message="
            previewTaskStatus === 'PENDING'
              ? '已排队，等待 AI 生成预览…'
              : 'AI 正在生成预览…'
          "
          :description="`当前进度 ${previewTaskProgress}%`"
        />

        <section class="qb-task__section">
          <h3 class="qb-task__section-title">抽题范围</h3>
          <div class="qb-task__grid">
            <Form.Item label="二级指标" required>
              <Select
                v-model:value="formState.indicatorSecondId"
                :options="secondIndicatorOptions"
                placeholder="请选择二级指标"
              />
            </Form.Item>
            <Form.Item label="题目数量" required>
              <InputNumber
                v-model:value="formState.questionCount"
                :min="1"
                :max="50"
                class="w-full"
                placeholder="请输入抽题数量"
              />
            </Form.Item>
          </div>
        </section>

        <section class="qb-task__section">
          <h3 class="qb-task__section-title">发布设置</h3>
          <div class="qb-task__grid">
            <Form.Item label="指定班级" required class="qb-task__full">
              <Select
                v-model:value="formState.classIds"
                mode="multiple"
                show-search
                option-filter-prop="label"
                :options="classOptions"
                max-tag-count="responsive"
                :placeholder="
                  classOptions.length > 0
                    ? '请选择班级（可多选）'
                    : `没有可选的班级，${CLAIM_CLASS_FIRST_HINT}`
                "
              />
              <p class="qb-task__help">
                已选择 {{ selectedClassCount }} 个班级，确认后每个班级都用同一批
                AI 新题新建任务。
              </p>
            </Form.Item>
            <Form.Item label="任务类型" required>
              <RadioGroup
                v-model:value="formState.homeworkType"
                option-type="button"
                button-style="solid"
                :options="homeworkTypeOptions"
              />
            </Form.Item>
            <Form.Item label="任务等级" required>
              <Select
                v-model:value="formState.homeworkLevel"
                :options="normalizedHomeworkLevelOptions"
                placeholder="请选择任务等级"
              />
            </Form.Item>
            <Form.Item label="截止时间" required class="qb-task__full">
              <DatePicker
                v-model:value="formState.deadline"
                show-time
                format="YYYY-MM-DD HH:mm:ss"
                class="w-full"
              />
              <div class="qb-task__quick">
                <span class="qb-task__quick-label">快捷截止</span>
                <Button
                  v-for="days in quickDeadlineDays"
                  :key="days"
                  size="small"
                  :aria-pressed="activeQuickDeadline === days"
                  :class="{ 'is-active': activeQuickDeadline === days }"
                  class="qb-task__quick-btn"
                  @click="handlePickQuickDeadline(days)"
                >
                  {{ days }} 天
                </Button>
              </div>
            </Form.Item>
          </div>
        </section>

        <section class="qb-task__section">
          <h3 class="qb-task__section-title">提交与批改</h3>
          <div class="qb-task__grid">
            <Form.Item label="提交模式" required>
              <Select
                v-model:value="formState.submissionMode"
                :options="SUBMISSION_MODE_OPTIONS"
                placeholder="请选择提交模式"
              />
            </Form.Item>
            <Form.Item label="批改方式" required>
              <Select
                v-model:value="formState.gradingStrategy"
                :options="GRADING_STRATEGY_OPTIONS"
                placeholder="请选择批改方式"
              />
            </Form.Item>
            <Form.Item label="及格分数线" required>
              <InputNumber
                v-model:value="formState.passScore"
                :min="0"
                :max="100"
                class="w-full"
              />
            </Form.Item>
            <Form.Item label="满分值" required>
              <InputNumber
                v-model:value="formState.score"
                :min="1"
                :max="100"
                class="w-full"
              />
            </Form.Item>
          </div>
        </section>
      </template>

      <template v-else>
        <p class="qb-task__hint">
          已生成 {{ preview.questionCount }} 道预览题，确认后为
          {{ preview.classCount }}
          个班级新建任务。可以修改标题和任务要求，也可以单独重新生成某一道题。
        </p>
        <section
          v-for="(item, index) in preview.items"
          :key="item.previewItemId"
          class="qb-preview"
        >
          <header class="qb-preview__head">
            <div class="min-w-0">
              <h3 class="qb-task__section-title">题目 {{ index + 1 }}</h3>
              <p class="qb-preview__source">
                源题：{{ item.sourceTitle || '-' }}
              </p>
            </div>
            <div class="qb-preview__actions">
              <StatusTag
                :label="getAiTaskPreviewItemStatusMeta(item.success).label"
                :tone="getAiTaskPreviewItemStatusMeta(item.success).tone"
              />
              <Button
                size="small"
                :disabled="
                  !!regeneratingItemId &&
                  regeneratingItemId !== item.previewItemId
                "
                :loading="regeneratingItemId === item.previewItemId"
                @click="handleRegenerate(item)"
              >
                {{
                  regeneratingItemId === item.previewItemId
                    ? '正在重新生成…'
                    : '重新生成'
                }}
              </Button>
            </div>
          </header>
          <div v-if="item.errorMessage" class="qb-preview__error" role="alert">
            <IconifyIcon
              class="qb-preview__error-icon"
              icon="lucide:circle-alert"
            />
            <div>
              <p class="qb-preview__error-text">{{ item.errorMessage }}</p>
              <p class="qb-task__help">
                可以重新生成，或补全标题和任务要求后再新建任务。
              </p>
            </div>
          </div>
          <Form.Item label="任务标题" required>
            <Input
              v-model:value="item.homeworkName"
              placeholder="请输入任务标题"
            />
          </Form.Item>
          <Form.Item label="任务要求" required>
            <Input.TextArea
              v-model:value="item.requirement"
              :auto-size="{ minRows: 4, maxRows: 8 }"
              placeholder="请输入任务要求"
            />
          </Form.Item>
        </section>
      </template>
    </Form>
  </Modal>
</template>

<style scoped>
.qb-task__hint {
  margin: 0 0 var(--nus-space-5);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.qb-task__progress {
  margin-bottom: var(--nus-space-5);
}

/* ---- 分组：区块标题 15/600，组与组之间用分隔线 ---- */
.qb-task__section + .qb-task__section {
  padding-top: var(--nus-space-4);
  border-top: 1px solid var(--nus-border-subtle);
}

.qb-task__section-title {
  margin: 0 0 var(--nus-space-3);
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
}

.qb-task__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: var(--nus-space-4);
}

.qb-task__full {
  grid-column: 1 / -1;
}

.qb-task :deep(.ant-form-item) {
  margin-bottom: var(--nus-space-4);
}

.qb-task :deep(.ant-form-item-label) {
  padding-bottom: var(--nus-space-1);
}

.qb-task__help {
  margin: var(--nus-space-1) 0 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

/* ---- 快捷截止：一组小按钮，选中的用主色边框与选中底 ---- */
.qb-task__quick {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2);
  align-items: center;
  margin-top: var(--nus-space-2);
}

.qb-task__quick-label {
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.qb-task__quick-btn {
  min-width: 52px;
  font-variant-numeric: tabular-nums;
}

.qb-task__quick-btn.is-active {
  color: var(--nus-primary);
  background: var(--nus-bg-selected);
  border-color: var(--nus-primary);
}

/* ---- 预览：每道题一组，用分隔线隔开 ---- */
.qb-preview + .qb-preview {
  padding-top: var(--nus-space-4);
  border-top: 1px solid var(--nus-border-subtle);
}

.qb-preview__head {
  display: flex;
  gap: var(--nus-space-3);
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--nus-space-3);
}

.qb-preview__head .qb-task__section-title {
  margin-bottom: 0;
}

.qb-preview__source {
  margin: 2px 0 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.qb-preview__actions {
  display: flex;
  flex: none;
  gap: var(--nus-space-3);
  align-items: center;
}

.qb-preview__error {
  display: flex;
  gap: var(--nus-space-2);
  align-items: flex-start;
  padding: var(--nus-space-2) var(--nus-space-3);
  margin-bottom: var(--nus-space-3);
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius);
}

.qb-preview__error-icon {
  flex: none;
  margin-top: 3px;
  color: var(--nus-warning);
}

.qb-preview__error-text {
  margin: 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text);
}
</style>
