<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiModelModelApi } from '#/api/ai/model/model';

import { computed, ref } from 'vue';

import { Page, useVbenModal, VbenLoading } from '@vben/common-ui';
import { AiModelTypeEnum } from '@vben/constants';
import { IconifyIcon } from '@vben/icons';
import { isEmpty } from '@vben/utils';

import { Button, message, Modal, Select, Spin } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteModel,
  getBusinessModelBindings,
  getModelPage,
  getModelSimpleList,
  saveBusinessModelBindings,
  testModelConnection,
} from '#/api/ai/model/model';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import { $t } from '#/locales';
import { deleteRowConfirm } from '#/utils/confirm-copy';
import { getModelBindingMeta } from '#/utils/status-infra-ai';

import { mergeBindingDrafts } from './binding-state';
import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const bindingLoading = ref(false);
const bindingSaving = ref(false);
const bindings = ref<AiModelModelApi.BusinessModelBinding[]>([]);
const chatModels = ref<AiModelModelApi.Model[]>([]);
const savedModels = ref(new Map<string, number | undefined>());
const testingIds = ref(new Set<number>());
const unifiedModelId = ref<number>();
/** 当前是否带筛选条件：决定空状态写“还没有”还是“没有符合条件的” */
const isFiltered = ref(false);
let bindingRequestId = 0;
const modelOptions = computed(() =>
  chatModels.value.map((model) => ({
    label: model.name,
    value: model.id,
  })),
);
const hasChanges = computed(() =>
  bindings.value.some(
    (binding) =>
      binding.modelId !== savedModels.value.get(binding.businessCode),
  ),
);

function isAvailable(binding: AiModelModelApi.BusinessModelBinding) {
  return chatModels.value.some((model) => model.id === binding.modelId);
}

function bindingOptions(binding: AiModelModelApi.BusinessModelBinding) {
  if (binding.modelId && !isAvailable(binding)) {
    return [
      ...modelOptions.value,
      {
        label: `${binding.modelName || '原模型'}（已不可用，请重新选择）`,
        value: binding.modelId,
        disabled: true,
      },
    ];
  }
  return modelOptions.value;
}

async function loadBindings(preserveDrafts = true) {
  const requestId = ++bindingRequestId;
  bindingLoading.value = true;
  try {
    const [latest, models] = await Promise.all([
      getBusinessModelBindings(),
      getModelSimpleList(AiModelTypeEnum.CHAT),
    ]);
    if (requestId !== bindingRequestId) return;
    bindings.value = preserveDrafts
      ? mergeBindingDrafts(latest, bindings.value, savedModels.value)
      : latest;
    savedModels.value = new Map(
      latest.map((item) => [item.businessCode, item.modelId]),
    );
    chatModels.value = models;
    if (!models.some((model) => model.id === unifiedModelId.value)) {
      unifiedModelId.value = undefined;
    }
  } finally {
    if (requestId === bindingRequestId) bindingLoading.value = false;
  }
}

function applyToAll() {
  const model = chatModels.value.find(
    (item) => item.id === unifiedModelId.value,
  );
  if (!model) return;
  bindings.value = bindings.value.map((binding) => ({
    ...binding,
    modelId: model.id,
    modelName: model.name,
  }));
}

async function handleSaveBindings() {
  if (bindingSaving.value || bindingLoading.value) return;
  const invalid = bindings.value.find((binding) => !isAvailable(binding));
  if (invalid) {
    message.warning(
      `「${invalid.businessName}」还没有可用的模型，选好后再保存`,
    );
    return;
  }
  bindingSaving.value = true;
  try {
    await saveBusinessModelBindings(
      bindings.value.map((binding) => ({
        businessCode: binding.businessCode,
        modelId: binding.modelId!,
      })),
    );
    message.success('全部业务模型绑定已保存');
    await loadBindings(false);
  } finally {
    bindingSaving.value = false;
  }
}

async function handleTest(row: AiModelModelApi.Model) {
  if (testingIds.value.has(row.id)) return;
  testingIds.value.add(row.id);
  try {
    const result = await testModelConnection(row.id);
    const content = `${result.message}（耗时 ${(result.durationMs / 1000).toFixed(2)} 秒）`;
    if (result.success) {
      message.success(`${row.name}：${content}`, 5);
    } else {
      Modal.error({
        title: `${row.name}：测试未通过`,
        content: `${content}。检查 API 密钥和模型标识后再测试。`,
      });
    }
  } finally {
    testingIds.value.delete(row.id);
  }
}

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 刷新表格 */
async function handleRefresh() {
  await gridApi.query();
}

/** 创建模型配置 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑模型配置 */
function handleEdit(row: AiModelModelApi.Model) {
  formModalApi.setData(row).open();
}

/** 删除模型配置 */
async function handleDelete(row: AiModelModelApi.Model) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteModel(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    await handleRefresh();
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
          const [pageResult] = await Promise.all([
            getModelPage({
              pageNo: page.currentPage,
              pageSize: page.pageSize,
              ...formValues,
            }),
            loadBindings(),
          ]);
          return pageResult;
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
  } as VxeTableGridOptions<AiModelModelApi.Model>,
});
</script>

<template>
  <Page
    auto-content-height
    description="配置可用的大模型，并为各项业务指定使用哪个模型。"
    title="模型配置"
  >
    <FormModal @success="handleRefresh" />
    <div class="model-page">
      <section class="model-binding surface-panel">
        <header class="model-binding__header">
          <div class="min-w-0">
            <h2 class="section-title">业务模型绑定</h2>
            <p class="model-binding__hint">
              为每项业务选择模型，点「保存全部」后一起生效。
            </p>
          </div>
          <div class="model-binding__actions">
            <StatusTag
              v-if="hasChanges"
              label="有未保存的修改"
              tone="waiting"
            />
            <Button
              v-access:code="['ai:model:update']"
              type="primary"
              :loading="bindingSaving"
              :disabled="bindingLoading || !hasChanges"
              @click="handleSaveBindings"
            >
              <IconifyIcon icon="lucide:save" />保存全部
            </Button>
          </div>
        </header>
        <div v-access:code="['ai:model:update']" class="model-binding__unified">
          <Select
            v-model:value="unifiedModelId"
            class="w-64"
            :options="modelOptions"
            :disabled="bindingLoading || bindingSaving"
            placeholder="统一选择模型"
          />
          <Button
            :disabled="!unifiedModelId || bindingLoading || bindingSaving"
            @click="applyToAll"
          >
            应用到全部业务
          </Button>
        </div>
        <Spin :spinning="bindingLoading" tip="正在读取业务模型绑定…">
          <div class="model-binding__list">
            <div
              v-for="binding in bindings"
              :key="binding.businessCode"
              class="model-binding__row"
            >
              <span class="model-binding__name">
                {{ binding.businessName }}
              </span>
              <Select
                v-model:value="binding.modelId"
                :options="bindingOptions(binding)"
                :disabled="bindingLoading || bindingSaving"
                placeholder="请选择模型"
              />
              <StatusTag v-bind="getModelBindingMeta(isAvailable(binding))" />
            </div>
          </div>
        </Spin>
      </section>

      <div class="model-page__grid">
        <Grid table-title="模型配置列表">
          <template #toolbar-tools>
            <TableAction
              :actions="[
                {
                  label: $t('ui.actionTitle.create', ['模型配置']),
                  type: 'primary',
                  icon: ACTION_ICON.ADD,
                  auth: ['ai:model:create'],
                  onClick: handleCreate,
                },
              ]"
            />
          </template>
          <template #actions="{ row }">
            <TableAction
              :actions="[
                {
                  label: testingIds.has(row.id) ? '测试中' : '测试',
                  type: 'link',
                  icon: 'lucide:plug-zap',
                  loading: testingIds.has(row.id),
                  disabled:
                    row.status !== 0 || row.type !== AiModelTypeEnum.CHAT,
                  tooltip: '发送一次测试请求',
                  auth: ['ai:model:update'],
                  onClick: handleTest.bind(null, row),
                },
                {
                  label: $t('common.edit'),
                  type: 'link',
                  icon: ACTION_ICON.EDIT,
                  auth: ['ai:model:update'],
                  onClick: handleEdit.bind(null, row),
                },
                {
                  label: $t('common.delete'),
                  type: 'link',
                  danger: true,
                  icon: ACTION_ICON.DELETE,
                  auth: ['ai:model:delete'],
                  popConfirm: {
                    ...deleteRowConfirm(
                      '模型',
                      row.name,
                      '绑定它的业务会变为未就绪，删除后不能恢复。',
                    ),
                    confirm: handleDelete.bind(null, row),
                  },
                },
              ]"
            />
          </template>
          <template #loading>
            <VbenLoading spinning text="正在读取模型配置…" />
          </template>
          <template #empty>
            <EmptyState
              v-if="isFiltered"
              compact
              description="调整或清空筛选条件后再试。"
              kind="filtered"
              title="没有符合条件的模型"
            />
            <EmptyState
              v-else
              compact
              description="先在「API 密钥」页面保存平台的密钥，再新建模型配置。"
              title="还没有模型配置"
            />
          </template>
        </Grid>
      </div>
    </div>
  </Page>
</template>

<style scoped>
.model-page {
  display: flex;
  flex-direction: column;
  gap: var(--nus-section-gap);
  height: 100%;
}

.model-binding {
  flex: none;
}

.model-page__grid {
  flex: 1 1 auto;
  min-height: 520px;
}

.model-binding.surface-panel {
  padding: var(--nus-card-padding);
}

.model-binding__header {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-3);
  align-items: flex-start;
  justify-content: space-between;
}

.model-binding__hint {
  margin: var(--nus-space-1) 0 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.model-binding__actions {
  display: flex;
  gap: var(--nus-space-3);
  align-items: center;
}

.model-binding__unified {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2);
  align-items: center;
  padding: var(--nus-space-3);
  margin: var(--nus-space-4) 0;
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius);
}

.model-binding__list {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--nus-space-3) var(--nus-space-8);

  /* 媒体查询嵌在规则里（stylelint 的 order 规则会把顶层媒体查询挪到前面，失去覆盖效果） */
  @media (width >= 1024px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.model-binding__row {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) minmax(180px, 2fr) 72px;
  gap: var(--nus-space-3);
  align-items: center;
  min-height: 40px;
}

.model-binding__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 绑定区按内容高度，表格占满剩下的高度（至少 520，放不下时整页滚动） */
</style>
