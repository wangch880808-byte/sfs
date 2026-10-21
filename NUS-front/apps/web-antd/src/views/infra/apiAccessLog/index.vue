<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { InfraApiAccessLogApi } from '#/api/infra/api-access-log';

import { ref } from 'vue';

import { Page, useVbenModal, VbenLoading } from '@vben/common-ui';
import { downloadFileFromBlobPart, isEmpty } from '@vben/utils';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  exportApiAccessLog,
  getApiAccessLogPage,
} from '#/api/infra/api-access-log';
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
  const data = await exportApiAccessLog(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: 'API 访问日志.xls', source: data });
}

/** 查看 API 访问日志详情 */
function handleDetail(row: InfraApiAccessLogApi.ApiAccessLog) {
  detailModalApi.setData(row).open();
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
          return await getApiAccessLogPage({
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
  } as VxeTableGridOptions<InfraApiAccessLogApi.ApiAccessLog>,
});
</script>

<template>
  <Page
    auto-content-height
    description="记录每次接口请求的来源、耗时与结果，用于排查问题。"
    title="访问日志"
  >
    <DetailModal @success="handleRefresh" />
    <Grid table-title="API 访问日志列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.export'),
              type: 'default',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['infra:api-access-log:export'],
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
              auth: ['infra:api-access-log:query'],
              onClick: handleDetail.bind(null, row),
            },
          ]"
        />
      </template>
      <template #loading>
        <VbenLoading spinning text="正在读取访问日志…" />
      </template>
      <template #empty>
        <EmptyState
          v-if="isFiltered"
          compact
          description="调整或清空筛选条件后再试。"
          kind="filtered"
          title="没有符合条件的访问记录"
        />
        <EmptyState
          v-else
          compact
          description="接口被调用后会自动记录在这里。"
          title="还没有访问记录"
        />
      </template>
    </Grid>
  </Page>
</template>
