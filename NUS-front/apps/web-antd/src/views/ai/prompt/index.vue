<script lang="ts" setup>
import type { PromptDiffData } from './data';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiPromptTemplateApi } from '#/api/ai/prompt';

import {
  Page,
  useVbenDrawer,
  useVbenModal,
  VbenLoading,
} from '@vben/common-ui';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getPromptTemplateList } from '#/api/ai/prompt';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import {
  getPromptActiveVersionLabel,
  getPromptDefaultMeta,
} from '#/utils/status-ai-governance';

import { useGridColumns } from './data';
import DiffModal from './modules/diff-modal.vue';
import HistoryDrawer from './modules/history-drawer.vue';
import VersionModal from './modules/version-modal.vue';

const [HistoryPanel, historyDrawerApi] = useVbenDrawer({
  connectedComponent: HistoryDrawer,
  destroyOnClose: true,
});

const [VersionDialog, versionModalApi] = useVbenModal({
  connectedComponent: VersionModal,
  destroyOnClose: true,
});

const [DiffDialog, diffModalApi] = useVbenModal({
  connectedComponent: DiffModal,
  destroyOnClose: true,
});

/** 刷新列表 */
function handleRefresh() {
  gridApi.query();
}

/** 最新版本比生效版本新时，提醒还有没启用的版本 */
function latestHint(row: AiPromptTemplateApi.Summary) {
  if (!row.latestVersion || row.latestVersion === row.activeVersion) {
    return '';
  }
  return `最新为第 ${row.latestVersion} 版`;
}

function handleHistory(row: AiPromptTemplateApi.Summary) {
  historyDrawerApi.setData(row).open();
}

function handleCreateVersion(row: AiPromptTemplateApi.Summary) {
  versionModalApi.setData(row).open();
}

function handleCompare(row: AiPromptTemplateApi.Summary) {
  diffModalApi
    .setData({
      name: row.name,
      defaultContent: row.defaultContent,
      content: row.content,
      label: row.usingDefault ? '当前生效（内置默认）' : '当前生效',
    } satisfies PromptDiffData)
    .open();
}

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    pagerConfig: {
      enabled: false,
    },
    proxyConfig: {
      ajax: {
        query: async () => await getPromptTemplateList(),
      },
    },
    rowConfig: {
      keyField: 'businessCode',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
    },
  } as VxeTableGridOptions<AiPromptTemplateApi.Summary>,
});
</script>

<template>
  <Page
    auto-content-height
    description="查看和修改各项 AI 业务的提示词，保存过的每一版都可以随时重新启用。"
    title="提示词管理"
  >
    <HistoryPanel @success="handleRefresh" />
    <VersionDialog @success="handleRefresh" />
    <DiffDialog />
    <Grid table-title="提示词列表">
      <template #activeVersion="{ row }">
        <span class="prompt-version-cell">
          {{ getPromptActiveVersionLabel(row.usingDefault, row.activeVersion) }}
          <span v-if="latestHint(row)" class="prompt-version-cell__meta">
            {{ latestHint(row) }}
          </span>
        </span>
      </template>
      <template #sameAsDefault="{ row }">
        <StatusTag v-bind="getPromptDefaultMeta(row.sameAsDefault)" />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '查看历史',
              type: 'link',
              icon: 'lucide:history',
              onClick: handleHistory.bind(null, row),
            },
            {
              label: '新建版本',
              type: 'link',
              icon: 'lucide:file-pen-line',
              auth: ['ai:prompt-template:create'],
              onClick: handleCreateVersion.bind(null, row),
            },
            {
              label: '对比默认',
              type: 'link',
              icon: 'lucide:git-compare',
              onClick: handleCompare.bind(null, row),
            },
          ]"
        />
      </template>
      <template #loading>
        <VbenLoading spinning text="正在读取提示词…" />
      </template>
      <template #empty>
        <EmptyState
          compact
          description="AI 业务用到的提示词会列在这里。刷新后仍为空时，请联系运维检查服务版本。"
          title="还没有提示词"
        />
      </template>
    </Grid>
  </Page>
</template>

<style scoped>
.prompt-version-cell {
  display: inline-flex;
  gap: var(--nus-space-2);
  align-items: baseline;
}

.prompt-version-cell__meta {
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-secondary);
}
</style>
