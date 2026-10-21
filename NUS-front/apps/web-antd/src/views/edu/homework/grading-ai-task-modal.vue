<script lang="ts" setup>
import { Alert, Button, Modal, Progress } from 'ant-design-vue';

import { StatusTag } from '#/components/status-tag';
import { AI_REVIEW_TEXT } from '#/utils/ai-calibration';

/** 批量生成 AI 初评的进度弹窗；轮询结束或超时后才显示关闭与刷新 */
defineProps<{
  completed: number;
  errorText: string;
  failed: number;
  polling: boolean;
  progress: number;
  progressText: string;
  timedOut: boolean;
  total: number;
}>();

const emit = defineEmits<{
  close: [];
  refresh: [];
}>();

const open = defineModel<boolean>('open', { required: true });
</script>

<template>
  <Modal
    v-model:open="open"
    :footer="null"
    :mask-closable="false"
    title="批量 AI 初评进度"
    @cancel="emit('close')"
  >
    <div class="grading-task">
      <Progress :percent="progress" type="circle" />
      <p class="grading-task__message">{{ progressText }}</p>
      <Alert
        v-if="timedOut && !errorText"
        :message="AI_REVIEW_TEXT.pollingTimeout"
        show-icon
        type="info"
      />
      <Alert v-if="errorText" :message="errorText" show-icon type="error" />
      <div class="grading-task__counts">
        <span>总计 {{ total }}</span>
        <span>完成 {{ completed }}</span>
        <StatusTag v-if="failed > 0" :label="`失败 ${failed}`" tone="failed" />
      </div>
      <div v-if="!polling" class="grading-task__actions">
        <Button v-if="timedOut" @click="emit('refresh')">
          {{ AI_REVIEW_TEXT.refresh }}
        </Button>
        <Button type="primary" @click="emit('close')">关闭</Button>
      </div>
    </div>
  </Modal>
</template>

<style scoped>
.grading-task {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-4);
  align-items: center;
  padding: var(--nus-space-4) 0;
}

.grading-task__message {
  margin: 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.grading-task__counts {
  display: flex;
  gap: var(--nus-space-4);
  align-items: center;
  font-size: var(--nus-fs-dense);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-dense);
  color: var(--nus-text);
}

.grading-task__actions {
  display: flex;
  gap: var(--nus-space-2);
}
</style>
