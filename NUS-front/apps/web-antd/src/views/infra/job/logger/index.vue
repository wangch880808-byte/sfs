<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { InfraJobLogApi } from '#/api/infra/job-log';

import { ref } from 'vue';
import { useRoute } from 'vue-router';

import { Page, useVbenModal, VbenLoading } from '@vben/common-ui';
import { downloadFileFromBlobPart, isEmpty } from '@vben/utils';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { exportJobLog, getJobLogPage } from '#/api/infra/job-log';
import { EmptyState } from '#/components/empty-state';
import { $t } from '#/locales';

import { useGridColumns, useGridFormSchema } from './data';
import Detail from './modules/detail.vue';

const { query } = useRoute();
/** 当前是否带筛选条件（含从某个任务进来）：决定空状态的写法 */
const isFiltered = ref(false);

const [DetailModal, detailModalApi] = useVbenModal({
  connectedComponent: Detail,
  destroyOnClose: true,
});

/** 导出表格 */
async function handleExport() {
  const data = await exportJobLog(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: '任务日志.xls', source: data });
}

/** 查看日志详情 */
function handleDetail(row: InfraJobLogApi.JobLog) {
  detailModalApi.setData({ id: row.id }).open();
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
          return await getJobLogPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
            jobId: query.id,
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
  } as VxeTableGridOptions<InfraJobLogApi.JobLog>,
});
</script>

<template>
  <Page
    auto-content-height
    description="查看定时任务每次执行的时间、耗时与结果。"
    title="调度日志"
  >
    <DetailModal />
    <Grid table-title="执行记录">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.export'),
              type: 'default',
              icon: ACTION_ICON.DOWNLOAD,
              auth: ['infra:job:export'],
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
              auth: ['infra:job:query'],
              onClick: handleDetail.bind(null, row),
            },
          ]"
        />
      </template>
      <template #loading>
        <VbenLoading spinning text="正在读取执行记录…" />
      </template>
      <template #empty>
        <EmptyState
          v-if="isFiltered"
          compact
          description="调整或清空筛选条件后再试。"
          kind="filtered"
          title="没有符合条件的执行记录"
        />
        <EmptyState
          v-else-if="query.id"
          compact
          description="这个任务按计划执行或手动执行后，会在这里留下记录。"
          title="这个任务还没有执行记录"
        />
        <EmptyState
          v-else
          compact
          description="定时任务按计划执行或手动执行后，会在这里留下记录。"
          title="还没有执行记录"
        />
      </template>
    </Grid>
  </Page>
</template>
