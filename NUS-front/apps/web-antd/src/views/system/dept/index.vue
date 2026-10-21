<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemDeptApi } from '#/api/system/dept';

import { computed, ref } from 'vue';

import { confirm, Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart, isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { exportOrganizationMasterData } from '#/api/edu/data-exchange';
import { deleteDept, deleteDeptList, getDeptList } from '#/api/system/dept';
import ImportWizard from '#/components/data-exchange/import-wizard.vue';
import { $t } from '#/locales';
import {
  DELETE_HINT,
  deleteBatchConfirm,
  deleteRowConfirm,
} from '#/utils/confirm-copy';
import GridEmpty from '#/views/system/modules/grid-empty.vue';

import { useGridColumns } from './data';
import Form from './modules/form.vue';

const props = withDefaults(defineProps<{ enableDataExchange?: boolean }>(), {
  enableDataExchange: false,
});

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 切换树形展开/收缩状态 */
const isExpanded = ref(true);
const importOpen = ref(false);
function handleExpand() {
  isExpanded.value = !isExpanded.value;
  gridApi.grid.setAllTreeExpand(isExpanded.value);
}

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

async function handleDataExchangeExport() {
  const source = await exportOrganizationMasterData();
  downloadFileFromBlobPart({ fileName: '组织班级数据.xlsx', source });
}

const toolbarActions = computed(() => [
  {
    label: $t('ui.actionTitle.create', ['组织']),
    type: 'primary' as const,
    icon: ACTION_ICON.ADD,
    auth: ['system:dept:create'],
    onClick: handleCreate,
  },
  ...(props.enableDataExchange
    ? [
        {
          label: '导出组织班级',
          type: 'default' as const,
          icon: ACTION_ICON.DOWNLOAD,
          auth: ['system:dept:query'],
          onClick: handleDataExchangeExport,
        },
        {
          label: '导入组织班级',
          type: 'default' as const,
          icon: ACTION_ICON.UPLOAD,
          auth: ['edu:data-exchange:import'],
          onClick: () => {
            importOpen.value = true;
          },
        },
      ]
    : []),
  {
    label: isExpanded.value ? '收起全部' : '展开全部',
    type: 'default' as const,
    onClick: handleExpand,
  },
  {
    label: $t('ui.actionTitle.deleteBatch'),
    type: 'default' as const,
    danger: true,
    icon: ACTION_ICON.DELETE,
    auth: ['system:dept:delete'],
    disabled: isEmpty(checkedIds.value),
    onClick: handleDeleteBatch,
  },
]);

/** 创建组织 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 添加下级组织 */
function handleAppend(row: SystemDeptApi.Dept) {
  formModalApi.setData({ parentId: row.id }).open();
}

/** 编辑组织 */
function handleEdit(row: SystemDeptApi.Dept) {
  formModalApi.setData(row).open();
}

/** 删除组织 */
async function handleDelete(row: SystemDeptApi.Dept) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteDept(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 批量删除组织 */
async function handleDeleteBatch() {
  await confirm(
    deleteBatchConfirm(
      '组织',
      checkedIds.value.length,
      `${DELETE_HINT}有下级组织的不能删除。`,
    ),
  );
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deletingBatch'),
    duration: 0,
  });
  try {
    await deleteDeptList(checkedIds.value);
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
  records: SystemDeptApi.Dept[];
}) {
  checkedIds.value = records.map((item) => item.id!);
}

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    pagerConfig: {
      enabled: false,
    },
    proxyConfig: {
      ajax: {
        query: async () => {
          return await getDeptList();
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
    treeConfig: {
      parentField: 'parentId',
      rowField: 'id',
      transform: true,
      expandAll: true,
      reserve: true,
    },
  } as VxeTableGridOptions<SystemDeptApi.Dept>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <ImportWizard
      v-if="props.enableDataExchange"
      v-model:open="importOpen"
      biz-type="ORGANIZATION"
      template-file-name="组织班级导入模板.xlsx"
      title="导入组织班级数据"
      @success="handleRefresh"
    />
    <Grid table-title="组织列表">
      <template #toolbar-tools>
        <TableAction :actions="toolbarActions" />
      </template>
      <template #empty>
        <GridEmpty
          hint="新建组织后，可以在下面建班级、加教师和学生。"
          subject="组织"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '新建下级',
              type: 'link',
              icon: ACTION_ICON.ADD,
              auth: ['system:dept:create'],
              onClick: handleAppend.bind(null, row),
            },
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['system:dept:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['system:dept:delete'],
              disabled: row.children && row.children.length > 0,
              popConfirm: {
                ...deleteRowConfirm('组织', row.name),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
