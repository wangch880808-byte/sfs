<script lang="ts" setup>
import type { CapacityIndicatorApi } from '#/api/capacity/indicator';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { InputNumber, message, Slider } from 'ant-design-vue';

import { resetIndicatorWeights } from '#/api/capacity/indicator';

import { formatIndicatorWeight } from '../data';

interface WeightResetModalData {
  title: string;
  indicatorLevel: number;
  parentIndicatorId: number;
  indicators: CapacityIndicatorApi.Indicator[];
}

interface EditableWeightItem {
  id: number;
  indicatorName: string;
  indicatorDetails?: string;
  weight: number;
  /** 打开弹窗时的权重：改过的指标旁显示原值，想改回去时照着填 */
  originalWeight: number;
}

const emit = defineEmits(['success']);

const modalTitle = ref('重设指标权重');
const indicatorLevel = ref<number>(1);
const parentIndicatorId = ref<number>(0);
const items = ref<EditableWeightItem[]>([]);
const weightSliderMarks = {
  0: '0',
  50: '50',
  100: '100',
};

const totalWeight = computed(() =>
  items.value.reduce((total, item) => total + Number(item.weight || 0), 0),
);
/** 合计超过 100% 时后端不会保存；按两位小数比较，避免浮点误差 */
const isOverLimit = computed(
  () => Math.round(totalWeight.value * 100) > 10_000,
);
const changedCount = computed(
  () => items.value.filter((item) => isChanged(item)).length,
);

function isChanged(item: EditableWeightItem) {
  return (
    Math.round(Number(item.weight || 0) * 100) !==
    Math.round(item.originalWeight * 100)
  );
}

function formatWeightSliderTip(value?: number) {
  return value === undefined ? '' : `${Math.round(value)}%`;
}

function handleWeightSliderChange(
  item: EditableWeightItem,
  value: [number, number] | number,
) {
  item.weight = Math.round(
    Array.isArray(value) ? Number(value[0] || 0) : Number(value || 0),
  );
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    if (items.value.length === 0) {
      message.warning('没有可以重设权重的指标。');
      return;
    }
    modalApi.lock();
    try {
      await resetIndicatorWeights({
        indicatorLevel: indicatorLevel.value,
        parentIndicatorId: parentIndicatorId.value,
        indicators: items.value.map((item) => ({
          id: item.id,
          weight: Number(item.weight || 0),
        })),
      });
      message.success('指标权重已重设');
      emit('success');
      await modalApi.close();
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      items.value = [];
      return;
    }
    const data = modalApi.getData<WeightResetModalData>();
    modalTitle.value = data?.title ?? '重设指标权重';
    indicatorLevel.value = data?.indicatorLevel ?? 1;
    parentIndicatorId.value = data?.parentIndicatorId ?? 0;
    items.value = (data?.indicators ?? []).map((item) => ({
      id: Number(item.id),
      indicatorName: item.indicatorName,
      indicatorDetails: item.indicatorDetails,
      weight: Number(item.weight || 0),
      originalWeight: Number(item.weight || 0),
    }));
  },
});
</script>

<template>
  <Modal
    :title="modalTitle"
    cancel-text="返回"
    class="w-[720px] max-w-[92vw]"
    confirm-text="重设权重"
  >
    <div class="weight-reset">
      <p class="weight-reset__hint">
        保存后，学生的能力报告会按新权重计算；需要时可以再次重设，改过的指标下面会显示原来的权重。
      </p>

      <div class="weight-reset__summary">
        <span>{{ items.length }} 个指标</span>
        <span>
          权重合计
          <strong
            :class="{ 'is-over': isOverLimit }"
            class="weight-reset__total"
          >
            {{ formatIndicatorWeight(totalWeight) }}
          </strong>
          / 100%
        </span>
        <span v-if="changedCount > 0">已改 {{ changedCount }} 个</span>
      </div>
      <p v-if="isOverLimit" class="weight-reset__warning" role="alert">
        <IconifyIcon
          class="weight-reset__warning-icon"
          icon="lucide:circle-alert"
        />
        合计超过 100%，调低一些权重后才能保存。
      </p>

      <ul class="weight-reset__list">
        <li v-for="item in items" :key="item.id" class="weight-reset__item">
          <div class="weight-reset__info">
            <div class="weight-reset__name">{{ item.indicatorName }}</div>
            <div class="weight-reset__details">
              {{ item.indicatorDetails || '未填写指标详情' }}
            </div>
          </div>
          <div class="weight-reset__control">
            <InputNumber
              v-model:value="item.weight"
              :aria-label="`${item.indicatorName}的权重`"
              :max="100"
              :min="0"
              :precision="2"
              :step="0.01"
              addon-after="%"
              class="w-full"
            />
            <Slider
              :marks="weightSliderMarks"
              :max="100"
              :min="0"
              :step="1"
              :tip-formatter="formatWeightSliderTip"
              :value="Number(item.weight || 0)"
              class="weight-reset__slider"
              @update:value="(value) => handleWeightSliderChange(item, value)"
            />
            <div v-if="isChanged(item)" class="weight-reset__original">
              原来是 {{ formatIndicatorWeight(item.originalWeight) }}
            </div>
          </div>
        </li>
      </ul>
    </div>
  </Modal>
</template>

<style scoped>
.weight-reset {
  margin: 0 var(--nus-space-4);
}

.weight-reset__hint {
  margin: var(--nus-space-1) 0 var(--nus-space-4);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

/* 合计：凹陷底的一行，数字用等宽，超过上限时数字与提示用危险色 */
.weight-reset__summary {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2) var(--nus-space-5);
  align-items: baseline;
  padding: var(--nus-space-3) var(--nus-space-4);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius-lg);
}

.weight-reset__total {
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  font-variant-numeric: tabular-nums;
  color: var(--nus-text);
}

.weight-reset__total.is-over {
  color: var(--nus-danger);
}

.weight-reset__warning {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  margin: var(--nus-space-2) 0 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-danger);
}

.weight-reset__warning-icon {
  flex: none;
}

/* 指标列表：一行一个指标，用分隔线隔开 */
.weight-reset__list {
  max-height: 480px;
  padding: 0 var(--nus-space-1) 0 0;
  margin: var(--nus-space-2) 0 0;
  overflow-y: auto;
  list-style: none;
}

.weight-reset__item {
  display: flex;
  gap: var(--nus-space-6);
  align-items: flex-start;
  padding: var(--nus-space-4) 0;
}

.weight-reset__item + .weight-reset__item {
  border-top: 1px solid var(--nus-border-subtle);
}

.weight-reset__info {
  flex: 1;
  min-width: 0;
  padding-top: var(--nus-space-1);
}

.weight-reset__name {
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.weight-reset__details {
  margin-top: 2px;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

/* 右侧留出滑块刻度“100”的位置，避免被列表的滚动区裁掉 */
.weight-reset__control {
  flex: none;
  width: 232px;
  padding-right: var(--nus-space-2);
}

/* 滑块不设宽度，按左右外边距收在控件区里 */
.weight-reset__slider {
  width: auto;
}

.weight-reset__original {
  font-size: var(--nus-fs-caption);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}
</style>
