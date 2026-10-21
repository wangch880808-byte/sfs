<script setup lang="ts">
import type { StatusTone } from '#/styles/theme/palette';

/**
 * 状态（V1.0.1.6 界面规范）：圆点＋文字，颜色只给圆点，文字保持正文色；需要强调时用浅底变体，文字跟状态色。
 * 状态与文案的对应集中在 utils/status.ts，页面不再各写一个 getStatusTag。
 */
interface Props {
  /** 状态的类别：neutral 未开始、running 进行中、waiting 待处理、done 已完成、failed 失败 */
  tone?: StatusTone;
  /** 状态文字；也可以用默认插槽 */
  label?: string;
  /** 需要强调时用浅底 */
  emphasis?: boolean;
}

withDefaults(defineProps<Props>(), {
  emphasis: false,
  label: '',
  tone: 'neutral',
});
</script>

<template>
  <span
    :class="[`nus-status--${tone}`, { 'is-emphasis': emphasis }]"
    class="nus-status"
  >
    <span aria-hidden="true" class="nus-status__dot"></span>
    <span class="nus-status__label">
      <slot>{{ label }}</slot>
    </span>
  </span>
</template>

<style scoped>
.nus-status {
  --status-color: var(--nus-status-neutral);
  --status-tint: var(--nus-status-neutral-tint);

  display: inline-flex;
  gap: 6px;
  align-items: center;
  max-width: 100%;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  vertical-align: middle;
  color: var(--nus-text);
  white-space: nowrap;
}

.nus-status--running {
  --status-color: var(--nus-status-running);
  --status-tint: var(--nus-status-running-tint);
}

.nus-status--waiting {
  --status-color: var(--nus-status-waiting);
  --status-tint: var(--nus-status-waiting-tint);
}

.nus-status--done {
  --status-color: var(--nus-status-done);
  --status-tint: var(--nus-status-done-tint);
}

.nus-status--failed {
  --status-color: var(--nus-status-failed);
  --status-tint: var(--nus-status-failed-tint);
}

.nus-status__dot {
  flex: none;
  width: 6px;
  height: 6px;
  background: var(--status-color);
  border-radius: 50%;
}

.nus-status__label {
  overflow: hidden;
  text-overflow: ellipsis;
}

.nus-status.is-emphasis {
  height: 22px;
  padding: 0 8px;
  color: var(--status-color);
  background: var(--status-tint);
  border-radius: var(--nus-radius-sm);
}
</style>
