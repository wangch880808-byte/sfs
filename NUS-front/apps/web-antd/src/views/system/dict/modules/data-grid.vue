<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemDictDataApi } from '#/api/system/dict/data';

import { computed, ref, watch } from 'vue';

import { confirm, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart, isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteDictData,
  deleteDictDataList,
  exportDictData,
  getDictDataPage,
} from '#/api/system/dict/data';
import { $t } from '#/locales';
import {
  deleteBatchConfirm,
  deleteRowConfirm,
  hasActiveFilters,
} from '#/utils/confirm-copy';
import GridEmpty from '#/views/system/modules/grid-empty.vue';

import {
  getColorTypeLabel,
  useDataGridColumns,
  useDataGridFormSchema,
} from '../data';
import DataForm from './data-form.vue';

const props = defineProps({
  dictType: {
    type: String,
    default: undefined,
  },
});

const [DataFormModal, dataFormModalApi] = useVbenModal({
  connectedComponent: DataForm,
  destroyOnClose: true,
});

/** 筛选条件是否生效：决定空状态的写法 */
const filtered = ref(false);
const emptyHint = computed(() =>
  props.dictType
    ? '点「新建字典数据」为这个类型设置取值。'
    : '先在左侧选择字典类型，再新建字典数据。',
);

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 导出表格 */
async function handleExport() {
  const data = await exportDictData(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: '字典数据.xls', source: data });
}

/** 创建字典数据 */
function handleCreate() {
  dataFormModalApi.setData({ dictType: props.dictType }).open();
}

/** 编辑字典数据 */
function handleEdit(row: SystemDictDataApi.DictData) {
  dataFormModalApi.setData(row).open();
}

/** 删除字典数据 */
async function handleDelete(row: SystemDictDataApi.DictData) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.label]),
    duration: 0,
  });
  try {
    await deleteDictData(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.label]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 批量删除字典数据 */
async function handleDeleteBatch() {
  await confirm(deleteBatchConfirm('字典数据', checkedIds.value.length));
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deletingBatch'),
    duration: 0,
  });
  try {
    await deleteDictDataList(checkedIds.value);
    checkedIds.value = [];
    message.success($t('ui.actionMessage.deleteSuccess'));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

const checkedIds = ref<number[]>([]);
function handleRowCheckboxChange({
  records,
}: {
  records: SystemDictDataApi.DictData[];
}) {
  checkedIds.value = records.map((item) => item.id!);
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    // 1280 以上左右两栏各占半屏，筛选项每行最多两个；更窄时上下排列，每行三个
    wrapperClass: 'grid-cols-1 md:grid-cols-3 xl:grid-cols-2',
    schema: useDataGridFormSchema(),
  },
  gridOptions: {
    columns: useDataGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          filtered.value = hasActiveFilters(formValues);
          return await getDictDataPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            dictType: props.dictType,
            ...formValues,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<SystemDictDataApi.DictData>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});

/** 监听 dictType 变化，重新查询 */
watch(
  () => props.dictType,
  () => {
    if (props.dictType) {
      handleRefresh();
    }
  },
);
</script>

<template>
  <div class="flex h-full flex-col">
    <DataFormModal @success="handleRefresh" />

    <Grid table-title="字典数据">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['字典数据']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['system:dict:create'],
              onClick: handleCreate,
            },
            {
              label: $t('ui.actionTitle.export'),
              type: 'default',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['system:dict:export'],
              onClick: handleExport,
            },
            {
              label: $t('ui.actionTitle.deleteBatch'),
              type: 'default',
              danger: true,
              icon: ACTION_ICON.DELETE,
              disabled: isEmpty(checkedIds),
              auth: ['system:dict:delete'],
              onClick: handleDeleteBatch,
            },
          ]"
        />
      </template>
      <template #colorType="{ row }">
        <span class="dict-cell-muted">
          {{ getColorTypeLabel(row.colorType) }}
        </span>
      </template>
      <template #cssClass="{ row }">
        <span v-if="row.cssClass" class="dict-color">
          <span
            aria-hidden="true"
            class="dict-color__swatch"
            :style="{ background: row.cssClass }"
          ></span>
          <span class="dict-color__value">{{ row.cssClass }}</span>
        </span>
        <span v-else class="dict-cell-muted">—</span>
      </template>
      <template #empty>
        <GridEmpty :filtered="filtered" :hint="emptyHint" subject="字典数据" />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['system:dict:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['system:dict:delete'],
              popConfirm: {
                ...deleteRowConfirm('字典数据', row.label),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </div>
</template>

<style scoped>
.dict-cell-muted {
  color: var(--nus-text-secondary);
}

.dict-color {
  display: inline-flex;
  gap: var(--nus-space-2);
  align-items: center;
  max-width: 100%;
}

.dict-color__swatch {
  flex: none;
  width: 10px;
  height: 10px;
  border: 1px solid var(--nus-border);
  border-radius: 50%;
}

.dict-color__value {
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: var(--nus-font-mono);
  font-size: var(--nus-fs-caption);
  color: var(--nus-text-secondary);
}
</style>
