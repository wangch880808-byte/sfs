<script lang="ts" setup>
import type { UndoOffer } from './use-grading-undo';

import { computed } from 'vue';

import { Button } from 'ant-design-vue';

import { getUndoText } from './use-grading-undo';

/**
 * 右栏底部操作栏：左侧打回，中间状态行（撤销倒计时、撤销失败原因，平时显示快捷键），右侧应用 AI 初评与保存。
 * 一个区域只有一个主按钮：保存并下一份。只读（不是布置者）时只留切换学生的快捷键说明。
 */
const props = defineProps<{
  aiApplying: boolean;
  canCorrect: boolean;
  formReady: boolean;
  /** 只读：不显示打回、应用 AI 初评与保存 */
  readOnly?: boolean;
  redoDisabled: boolean;
  redoLabel: string;
  submitting: boolean;
  undoError: string;
  undoing: boolean;
  undoOffer?: UndoOffer;
}>();

const emit = defineEmits<{
  aiApply: [];
  redo: [];
  save: [goNext: boolean];
  undo: [];
}>();

const SHORTCUT_HINT = '↑ ↓ 切换学生，Ctrl + Enter 保存并看下一份';
const READ_ONLY_SHORTCUT_HINT = '↑ ↓ 切换学生';

const undoText = computed(() =>
  props.undoOffer ? getUndoText(props.undoOffer) : undefined,
);
const saveDisabled = computed(() => !props.canCorrect || !props.formReady);
</script>

<template>
  <footer class="grading-footer">
    <Button
      v-if="!readOnly"
      :disabled="redoDisabled"
      :loading="submitting"
      danger
      @click="emit('redo')"
    >
      {{ redoLabel }}
    </Button>
    <p aria-live="polite" class="grading-status" role="status">
      <template v-if="undoText">
        <span class="grading-status__text">{{ undoText.text }}</span>
        <Button
          :loading="undoing"
          size="small"
          type="link"
          @click="emit('undo')"
        >
          {{ undoText.action }}
        </Button>
      </template>
      <span v-else-if="undoError">{{ undoError }}</span>
      <span v-else class="grading-status__hint">
        {{ readOnly ? READ_ONLY_SHORTCUT_HINT : SHORTCUT_HINT }}
      </span>
    </p>
    <div v-if="!readOnly" class="grading-footer__main">
      <Button
        :disabled="!canCorrect"
        :loading="aiApplying"
        @click="emit('aiApply')"
      >
        应用 AI 初评
      </Button>
      <Button
        :disabled="saveDisabled"
        :loading="submitting"
        @click="emit('save', false)"
      >
        保存
      </Button>
      <Button
        :disabled="saveDisabled"
        :loading="submitting"
        aria-keyshortcuts="Control+Enter Meta+Enter"
        type="primary"
        @click="emit('save', true)"
      >
        保存并下一份
      </Button>
    </div>
  </footer>
</template>

<style scoped>
.grading-footer {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: var(--nus-space-2);
  align-items: center;
  justify-content: space-between;
  padding: var(--nus-space-3) var(--nus-space-5);
  border-top: 1px solid var(--nus-border-subtle);
}

.grading-footer__main {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2);
}

.grading-status {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 0 var(--nus-space-1);
  align-items: center;
  min-width: 0;
  margin: 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.grading-status__text {
  color: var(--nus-text);
}

.grading-status__hint {
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}
</style>
