<script lang="ts" setup>
import { CircleAlert } from '@vben/icons';

import { Alert, Button, Input, InputNumber } from 'ant-design-vue';

/**
 * 批改表单：评分与评语。校验结果写在对应输入框下面；保存冲突（他人已更新、状态已变）用一条横幅，
 * 写明发生了什么并给出“刷新这份提交”，已填的内容保留在表单里。
 */
defineProps<{
  blockedReason: string;
  commentError: string;
  conflictText: string;
  disabled: boolean;
  refreshing: boolean;
  scoreError: string;
}>();

const emit = defineEmits<{
  refresh: [];
}>();

const score = defineModel<null | number>('score', { required: true });
const comment = defineModel<string>('comment', { required: true });

/** 清空输入框时记为未填写，校验会提示 */
function handleScoreInput(value: null | number | string | undefined) {
  score.value =
    value === null || value === undefined || value === ''
      ? null
      : Number(value);
}
</script>

<template>
  <section aria-label="批改" class="grading-section">
    <h3 class="grading-section__title">批改</h3>
    <Alert
      v-if="conflictText"
      :message="conflictText"
      description="你填写的评分和评语还在表单里，刷新后核对再保存。"
      show-icon
      type="warning"
    >
      <template #action>
        <Button :loading="refreshing" size="small" @click="emit('refresh')">
          刷新这份提交
        </Button>
      </template>
    </Alert>
    <p v-if="blockedReason" class="grading-blocked">
      <CircleAlert aria-hidden="true" class="grading-blocked__icon" />
      {{ blockedReason }}
    </p>
    <div class="grading-field">
      <label class="grading-field__label" for="grading-score">评分</label>
      <div class="grading-score">
        <InputNumber
          id="grading-score"
          :value="score ?? undefined"
          @update:value="handleScoreInput"
          :aria-describedby="scoreError ? 'grading-score-error' : undefined"
          :aria-invalid="Boolean(scoreError)"
          :disabled="disabled"
          :max="100"
          :min="0"
          :precision="0"
          :status="scoreError ? 'error' : undefined"
          class="grading-score__input"
        />
        <span class="grading-caption">0～100 分</span>
      </div>
      <p
        v-if="scoreError"
        id="grading-score-error"
        class="grading-field__error"
        role="alert"
      >
        {{ scoreError }}
      </p>
    </div>
    <div class="grading-field">
      <label class="grading-field__label" for="grading-comment">评语</label>
      <Input.TextArea
        id="grading-comment"
        v-model:value="comment"
        :aria-describedby="commentError ? 'grading-comment-error' : undefined"
        :aria-invalid="Boolean(commentError)"
        :disabled="disabled"
        :rows="5"
        :status="commentError ? 'error' : undefined"
        placeholder="请输入评语"
      />
      <p
        v-if="commentError"
        id="grading-comment-error"
        class="grading-field__error"
        role="alert"
      >
        {{ commentError }}
      </p>
    </div>
  </section>
</template>

<style scoped>
.grading-blocked {
  display: flex;
  gap: var(--nus-space-2);
  align-items: flex-start;
  padding: var(--nus-space-2) var(--nus-space-3);
  margin: 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius);
}

.grading-blocked__icon {
  flex: none;
  width: 16px;
  height: 16px;
  margin-top: 2px;
  color: var(--nus-warning);
}

.grading-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.grading-field__label {
  font-size: var(--nus-fs-dense);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.grading-field__error {
  margin: 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-danger);
}

.grading-score {
  display: flex;
  gap: var(--nus-space-3);
  align-items: center;
}

.grading-score__input {
  width: 120px;
}
</style>
