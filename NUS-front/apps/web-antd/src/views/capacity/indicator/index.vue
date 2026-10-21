<script lang="ts" setup>
import type { IndicatorListRow } from './data';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CapacityIndicatorApi } from '#/api/capacity/indicator';

import { computed, nextTick, onMounted, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteIndicator, getIndicatorList } from '#/api/capacity/indicator';
import { EmptyState } from '#/components/empty-state';
import { $t } from '#/locales';
import { deleteRowConfirm } from '#/utils/confirm-copy';

import {
  FIRST_LEVEL,
  formatIndicatorWeight,
  resolveIndicatorLevelLabel,
  SECOND_LEVEL,
  useFirstLevelGridColumns,
  useSecondLevelGridColumns,
} from './data';
import Form from './modules/form.vue';
import WeightResetModal from './modules/weight-reset-modal.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});
const [WeightResetModalInstance, weightResetModalApi] = useVbenModal({
  connectedComponent: WeightResetModal,
  destroyOnClose: true,
});

const { hasAccessByCodes } = useAccess();
// 没有修改、删除权限时行内没有可点的操作，不显示空着的操作列（按钮本身仍按权限显示）
const showRowActions = hasAccessByCodes([
  'capacity:indicator:update',
  'capacity:indicator:delete',
]);

function withRowActions(
  columns: VxeTableGridOptions<IndicatorListRow>['columns'],
) {
  return showRowActions
    ? columns
    : columns?.filter((column) => column.slots?.default !== 'actions');
}

const allIndicators = ref<IndicatorListRow[]>([]);
const firstLevelIndicators = ref<IndicatorListRow[]>([]);
const secondLevelIndicators = ref<IndicatorListRow[]>([]);
const selectedFirstIndicatorId = ref<number>();
const GRID_HEIGHT = 520;

const secondGridAnimating = ref(false);
const selectedFirstIndicator = computed(() =>
  firstLevelIndicators.value.find(
    (item) => Number(item.id) === selectedFirstIndicatorId.value,
  ),
);
const firstLevelWeightTotal = computed(() =>
  firstLevelIndicators.value
    .reduce((total, item) => total + Number(item.weight || 0), 0)
    .toFixed(2),
);
const secondLevelWeightTotal = computed(() =>
  secondLevelIndicators.value
    .reduce((total, item) => total + Number(item.weight || 0), 0)
    .toFixed(2),
);

async function syncGridData() {
  await firstGridApi.grid.loadData(firstLevelIndicators.value);
  await secondGridApi.grid.loadData(secondLevelIndicators.value);
  if (selectedFirstIndicator.value) {
    await (firstGridApi.grid as any).setCurrentRow?.(
      selectedFirstIndicator.value,
    );
  }
}

function buildIndicatorRows(list: CapacityIndicatorApi.Indicator[]) {
  const parentNameMap = new Map<number, string>();
  const secondaryGroupMap = new Map<number, IndicatorListRow[]>();

  list.forEach((item) => {
    parentNameMap.set(Number(item.id), item.indicatorName);
  });
  list.forEach((item) => {
    if (Number(item.indicatorLevel) !== SECOND_LEVEL) {
      return;
    }
    const parentId = Number(item.parentIndicatorId);
    const children = secondaryGroupMap.get(parentId) ?? [];
    children.push({
      ...item,
      parentIndicatorName: parentNameMap.get(parentId) ?? '-',
    });
    secondaryGroupMap.set(parentId, children);
  });

  return list.map((item) => {
    const children = secondaryGroupMap.get(Number(item.id)) ?? [];
    return {
      ...item,
      parentIndicatorName:
        Number(item.parentIndicatorId) > 0
          ? (parentNameMap.get(Number(item.parentIndicatorId)) ?? '-')
          : '-',
      secondaryCount:
        Number(item.indicatorLevel) === FIRST_LEVEL
          ? children.length
          : undefined,
      secondaryWeightTotal:
        Number(item.indicatorLevel) === FIRST_LEVEL
          ? children.reduce(
              (total, child) => total + Number(child.weight || 0),
              0,
            )
          : undefined,
    };
  });
}

function syncSelectedIndicator() {
  if (
    selectedFirstIndicatorId.value &&
    firstLevelIndicators.value.some(
      (item) => Number(item.id) === selectedFirstIndicatorId.value,
    )
  ) {
    return;
  }
  selectedFirstIndicatorId.value = firstLevelIndicators.value[0]?.id;
}

function syncSecondLevelIndicators() {
  if (!selectedFirstIndicatorId.value) {
    secondLevelIndicators.value = [];
    return;
  }
  secondLevelIndicators.value = allIndicators.value.filter(
    (item) =>
      Number(item.indicatorLevel) === SECOND_LEVEL &&
      Number(item.parentIndicatorId) === selectedFirstIndicatorId.value,
  );
}

async function handleRefresh() {
  const list = await getIndicatorList();
  allIndicators.value = buildIndicatorRows(list || []);
  firstLevelIndicators.value = allIndicators.value.filter(
    (item) => Number(item.indicatorLevel) === FIRST_LEVEL,
  );
  syncSelectedIndicator();
  syncSecondLevelIndicators();
  await syncGridData();
}

function handleCreateRoot() {
  formModalApi
    .setData({
      indicatorLevel: FIRST_LEVEL,
      parentIndicatorId: 0,
      indicatorLevelText: resolveIndicatorLevelLabel(FIRST_LEVEL),
      status: 0,
      weight: 0,
    } as unknown as CapacityIndicatorApi.Indicator)
    .open();
}

function handleCreateChild() {
  if (!selectedFirstIndicator.value?.id) {
    message.warning('先新建一级指标，并在左侧选中它。');
    return;
  }
  formModalApi
    .setData({
      indicatorLevel: SECOND_LEVEL,
      parentIndicatorId: Number(selectedFirstIndicator.value.id),
      parentIndicatorName: selectedFirstIndicator.value.indicatorName,
      indicatorLevelText: resolveIndicatorLevelLabel(SECOND_LEVEL),
      status: 0,
      weight: 0,
    } as unknown as CapacityIndicatorApi.Indicator)
    .open();
}

function handleResetFirstLevelWeights() {
  if (firstLevelIndicators.value.length === 0) {
    message.warning('还没有一级指标，无需重设权重。');
    return;
  }
  weightResetModalApi
    .setData({
      title: '重设一级指标权重',
      indicatorLevel: FIRST_LEVEL,
      parentIndicatorId: 0,
      indicators: firstLevelIndicators.value,
    })
    .open();
}

function handleResetSecondLevelWeights() {
  if (!selectedFirstIndicator.value?.id) {
    message.warning('先在左侧选中一个一级指标。');
    return;
  }
  if (secondLevelIndicators.value.length === 0) {
    message.warning('这个一级指标下还没有二级指标，无需重设权重。');
    return;
  }
  weightResetModalApi
    .setData({
      title: `重设「${selectedFirstIndicator.value.indicatorName}」的二级指标权重`,
      indicatorLevel: SECOND_LEVEL,
      parentIndicatorId: Number(selectedFirstIndicator.value.id),
      indicators: secondLevelIndicators.value,
    })
    .open();
}

async function handleSelectFirst(row: IndicatorListRow) {
  if (selectedFirstIndicatorId.value === Number(row.id)) {
    return;
  }
  selectedFirstIndicatorId.value = Number(row.id);
  syncSecondLevelIndicators();
  await syncGridData();
  secondGridAnimating.value = false;
  await nextTick();
  secondGridAnimating.value = true;
  window.setTimeout(() => {
    secondGridAnimating.value = false;
  }, 280);
}

function handleEdit(row: CapacityIndicatorApi.Indicator) {
  formModalApi.setData(row).open();
}

async function handleDelete(row: CapacityIndicatorApi.Indicator) {
  const hideLoading = message.loading({
    content: `正在删除「${row.indicatorName}」…`,
    duration: 0,
  });
  try {
    await deleteIndicator(row.id!);
    message.success(`已删除「${row.indicatorName}」`);
    await handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 删除确认：标题写对象和动作，正文写后果（删除后不能恢复），确认按钮重复动作名 */
function deleteConfirmOf(row: IndicatorListRow) {
  return {
    ...deleteRowConfirm(
      '指标',
      row.indicatorName,
      '删除后不能恢复，需要时重新新建。',
    ),
    confirm: handleDelete.bind(null, row),
  };
}

// 表格右上角的刷新按钮：两张表的数据一起重新读取
const refreshToolbar = {
  refresh: true,
  refreshOptions: { queryMethod: () => handleRefresh() },
};

const [FirstGrid, firstGridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: withRowActions(useFirstLevelGridColumns()),
    height: GRID_HEIGHT,
    keepSource: true,
    rowClassName: 'indicator-row--selectable',
    rowConfig: {
      keyField: 'id',
      isCurrent: true,
      isHover: true,
    },
    pagerConfig: {
      enabled: false,
    },
    toolbarConfig: refreshToolbar,
  } as VxeTableGridOptions<IndicatorListRow>,
  gridEvents: {
    cellClick: ({ row }: { row: IndicatorListRow }) => {
      handleSelectFirst(row);
    },
  },
});

const [SecondGrid, secondGridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: withRowActions(useSecondLevelGridColumns()),
    height: GRID_HEIGHT,
    keepSource: true,
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    pagerConfig: {
      enabled: false,
    },
    toolbarConfig: refreshToolbar,
  } as VxeTableGridOptions<IndicatorListRow>,
});

onMounted(async () => {
  await handleRefresh();
});
</script>

<template>
  <Page
    auto-content-height
    description="一级指标的权重合计不超过 100%，每个一级指标下二级指标的权重合计也不超过 100%。"
    title="评价指标管理"
  >
    <FormModal @success="handleRefresh" />
    <WeightResetModalInstance @success="handleRefresh" />
    <div class="grid grid-cols-1 gap-4 xl:grid-cols-2">
      <div class="indicator-pane">
        <FirstGrid>
          <template #table-title>
            <h2 class="indicator-pane__title">一级指标</h2>
          </template>
          <template #top>
            <p class="indicator-pane__meta">
              {{ firstLevelIndicators.length }} 个 · 权重合计
              <span class="indicator-pane__number">
                {{ formatIndicatorWeight(firstLevelWeightTotal) }}
              </span>
              / 100%
            </p>
          </template>
          <template #toolbar-tools>
            <TableAction
              :actions="[
                {
                  label: '新建一级指标',
                  type: 'primary',
                  icon: ACTION_ICON.ADD,
                  auth: ['capacity:indicator:create'],
                  onClick: handleCreateRoot,
                },
                {
                  label: '重设指标权重',
                  type: 'default',
                  auth: ['capacity:indicator:update'],
                  onClick: handleResetFirstLevelWeights,
                },
              ]"
            />
          </template>
          <template #indicatorInfo="{ row }">
            <div class="indicator-cell">
              <div class="indicator-cell__name">{{ row.indicatorName }}</div>
              <div class="indicator-cell__details">
                {{ row.indicatorDetails || '未填写指标详情' }}
              </div>
            </div>
          </template>
          <template #actions="{ row }">
            <TableAction
              :actions="[
                {
                  label: $t('common.edit'),
                  type: 'link',
                  icon: ACTION_ICON.EDIT,
                  auth: ['capacity:indicator:update'],
                  onClick: handleEdit.bind(null, row),
                },
                {
                  label: $t('common.delete'),
                  type: 'link',
                  danger: true,
                  icon: ACTION_ICON.DELETE,
                  auth: ['capacity:indicator:delete'],
                  disabled: Number(row.secondaryCount || 0) > 0,
                  tooltip:
                    Number(row.secondaryCount || 0) > 0
                      ? '先删除它下面的二级指标，才能删除'
                      : undefined,
                  popConfirm: deleteConfirmOf(row),
                },
              ]"
            />
          </template>
          <template #empty>
            <EmptyState
              compact
              description="点「新建一级指标」添加，所有一级指标的权重合计不超过 100%。"
              title="还没有一级指标"
            />
          </template>
        </FirstGrid>
      </div>

      <div
        :class="{ 'indicator-switch-animating': secondGridAnimating }"
        class="indicator-pane"
      >
        <SecondGrid>
          <template #table-title>
            <h2 class="indicator-pane__title">
              二级指标
              <span
                v-if="selectedFirstIndicator"
                class="indicator-pane__parent"
              >
                {{ selectedFirstIndicator.indicatorName }}
              </span>
            </h2>
          </template>
          <template #top>
            <p v-if="selectedFirstIndicator" class="indicator-pane__meta">
              一级指标权重
              <span class="indicator-pane__number">
                {{ formatIndicatorWeight(selectedFirstIndicator.weight) }}
              </span>
              · {{ secondLevelIndicators.length }} 个 · 权重合计
              <span class="indicator-pane__number">
                {{ formatIndicatorWeight(secondLevelWeightTotal) }}
              </span>
              / 100%
            </p>
            <p v-else class="indicator-pane__meta">
              先在左侧新建并选中一级指标
            </p>
          </template>
          <template #toolbar-tools>
            <TableAction
              :actions="[
                {
                  label: '新建二级指标',
                  type: 'primary',
                  icon: ACTION_ICON.ADD,
                  auth: ['capacity:indicator:create'],
                  disabled: !selectedFirstIndicator,
                  onClick: handleCreateChild,
                },
                {
                  label: '重设指标权重',
                  type: 'default',
                  auth: ['capacity:indicator:update'],
                  disabled: !selectedFirstIndicator,
                  onClick: handleResetSecondLevelWeights,
                },
              ]"
            />
          </template>
          <template #indicatorInfo="{ row }">
            <div class="indicator-cell">
              <div class="indicator-cell__name">{{ row.indicatorName }}</div>
              <div class="indicator-cell__details">
                {{ row.indicatorDetails || '未填写指标详情' }}
              </div>
            </div>
          </template>
          <template #actions="{ row }">
            <TableAction
              :actions="[
                {
                  label: $t('common.edit'),
                  type: 'link',
                  icon: ACTION_ICON.EDIT,
                  auth: ['capacity:indicator:update'],
                  onClick: handleEdit.bind(null, row),
                },
                {
                  label: $t('common.delete'),
                  type: 'link',
                  danger: true,
                  icon: ACTION_ICON.DELETE,
                  auth: ['capacity:indicator:delete'],
                  popConfirm: deleteConfirmOf(row),
                },
              ]"
            />
          </template>
          <template #empty>
            <EmptyState
              v-if="selectedFirstIndicator"
              :title="`「${selectedFirstIndicator.indicatorName}」下还没有二级指标`"
              compact
              description="点「新建二级指标」添加，同一个一级指标下的权重合计不超过 100%。"
            />
            <EmptyState
              v-else
              compact
              description="二级指标挂在一级指标下面。先在左侧新建并选中一个一级指标。"
              kind="blocked"
              title="还没有选中一级指标"
            />
          </template>
        </SecondGrid>
      </div>
    </div>
  </Page>
</template>

<style scoped>
/* 切换一级指标时二级指标表的淡入 */
@keyframes indicator-switch-fade {
  0% {
    opacity: 0.4;
  }

  100% {
    opacity: 1;
  }
}

/* 表格面板：标题 15/600，下面一行是数量与权重合计（等宽数字） */
.indicator-pane {
  min-width: 0;
}

.indicator-pane__title {
  display: flex;
  gap: var(--nus-space-2);
  align-items: baseline;
  min-width: 0;
  padding-left: var(--nus-space-1);
  margin: 0;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
}

.indicator-pane__parent {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--nus-fs-dense);
  font-weight: var(--nus-fw-medium);
  color: var(--nus-text-secondary);
  white-space: nowrap;
}

.indicator-pane__meta {
  padding: 0 var(--nus-space-1);
  margin: 0 0 var(--nus-space-2);
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.indicator-pane__number {
  font-variant-numeric: tabular-nums;
  color: var(--nus-text-secondary);
}

.indicator-cell {
  min-width: 0;
  padding: var(--nus-space-1) 0;
}

.indicator-cell__name {
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.indicator-cell__details {
  margin-top: 2px;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

/* 一级指标的行可以点选：选中行由表格的选中底标出 */
.indicator-pane :deep(.indicator-row--selectable) {
  cursor: pointer;
}

/* 切换一级指标时，二级指标表淡入一次（减少动态效果时为 0） */
.indicator-switch-animating {
  animation: indicator-switch-fade var(--nus-dur) var(--nus-ease);
}
</style>
