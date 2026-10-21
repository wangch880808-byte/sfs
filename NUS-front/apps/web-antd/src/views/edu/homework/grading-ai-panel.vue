<script lang="ts" setup>
import type { AiReviewApi } from '#/api/edu/ai-review';

import { computed, onUnmounted, ref, watch } from 'vue';

import { Alert, Button, message } from 'ant-design-vue';

import { getAiCalibration, submitAiFeedback } from '#/api/edu/ai-review';
import { StatusTag } from '#/components/status-tag';
import {
  AI_REVIEW_TEXT,
  getAiCalibrationDisplay,
} from '#/utils/ai-calibration';
import { HOMEWORK_RECORD_STATUS } from '#/utils/status';
import { getAiRiskFlagMeta, isCorrectableStatus } from '#/utils/status-grading';

import {
  getAiDraftCommentLabel,
  getAiDraftCommentText,
  useAiPreReview,
} from './use-ai-pre-review';

/**
 * 批改工作台右栏的“AI 初评”区块：草案、风险标记、分项评分、学生可见评语建议，
 * 以及评分反馈与校准提示。草案只供参考，填入批改表单后由教师核对保存。
 * 只读（不是布置者）时只看草案，不显示触发、填入、忽略与评分反馈。
 */
const props = defineProps<{
  canAiReview: boolean;
  homeworkId: number;
  /** 只读：只看 AI 初评，不能触发、填入、忽略或反馈 */
  readOnly?: boolean;
  recordId?: number;
  recordStatus?: number;
  /** 保存、撤销后变化：重读这份提交的 AI 初评 */
  reloadToken: number;
}>();

const emit = defineEmits<{
  fill: [draft: { comment?: string; score?: number }];
}>();

const ai = useAiPreReview(() => props.recordId);
const {
  confidenceThreshold,
  draftNote,
  draftVisible,
  errorText,
  isLowConfidence,
  isReady,
  review,
  riskFlags,
  statusMeta,
  subScores,
  timedOut,
  triggering,
  triggerLabel,
} = ai;

const canCorrect = computed(() => isCorrectableStatus(props.recordStatus));
const calibration = ref<AiReviewApi.CalibrationRespVO>();
const calibrationLoading = ref(false);
const calibrationError = ref(false);
const calibrationHintOpen = ref(false);
let calibrationRequestId = 0;
const calibrationDisplay = computed(() =>
  calibration.value ? getAiCalibrationDisplay(calibration.value) : undefined,
);

function formatConfidence(value?: number) {
  return value === undefined ? '-' : `${Math.round(value * 100)}%`;
}

async function loadCalibration() {
  const homeworkId = props.homeworkId;
  const requestId = ++calibrationRequestId;
  if (!homeworkId || !props.canAiReview) {
    calibration.value = undefined;
    calibrationLoading.value = false;
    return;
  }
  calibrationLoading.value = true;
  calibrationError.value = false;
  try {
    const result = await getAiCalibration(homeworkId, { silentError: true });
    if (requestId === calibrationRequestId) calibration.value = result;
  } catch {
    if (requestId === calibrationRequestId) calibrationError.value = true;
  } finally {
    if (requestId === calibrationRequestId) calibrationLoading.value = false;
  }
}

watch(
  () => [props.homeworkId, props.canAiReview] as const,
  () => {
    calibration.value = undefined;
    calibrationError.value = false;
    calibrationHintOpen.value = false;
    void loadCalibration();
  },
  { immediate: true },
);

watch(
  () => props.recordId,
  (recordId) => {
    ai.reset();
    void ai.load(recordId, true);
  },
  { immediate: true },
);

watch(
  () => props.reloadToken,
  () => void ai.load(props.recordId, true),
);

function fillForm() {
  const draft = ai.draftForForm();
  if (!draft) return false;
  emit('fill', draft);
  return true;
}

function handleFill() {
  if (fillForm()) {
    message.success('AI 初评已填入批改表单，核对后再保存批改');
  }
}

async function handleTrigger() {
  if (props.readOnly) return;
  if (await ai.trigger()) message.success(AI_REVIEW_TEXT.draftTriggered);
}

async function handleReject() {
  if (props.readOnly) return;
  if (await ai.reject()) message.success('已忽略 AI 初评');
}

/** 底部“应用 AI 初评”：已生成就填入表单，否则触发生成 */
async function applyOrTrigger() {
  if (props.readOnly) return;
  if (isReady.value && fillForm()) {
    message.success('已把 AI 初评填入批改表单');
    return;
  }
  await handleTrigger();
}

async function handleFeedback(
  feedbackType: AiReviewApi.FeedbackReqVO['feedbackType'],
) {
  if (!props.recordId || props.readOnly) return;
  try {
    await submitAiFeedback({ feedbackType, homeworkRecordId: props.recordId });
    message.success('反馈已提交');
    await loadCalibration();
  } catch {
    // 全局已提示失败原因
  }
}

onUnmounted(() => {
  ai.stopPolling();
  ++calibrationRequestId;
});

defineExpose({ applyOrTrigger });
</script>

<template>
  <section aria-label="AI 初评" class="grading-section">
    <div class="grading-section__head">
      <h3 class="grading-section__title">AI 初评</h3>
      <StatusTag v-bind="statusMeta" />
      <Button
        v-if="!readOnly"
        :disabled="!canCorrect || ai.isPending.value"
        :loading="triggering"
        class="grading-section__action"
        size="small"
        @click="handleTrigger"
      >
        {{ triggerLabel }}
      </Button>
    </div>
    <p v-if="review?.aiConfidence !== undefined" class="grading-caption">
      置信度 {{ formatConfidence(review.aiConfidence) }}，阈值
      {{ formatConfidence(confidenceThreshold) }}
    </p>
    <Alert v-if="errorText" :message="errorText" show-icon type="warning">
      <template #action>
        <Button size="small" @click="ai.refresh()">
          {{ AI_REVIEW_TEXT.refresh }}
        </Button>
      </template>
    </Alert>
    <Alert
      v-else-if="timedOut"
      :message="AI_REVIEW_TEXT.pollingTimeout"
      show-icon
      type="info"
    >
      <template #action>
        <Button size="small" @click="ai.refresh()">
          {{ AI_REVIEW_TEXT.refresh }}
        </Button>
      </template>
    </Alert>
    <Alert
      v-else-if="isLowConfidence && isReady"
      message="这份 AI 初评置信度不足或有高风险标记，要逐项人工复核。"
      show-icon
      type="warning"
    />

    <dl v-if="draftVisible" class="grading-sunken grading-draft">
      <div>
        <dt>初评分数</dt>
        <dd class="grading-draft__score">
          {{ review?.aiScore ?? '-' }}
          <span class="grading-draft__full">/ 100</span>
        </dd>
      </div>
      <div>
        <dt>{{ getAiDraftCommentLabel(review?.status) }}</dt>
        <dd class="grading-text">{{ getAiDraftCommentText(review) }}</dd>
      </div>
      <div v-if="riskFlags.length > 0">
        <dt>风险标记</dt>
        <dd class="grading-draft__flags">
          <StatusTag
            v-for="flag in riskFlags"
            :key="flag"
            v-bind="getAiRiskFlagMeta(flag)"
          />
        </dd>
      </div>
      <div v-if="subScores.length > 0">
        <dt>分项评分</dt>
        <dd>
          <ul class="grading-subscores">
            <li
              v-for="(item, index) in subScores"
              :key="`${item.key || 'item'}-${index}`"
            >
              <div class="grading-subscores__head">
                <span>{{ item.key || `分项 ${index + 1}` }}</span>
                <span class="grading-subscores__score">
                  {{ item.score ?? '-' }} 分
                </span>
              </div>
              <p v-if="item.reason" class="grading-subscores__reason">
                {{ item.reason }}
              </p>
            </li>
          </ul>
        </dd>
      </div>
      <div v-if="review?.studentCommentSuggestion">
        <dt>学生可见评语建议</dt>
        <dd class="grading-text">{{ review.studentCommentSuggestion }}</dd>
      </div>
    </dl>
    <p v-else class="grading-note">{{ draftNote }}</p>

    <div v-if="isReady && !readOnly" class="grading-actions">
      <Button :disabled="!canCorrect" size="small" @click="handleFill">
        填入批改表单
      </Button>
      <Button size="small" @click="handleReject">忽略 AI 初评</Button>
    </div>

    <div
      v-if="!readOnly && recordStatus === HOMEWORK_RECORD_STATUS.CORRECTED"
      class="grading-actions"
    >
      <span class="grading-caption">AI 评分反馈</span>
      <Button size="small" @click="handleFeedback('strict')">加严</Button>
      <Button size="small" @click="handleFeedback('normal')">正常</Button>
      <Button size="small" @click="handleFeedback('loose')">放宽</Button>
    </div>

    <div v-if="canAiReview" class="grading-calibration">
      <span v-if="calibrationLoading">
        {{ AI_REVIEW_TEXT.calibrationLoading }}
      </span>
      <template v-else-if="calibrationDisplay">
        <span>{{ calibrationDisplay.text }}</span>
        <Button
          v-if="calibration?.hint"
          size="small"
          type="link"
          @click="calibrationHintOpen = !calibrationHintOpen"
        >
          {{
            calibrationHintOpen
              ? AI_REVIEW_TEXT.hideHint
              : AI_REVIEW_TEXT.showHint
          }}
        </Button>
        <p
          v-if="calibrationHintOpen && calibration?.hint"
          class="grading-sunken grading-text grading-calibration__hint"
        >
          {{ calibration.hint }}
        </p>
      </template>
      <span v-else-if="calibrationError">
        {{ AI_REVIEW_TEXT.calibrationLoadFailed }}
        <Button size="small" type="link" @click="loadCalibration">
          {{ AI_REVIEW_TEXT.retry }}
        </Button>
      </span>
    </div>
  </section>
</template>

<style scoped>
.grading-draft {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-3);
}

.grading-draft dt {
  margin-bottom: 2px;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.grading-draft dd {
  margin: 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text);
}

.grading-draft__score {
  font-weight: var(--nus-fw-semibold);
  font-variant-numeric: tabular-nums;
}

.grading-draft__full {
  font-weight: var(--nus-fw-regular);
  color: var(--nus-text-tertiary);
}

.grading-draft__flags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-1) var(--nus-space-3);
}

.grading-subscores {
  padding: 0;
  margin: 0;
  list-style: none;
}

.grading-subscores li + li {
  padding-top: var(--nus-space-2);
  margin-top: var(--nus-space-2);
  border-top: 1px solid var(--nus-border-subtle);
}

.grading-subscores__head {
  display: flex;
  gap: var(--nus-space-3);
  justify-content: space-between;
}

.grading-subscores__score {
  flex: none;
  font-variant-numeric: tabular-nums;
  color: var(--nus-text-secondary);
}

.grading-subscores__reason {
  margin: 2px 0 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.grading-calibration {
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.grading-calibration :deep(.ant-btn-link) {
  height: auto;
  padding: 0 var(--nus-space-1);
  font-size: inherit;
  line-height: inherit;
}

.grading-calibration__hint {
  margin-top: var(--nus-space-2);
  color: var(--nus-text-secondary);
}
</style>
