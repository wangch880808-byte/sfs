<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemNotifyTemplateApi } from '#/api/system/notify/template';

import { ref } from 'vue';

import { confirm, Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart, isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteNotifyTemplate,
  deleteNotifyTemplateList,
  exportNotifyTemplate,
  getNotifyTemplatePage,
} from '#/api/system/notify/template';
import { $t } from '#/locales';
import {
  deleteBatchConfirm,
  deleteRowConfirm,
  hasActiveFilters,
} from '#/utils/confirm-copy';
import GridEmpty from '#/views/system/modules/grid-empty.vue';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';
import SendForm from './modules/send-form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [SendModal, sendModalApi] = useVbenModal({
  connectedComponent: SendForm,
  destroyOnClose: true,
});

/** 筛选条件是否生效：决定空状态的写法 */
const filtered = ref(false);

/** 删除模板的后果 */
const TEMPLATE_DELETE_HINT =
  '删除后不能恢复，按相应模板发送的通知会发不出去，已发出的通知不受影响。';

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 导出表格 */
async function handleExport() {
  const data = await exportNotifyTemplate(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: '通知模板.xls', source: data });
}

/** 新建通知模板 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑通知模板 */
function handleEdit(row: SystemNotifyTemplateApi.NotifyTemplate) {
  formModalApi.setData(row).open();
}

/** 测试发送通知 */
function handleSend(row: SystemNotifyTemplateApi.NotifyTemplate) {
  sendModalApi.setData(row).open();
}

/** 删除通知模板 */
async function handleDelete(row: SystemNotifyTemplateApi.NotifyTemplate) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteNotifyTemplate(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 批量删除通知模板 */
async function handleDeleteBatch() {
  await confirm(
    deleteBatchConfirm(
      '通知模板',
      checkedIds.value.length,
      TEMPLATE_DELETE_HINT,
    ),
  );
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deletingBatch'),
    duration: 0,
  });
  try {
    await deleteNotifyTemplateList(checkedIds.value);
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
  records: SystemNotifyTemplateApi.NotifyTemplate[];
}) {
  checkedIds.value = records.map((item) => item.id!);
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          filtered.value = hasActiveFilters(formValues);
          return await getNotifyTemplatePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
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
  } as VxeTableGridOptions<SystemNotifyTemplateApi.NotifyTemplate>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <SendModal />
    <Grid table-title="通知模板列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['通知模板']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['system:notify-template:create'],
              onClick: handleCreate,
            },
            {
              label: $t('ui.actionTitle.export'),
              type: 'default',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['system:notify-template:export'],
              onClick: handleExport,
            },
            {
              label: $t('ui.actionTitle.deleteBatch'),
              type: 'default',
              danger: true,
              icon: ACTION_ICON.DELETE,
              disabled: isEmpty(checkedIds),
              auth: ['system:notify-template:delete'],
              onClick: handleDeleteBatch,
            },
          ]"
        />
      </template>
      <template #empty>
        <GridEmpty
          :filtered="filtered"
          hint="新建模板后，可以用「测试发送」给指定用户发一条。"
          subject="通知模板"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['system:notify-template:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '测试发送',
              type: 'link',
              icon: 'lucide:send',
              auth: ['system:notify-template:send-notify'],
              onClick: handleSend.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['system:notify-template:delete'],
              popConfirm: {
                ...deleteRowConfirm('通知模板', row.name, TEMPLATE_DELETE_HINT),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
