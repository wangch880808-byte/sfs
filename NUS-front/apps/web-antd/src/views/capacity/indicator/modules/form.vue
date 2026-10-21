<script lang="ts" setup>
import type { Rule } from 'ant-design-vue/es/form';

import type { CapacityIndicatorApi } from '#/api/capacity/indicator';

import { computed, nextTick, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Form,
  Input,
  InputNumber,
  message,
  Radio,
  RadioGroup,
  Slider,
} from 'ant-design-vue';

import {
  createIndicatorBatch,
  getIndicator,
  updateIndicator,
} from '#/api/capacity/indicator';
import { $t } from '#/locales';

import {
  FIRST_LEVEL,
  formatIndicatorWeight,
  resolveIndicatorLevelLabel,
  SECOND_LEVEL,
} from '../data';

interface IndicatorFormModalData extends CapacityIndicatorApi.Indicator {
  indicatorLevelText?: string;
  parentIndicatorName?: string;
}

interface IndicatorFormItem {
  id?: number;
  indicatorName?: string;
  indicatorDetails?: string;
  weight?: number;
  status?: number;
}

interface IndicatorFormState {
  indicatorLevel?: number;
  parentIndicatorId?: number;
  indicatorLevelText?: string;
  parentIndicatorName?: string;
  items: IndicatorFormItem[];
}

const emit = defineEmits(['success']);

const formRef = ref();
const formData = ref<IndicatorFormState>(createInitialState());
const statusOptions = getDictOptions(DICT_TYPE.COMMON_STATUS, 'number');
const weightSliderMarks = {
  0: '0',
  50: '50',
  100: '100',
};

const textRules = {
  indicatorDetails: [
    {
      max: 255,
      message: '指标详情长度不能超过 255 个字符',
      trigger: 'blur',
    },
  ] as Rule[],
  indicatorName: [
    { required: true, message: '请输入指标名称', trigger: 'blur' },
    {
      max: 255,
      message: '指标名称长度不能超过 255 个字符',
      trigger: 'blur',
    },
  ] as Rule[],
  status: [
    { required: true, message: '请选择状态', trigger: 'change' },
  ] as Rule[],
  weight: [
    { required: true, message: '请输入权重', trigger: 'change' },
    {
      validator: async (_rule: Rule, value: number) => {
        if (
          value === undefined ||
          value === null ||
          Number.isNaN(Number(value))
        ) {
          throw new Error('请输入权重');
        }
        if (Number(value) < 0 || Number(value) > 100) {
          throw new Error('权重范围必须在 0 到 100 之间');
        }
      },
      trigger: 'change',
    },
  ] as Rule[],
};

const isEditMode = computed(() => !!formData.value.items[0]?.id);
const batchWeightTotal = computed(() =>
  formData.value.items
    .reduce((total, item) => total + Number(item.weight || 0), 0)
    .toFixed(2),
);
/** 标题带上级别：新建一级指标、修改二级指标 */
const levelLabel = computed(() => {
  const label = resolveIndicatorLevelLabel(formData.value.indicatorLevel);
  return label === '-' ? '指标' : label;
});
const getTitle = computed(() => {
  return isEditMode.value
    ? $t('ui.actionTitle.edit', [levelLabel.value])
    : `新建${levelLabel.value}`;
});
const confirmText = computed(() => (isEditMode.value ? '保存' : '新建'));
/** 顶部说明：放在哪一级、权重上限；二级指标写明所属的一级指标 */
const formHint = computed(() => {
  const isSecondLevel = formData.value.indicatorLevel === SECOND_LEVEL;
  const scope = isSecondLevel
    ? `所属一级指标：${formData.value.parentIndicatorName || '-'}。同一个一级指标下，二级指标的权重合计不超过 100%。`
    : '所有一级指标的权重合计不超过 100%。';
  return isEditMode.value ? scope : `${scope}可以一次新建多个。`;
});

function createEmptyItem(): IndicatorFormItem {
  return {
    indicatorName: undefined,
    indicatorDetails: undefined,
    weight: 0,
    status: 0,
  };
}

function createInitialState(): IndicatorFormState {
  return {
    indicatorLevel: undefined,
    parentIndicatorId: 0,
    indicatorLevelText: '-',
    parentIndicatorName: undefined,
    items: [createEmptyItem()],
  };
}

function resetForm() {
  formData.value = createInitialState();
  formRef.value?.resetFields();
}

function buildBaseData(data: Partial<IndicatorFormModalData>) {
  const indicatorLevel = Number(data.indicatorLevel);
  return {
    indicatorLevel,
    indicatorLevelText: resolveIndicatorLevelLabel(indicatorLevel),
    parentIndicatorId:
      indicatorLevel === FIRST_LEVEL ? 0 : Number(data.parentIndicatorId || 0),
    parentIndicatorName:
      indicatorLevel === SECOND_LEVEL ? data.parentIndicatorName : undefined,
  };
}

const itemsRef = ref<HTMLElement>();

async function handleAppendItem() {
  formData.value.items.push(createEmptyItem());
  // 新增的一条在列表末尾，滚到能看见它（开了“减少动态效果”时直接跳过去）
  await nextTick();
  const reduceMotion = window.matchMedia?.(
    '(prefers-reduced-motion: reduce)',
  ).matches;
  itemsRef.value?.lastElementChild?.scrollIntoView({
    behavior: reduceMotion ? 'auto' : 'smooth',
    block: 'nearest',
  });
}

function handleRemoveItem(index: number) {
  if (formData.value.items.length <= 1) {
    return;
  }
  formData.value.items.splice(index, 1);
}

function formatWeightSliderTip(value?: number) {
  return value === undefined ? '' : `${Math.round(value)}%`;
}

function handleWeightSliderChange(
  item: IndicatorFormItem,
  value: [number, number] | number,
) {
  item.weight = Math.round(
    Array.isArray(value) ? Number(value[0] || 0) : Number(value || 0),
  );
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    await formRef.value?.validate();
    modalApi.lock();
    try {
      if (isEditMode.value) {
        const item = formData.value.items[0]!;
        await updateIndicator({
          id: item.id,
          indicatorName: item.indicatorName!,
          indicatorDetails: item.indicatorDetails,
          indicatorLevel: Number(formData.value.indicatorLevel),
          parentIndicatorId: Number(formData.value.parentIndicatorId || 0),
          weight: Number(item.weight || 0),
          status: Number(item.status),
        });
      } else {
        await createIndicatorBatch({
          indicatorLevel: Number(formData.value.indicatorLevel),
          parentIndicatorId: Number(formData.value.parentIndicatorId || 0),
          indicators: formData.value.items.map((item) => ({
            indicatorName: item.indicatorName!,
            indicatorDetails: item.indicatorDetails,
            weight: Number(item.weight || 0),
            status: Number(item.status),
          })),
        });
      }
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      resetForm();
      return;
    }
    const data = modalApi.getData<IndicatorFormModalData>();
    if (!data || !data.id) {
      formData.value = {
        ...createInitialState(),
        ...buildBaseData(data || {}),
      };
      return;
    }
    modalApi.lock();
    try {
      const indicator = await getIndicator(data.id);
      formData.value = {
        ...buildBaseData({
          ...indicator,
          parentIndicatorName: data.parentIndicatorName,
        }),
        items: [
          {
            id: indicator.id,
            indicatorName: indicator.indicatorName,
            indicatorDetails: indicator.indicatorDetails,
            weight: Number(indicator.weight || 0),
            status: indicator.status,
          },
        ],
      };
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal
    :confirm-text="confirmText"
    :title="getTitle"
    class="w-[680px] max-w-[92vw]"
  >
    <p class="indicator-form__hint">{{ formHint }}</p>
    <Form
      ref="formRef"
      :colon="false"
      :label-col="{ style: { width: '96px' } }"
      :model="formData"
      class="indicator-form"
    >
      <div ref="itemsRef" class="indicator-form__items">
        <section
          v-for="(item, index) in formData.items"
          :key="item.id ?? `indicator-${index}`"
          class="indicator-form__item"
        >
          <header v-if="!isEditMode" class="indicator-form__item-head">
            <h3 class="indicator-form__item-title">指标 {{ index + 1 }}</h3>
            <Button
              v-if="formData.items.length > 1"
              danger
              size="small"
              type="link"
              @click="handleRemoveItem(index)"
            >
              删除这条
            </Button>
          </header>

          <Form.Item
            :name="['items', index, 'indicatorName']"
            :rules="textRules.indicatorName"
            label="指标名称"
          >
            <Input
              v-model:value="item.indicatorName"
              :maxlength="255"
              placeholder="请输入指标名称"
            />
          </Form.Item>

          <Form.Item
            :name="['items', index, 'indicatorDetails']"
            :rules="textRules.indicatorDetails"
            label="指标详情"
          >
            <Input.TextArea
              v-model:value="item.indicatorDetails"
              :maxlength="255"
              :rows="3"
              placeholder="请输入指标详情"
            />
          </Form.Item>

          <Form.Item
            :name="['items', index, 'weight']"
            :rules="textRules.weight"
            label="权重"
          >
            <InputNumber
              v-model:value="item.weight"
              :max="100"
              :min="0"
              :precision="2"
              :step="0.01"
              addon-after="%"
              class="w-full"
              placeholder="请输入权重"
            />
            <Slider
              :marks="weightSliderMarks"
              :max="100"
              :min="0"
              :step="1"
              :tip-formatter="formatWeightSliderTip"
              :value="Number(item.weight || 0)"
              class="indicator-form__slider"
              @update:value="(value) => handleWeightSliderChange(item, value)"
            />
          </Form.Item>

          <Form.Item
            :name="['items', index, 'status']"
            :rules="textRules.status"
            label="状态"
          >
            <RadioGroup v-model:value="item.status">
              <Radio
                v-for="dict in statusOptions"
                :key="dict.value.toString()"
                :value="dict.value"
              >
                {{ dict.label }}
              </Radio>
            </RadioGroup>
          </Form.Item>
        </section>
      </div>
    </Form>
    <template #prepend-footer>
      <div v-if="!isEditMode" class="indicator-form__batch">
        <Button @click="handleAppendItem">
          <template #icon>
            <IconifyIcon icon="lucide:plus" />
          </template>
          再增加一个指标
        </Button>
        <span class="indicator-form__batch-total">
          已添加 {{ formData.items.length }} 个 · 权重合计
          <span class="indicator-form__number">
            {{ formatIndicatorWeight(batchWeightTotal) }}
          </span>
        </span>
      </div>
    </template>
  </Modal>
</template>

<style scoped>
.indicator-form__hint {
  margin: var(--nus-space-1) var(--nus-space-4) var(--nus-space-4);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.indicator-form {
  margin: 0 var(--nus-space-4);
}

/* 批量新增时每条指标一组，组与组之间用分隔线，不再套边框 */
.indicator-form__item + .indicator-form__item {
  padding-top: var(--nus-space-4);
  border-top: 1px solid var(--nus-border-subtle);
}

.indicator-form__item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--nus-space-3);
}

.indicator-form__item-title {
  margin: 0;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
}

.indicator-form__slider {
  margin: var(--nus-space-3) var(--nus-space-1) var(--nus-space-2);
}

/* 批量新增的入口与合计放在底栏左侧，列表再长也看得见 */
.indicator-form__batch {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-3);
  align-items: center;
  margin-right: auto;
}

.indicator-form__batch-total {
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.indicator-form__number {
  font-variant-numeric: tabular-nums;
  color: var(--nus-text-secondary);
}
</style>
