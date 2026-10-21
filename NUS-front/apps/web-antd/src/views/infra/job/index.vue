<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { InfraJobApi } from '#/api/infra/job';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { confirm, Page, useVbenModal, VbenLoading } from '@vben/common-ui';
import { InfraJobStatusEnum } from '@vben/constants';
import { downloadFileFromBlobPart, isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteJob,
  deleteJobList,
  exportJob,
  getJobPage,
  runJob,
  updateJobStatus,
} from '#/api/infra/job';
import { EmptyState } from '#/components/empty-state';
import { $t } from '#/locales';
import { deleteBatchConfirm, deleteRowConfirm } from '#/utils/confirm-copy';

import { useGridColumns, useGridFormSchema } from './data';
import Detail from './modules/detail.vue';
import Form from './modules/form.vue';

const { push } = useRouter();

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [DetailModal, detailModalApi] = useVbenModal({
  connectedComponent: Detail,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 导出表格 */
async function handleExport() {
  const data = await exportJob(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: '定时任务.xls', source: data });
}

/** 创建任务 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑任务 */
function handleEdit(row: InfraJobApi.Job) {
  formModalApi.setData(row).open();
}

/** 查看任务详情 */
function handleDetail(row: InfraJobApi.Job) {
  detailModalApi.setData({ id: row.id }).open();
}

/** 更新任务状态 */
async function handleUpdateStatus(row: InfraJobApi.Job) {
  const status =
    row.status === InfraJobStatusEnum.STOP
      ? InfraJobStatusEnum.NORMAL
      : InfraJobStatusEnum.STOP;
  const statusText = status === InfraJobStatusEnum.NORMAL ? '开启' : '暂停';

  await confirm({
    cancelText: '返回',
    confirmText: statusText,
    content:
      status === InfraJobStatusEnum.NORMAL
        ? '开启后任务会按 Cron 表达式定时执行，可以随时暂停。'
        : '暂停后任务不再按计划执行，可以随时重新开启。',
    icon: status === InfraJobStatusEnum.NORMAL ? 'info' : 'warning',
    title: `${statusText}定时任务「${row.name}」？`,
  });
  const hideLoading = message.loading({
    content: `正在${statusText}定时任务「${row.name}」…`,
    duration: 0,
  });
  try {
    await updateJobStatus(row.id!, status);
    message.success($t('ui.actionMessage.operationSuccess'));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 执行一次任务 */
async function handleTrigger(row: InfraJobApi.Job) {
  await confirm({
    cancelText: '返回',
    confirmText: '执行',
    content: '立即执行一次，不影响原有的执行计划，结果可以在执行日志里查看。',
    icon: 'info',
    title: `执行定时任务「${row.name}」？`,
  });
  const hideLoading = message.loading({
    content: `正在执行定时任务「${row.name}」…`,
    duration: 0,
  });
  try {
    await runJob(row.id!);
    message.success($t('ui.actionMessage.operationSuccess'));
  } finally {
    hideLoading();
  }
}

/** 跳转到任务日志 */
function handleLog(row?: InfraJobApi.Job) {
  push({
    name: 'InfraJobLog',
    query: row?.id ? { id: row.id } : {},
  });
}

/** 删除任务 */
async function handleDelete(row: InfraJobApi.Job) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteJob(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 批量删除任务 */
async function handleDeleteBatch() {
  await confirm({
    ...deleteBatchConfirm(
      '定时任务',
      checkedIds.value.length,
      '删除后不能恢复，这些任务不再执行。',
    ),
    icon: 'warning',
  });
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deletingBatch'),
    duration: 0,
  });
  try {
    await deleteJobList(checkedIds.value);
    checkedIds.value = [];
    message.success($t('ui.actionMessage.deleteSuccess'));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

const checkedIds = ref<number[]>([]);
/** 当前是否带筛选条件：决定空状态写“还没有”还是“没有符合条件的” */
const isFiltered = ref(false);
function handleRowCheckboxChange({ records }: { records: InfraJobApi.Job[] }) {
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
          isFiltered.value = Object.values(formValues ?? {}).some(
            (value) => !isEmpty(value),
          );
          return await getJobPage({
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
  } as VxeTableGridOptions<InfraJobApi.Job>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});
</script>

<template>
  <Page
    auto-content-height
    description="按 Cron 表达式定时执行后台任务，可以暂停、手动执行或查看执行日志。"
    title="定时任务"
  >
    <FormModal @success="handleRefresh" />
    <DetailModal />
    <Grid table-title="定时任务列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['任务']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['infra:job:create'],
              onClick: handleCreate,
            },
            {
              label: $t('ui.actionTitle.export'),
              type: 'default',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['infra:job:export'],
              onClick: handleExport,
            },
            {
              label: '执行日志',
              type: 'default',
              icon: 'lucide:history',
              auth: ['infra:job:query'],
              onClick: () => handleLog(undefined),
            },
            {
              label: $t('ui.actionTitle.deleteBatch'),
              type: 'default',
              danger: true,
              icon: ACTION_ICON.DELETE,
              disabled: isEmpty(checkedIds),
              auth: ['infra:job:delete'],
              onClick: handleDeleteBatch,
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['infra:job:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '开启',
              type: 'link',
              icon: 'lucide:circle-play',
              auth: ['infra:job:update'],
              ifShow: () => row.status === InfraJobStatusEnum.STOP,
              onClick: handleUpdateStatus.bind(null, row),
            },
            {
              label: '暂停',
              type: 'link',
              icon: 'lucide:circle-pause',
              auth: ['infra:job:update'],
              ifShow: () => row.status === InfraJobStatusEnum.NORMAL,
              onClick: handleUpdateStatus.bind(null, row),
            },
            {
              label: '执行',
              type: 'link',
              icon: 'lucide:clock-plus',
              auth: ['infra:job:trigger'],
              onClick: handleTrigger.bind(null, row),
            },
          ]"
          :drop-down-actions="[
            {
              label: $t('common.detail'),
              type: 'link',
              auth: ['infra:job:query'],
              onClick: handleDetail.bind(null, row),
            },
            {
              label: '日志',
              type: 'link',
              auth: ['infra:job:query'],
              onClick: handleLog.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              auth: ['infra:job:delete'],
              popConfirm: {
                ...deleteRowConfirm(
                  '定时任务',
                  row.name,
                  '删除后不能恢复，任务不再执行。',
                ),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
      <template #loading>
        <VbenLoading spinning text="正在读取定时任务…" />
      </template>
      <template #empty>
        <EmptyState
          v-if="isFiltered"
          compact
          description="调整或清空筛选条件后再试。"
          kind="filtered"
          title="没有符合条件的任务"
        />
        <EmptyState
          v-else
          compact
          description="新建定时任务后，会按 Cron 表达式定时执行。"
          title="还没有定时任务"
        />
      </template>
    </Grid>
  </Page>
</template>
