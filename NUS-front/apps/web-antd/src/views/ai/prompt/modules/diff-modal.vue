<script lang="ts" setup>
import type { PromptDiffData } from '../data';
import type { LineDiffType } from '../line-diff';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { diffLines, diffStats, toSideBySide } from '../line-diff';

const data = ref<PromptDiffData>();

const ops = computed(() =>
  data.value ? diffLines(data.value.defaultContent, data.value.content) : [],
);
const rows = computed(() => toSideBySide(ops.value));
const stats = computed(() => diffStats(ops.value));
const summaryText = computed(() => {
  const { added, removed } = stats.value;
  if (added === 0 && removed === 0) {
    return '与内置默认一致。';
  }
  return `相比内置默认：加了 ${added} 行，删了 ${removed} 行。`;
});
const title = computed(() =>
  data.value ? `对比「${data.value.name}」与内置默认` : '对比内置默认',
);

/** 行首记号：颜色之外再用符号区分删除与新增 */
function signOf(type: LineDiffType) {
  if (type === 'removed') return '−';
  if (type === 'added') return '+';
  return '';
}

const [Modal, modalApi] = useVbenModal({
  cancelText: '关闭',
  showConfirmButton: false,
  onOpenChange(isOpen: boolean) {
    data.value = isOpen ? modalApi.getData<PromptDiffData>() : undefined;
  },
});
</script>

<template>
  <Modal :title="title" class="w-4/5">
    <div v-if="data" class="prompt-diff">
      <p class="prompt-diff__summary">
        {{ summaryText }}
        <span class="prompt-diff__legend">
          <span class="prompt-diff__swatch is-removed" aria-hidden="true">
            −
          </span>
          只在内置默认里
          <span class="prompt-diff__swatch is-added" aria-hidden="true">
            +
          </span>
          只在{{ data.label }}里
        </span>
      </p>
      <div class="prompt-diff__table" role="table">
        <div class="prompt-diff__head" role="row">
          <span role="columnheader">内置默认</span>
          <span role="columnheader">{{ data.label }}</span>
        </div>
        <div
          v-for="(row, index) in rows"
          :key="index"
          class="prompt-diff__row"
          role="row"
        >
          <div
            v-for="(cell, side) in [row.left, row.right]"
            :key="side"
            :class="cell ? `is-${cell.type}` : 'is-blank'"
            class="prompt-diff__cell"
            role="cell"
          >
            <template v-if="cell">
              <span class="prompt-diff__no">{{ cell.no }}</span>
              <span class="prompt-diff__sign" aria-hidden="true">
                {{ signOf(cell.type) }}
              </span>
              <span class="prompt-diff__text">{{ cell.text }}</span>
            </template>
          </div>
        </div>
      </div>
    </div>
  </Modal>
</template>

<style scoped>
.prompt-diff {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-3);
}

.prompt-diff__summary {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-3);
  align-items: center;
  justify-content: space-between;
  margin: 0;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.prompt-diff__legend {
  display: inline-flex;
  gap: var(--nus-space-2);
  align-items: center;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-secondary);
}

.prompt-diff__swatch {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-family: var(--nus-font-mono);
  border-radius: var(--nus-radius-sm);
}

.prompt-diff__swatch.is-removed {
  color: var(--nus-danger);
  background: var(--nus-danger-tint);
}

.prompt-diff__swatch.is-added {
  color: var(--nus-success);
  background: var(--nus-success-tint);
}

.prompt-diff__table {
  max-height: 60vh;
  overflow: auto;
  font-family: var(--nus-font-mono);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius-lg);
}

.prompt-diff__head,
.prompt-diff__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.prompt-diff__head {
  position: sticky;
  top: 0;
  z-index: 1;
  font-family: var(--nus-font-sans);
  font-weight: var(--nus-fw-semibold);
  color: var(--nus-text-secondary);
  background: var(--nus-bg-sunken);
  border-bottom: 1px solid var(--nus-border-subtle);
}

.prompt-diff__head > span {
  padding: var(--nus-space-2) var(--nus-space-3);
}

.prompt-diff__head > span + span,
.prompt-diff__cell + .prompt-diff__cell {
  border-left: 1px solid var(--nus-border-subtle);
}

.prompt-diff__cell {
  display: grid;
  grid-template-columns: 40px 16px minmax(0, 1fr);
  min-width: 0;
  color: var(--nus-text);
}

.prompt-diff__cell.is-removed {
  background: var(--nus-danger-tint);
}

.prompt-diff__cell.is-added {
  background: var(--nus-success-tint);
}

.prompt-diff__cell.is-blank {
  background: var(--nus-bg-sunken);
}

.prompt-diff__no {
  padding-right: var(--nus-space-2);
  color: var(--nus-text-tertiary);
  text-align: right;
  user-select: none;
}

.prompt-diff__sign {
  user-select: none;
}

.prompt-diff__cell.is-removed .prompt-diff__sign {
  color: var(--nus-danger);
}

.prompt-diff__cell.is-added .prompt-diff__sign {
  color: var(--nus-success);
}

.prompt-diff__text {
  padding-right: var(--nus-space-3);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
</style>
