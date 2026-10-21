<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { InfraApiErrorLogApi } from '#/api/infra/api-error-log';

import { ref } from 'vue';

import { confirm, Page, useVbenModal, VbenLoading } from '@vben/common-ui';
import { InfraApiErrorLogProcessStatusEnum } from '@vben/constants';
import { downloadFileFromBlobPart, isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  exportApiErrorLog,
  getApiErrorLogPage,
  updateApiErrorLogStatus,
} from '#/api/infra/api-error-log';
import { EmptyState } from '#/components/empty-state';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Detail from './modules/detail.vue';

const [DetailModal, detailModalApi] = useVbenModal({
  connectedComponent: Detail,
  destroyOnClose: true,
});

/** 当前是否带筛选条件：决定空状态写“还没有”还是“没有符合条件的” */
const isFiltered = ref(false);

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 导出表格 */
async function handleExport() {
  const data = await exportApiErrorLog(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: 'API 错误日志.xls', source: data });
}

/** 查看 API 错误日志详情 */
function handleDetail(row: InfraApiErrorLogApi.ApiErrorLog) {
  detailModalApi.setData(row).open();
}

/** 处理已处理 / 已忽略的操作 */
async function handleProcess(id: number, processStatus: number) {
  const statusText =
    processStatus === InfraApiErrorLogProcessStatusEnum.DONE
      ? '已处理'
      : '已忽略';
  await confirm({
    cancelText: '返回',
    confirmText: `标记为${statusText}`,
    content: `处理状态会改为「${statusText}」，并记下处理人和处理时间；标记后不能改回未处理。`,
    icon: 'info',
    title: `把这条错误日志标记为${statusText}？`,
  });
  const hideLoading = message.loading({
    content: `正在标记为${statusText}…`,
    duration: 0,
  });
  try {
    await updateApiErrorLogStatus(id, processStatus);
    message.success($t('ui.actionMessage.operationSuccess'));
    handleRefresh();
  } finally {
    hideLoading();
  }
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
          return await getApiErrorLogPage({
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
  } as VxeTableGridOptions<InfraApiErrorLogApi.ApiErrorLog>,
});
</script>

<template>
  <Page
    auto-content-height
    description="记录接口出现的异常；查看后标记为已处理或已忽略。"
    title="错误日志"
  >
    <DetailModal @success="handleRefresh" />
    <Grid table-title="API 错误日志列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.export'),
              type: 'default',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['infra:api-error-log:export'],
              onClick: handleExport,
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.detail'),
              type: 'link',
              icon: ACTION_ICON.VIEW,
              auth: ['infra:api-error-log:query'],
              onClick: handleDetail.bind(null, row),
            },
            {
              label: '已处理',
              type: 'link',
              icon: 'lucide:check',
              auth: ['infra:api-error-log:update-status'],
              ifShow:
                row.processStatus === InfraApiErrorLogProcessStatusEnum.INIT,
              onClick: handleProcess.bind(
                null,
                row.id,
                InfraApiErrorLogProcessStatusEnum.DONE,
              ),
            },
            {
              label: '已忽略',
              type: 'link',
              icon: 'lucide:eye-off',
              auth: ['infra:api-error-log:update-status'],
              ifShow:
                row.processStatus === InfraApiErrorLogProcessStatusEnum.INIT,
              onClick: handleProcess.bind(
                null,
                row.id,
                InfraApiErrorLogProcessStatusEnum.IGNORE,
              ),
            },
          ]"
        />
      </template>
      <template #loading>
        <VbenLoading spinning text="正在读取错误日志…" />
      </template>
      <template #empty>
        <EmptyState
          v-if="isFiltered"
          compact
          description="调整或清空筛选条件后再试。"
          kind="filtered"
          title="没有符合条件的错误日志"
        />
        <EmptyState
          v-else
          compact
          description="接口出现异常时会自动记录在这里。"
          title="还没有错误日志"
        />
      </template>
    </Grid>
  </Page>
</template>
