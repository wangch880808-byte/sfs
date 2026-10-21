<script lang="ts" setup>
import type { PromptDiffData } from '../data';

import type { AiPromptTemplateApi } from '#/api/ai/prompt';

import { computed, ref } from 'vue';

import { useVbenDrawer, useVbenModal, VbenLoading } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { Alert, Button, message } from 'ant-design-vue';

import { TableAction } from '#/adapter/vxe-table';
import {
  activatePromptTemplateVersion,
  getPromptTemplateHistory,
} from '#/api/ai/prompt';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import {
  getPromptActiveVersionLabel,
  getPromptVersionMeta,
} from '#/utils/status-ai-governance';

import { activateVersionConfirm, PROMPT_EFFECT_HINT } from '../data';
import DiffModal from './diff-modal.vue';

const emit = defineEmits(['success']);

const summary = ref<AiPromptTemplateApi.Summary>();
const versions = ref<AiPromptTemplateApi.Version[]>([]);
const loading = ref(false);
const loadError = ref('');
const expanded = ref(new Set<number>());
const activatingId = ref<number>();
let requestId = 0;

const title = computed(() =>
  summary.value ? `「${summary.value.name}」的版本` : '版本',
);
const description = computed(() => {
  const current = summary.value;
  if (!current) return '';
  const active = versions.value.find((item) => item.active);
  const label = active
    ? `第 ${active.version} 版`
    : getPromptActiveVersionLabel(current.usingDefault, current.activeVersion);
  return `${current.businessName} · 现在生效：${label}`;
});

const [DiffDialog, diffModalApi] = useVbenModal({
  connectedComponent: DiffModal,
  destroyOnClose: true,
});

/** 读不到时写明原因，原因来自后端提示，没有就写未知 */
function reasonOf(error: unknown) {
  const data = (error as { response?: { data?: { msg?: string } } })?.response
    ?.data;
  const reason = data?.msg || (error as Error)?.message;
  return reason ? reason.replace(/[。.]$/, '') : '原因未知';
}

async function loadHistory() {
  const current = summary.value;
  if (!current) return;
  const id = ++requestId;
  loading.value = true;
  loadError.value = '';
  try {
    const list = await getPromptTemplateHistory(current.businessCode, {
      silentError: true,
    });
    if (id !== requestId) return;
    versions.value = list;
  } catch (error) {
    if (id !== requestId) return;
    loadError.value = `读取版本没有成功：${reasonOf(error)}。点「重试」再读一次。`;
  } finally {
    if (id === requestId) loading.value = false;
  }
}

function toggleContent(version: AiPromptTemplateApi.Version) {
  const next = new Set(expanded.value);
  if (next.has(version.id)) {
    next.delete(version.id);
  } else {
    next.add(version.id);
  }
  expanded.value = next;
}

function handleCompare(version: AiPromptTemplateApi.Version) {
  const current = summary.value;
  if (!current) return;
  diffModalApi
    .setData({
      name: current.name,
      defaultContent: current.defaultContent,
      content: version.content,
      label: `第 ${version.version} 版`,
    } satisfies PromptDiffData)
    .open();
}

async function handleActivate(version: AiPromptTemplateApi.Version) {
  const current = summary.value;
  if (!current || activatingId.value !== undefined) return;
  activatingId.value = version.id;
  try {
    await activatePromptTemplateVersion(version.id);
    message.success(
      `已启用「${current.name}」第 ${version.version} 版，${PROMPT_EFFECT_HINT}。`,
    );
    emit('success');
    await loadHistory();
  } finally {
    activatingId.value = undefined;
  }
}

const [Drawer, drawerApi] = useVbenDrawer({
  footer: false,
  class: 'w-full md:w-3/5 xl:w-2/5',
  onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      requestId++;
      summary.value = undefined;
      versions.value = [];
      expanded.value = new Set();
      loadError.value = '';
      return;
    }
    summary.value = drawerApi.getData<AiPromptTemplateApi.Summary>();
    void loadHistory();
  },
});
</script>

<template>
  <Drawer :description="description" :title="title">
    <DiffDialog />
    <div class="prompt-history">
      <Alert v-if="loadError" :message="loadError" show-icon type="error">
        <template #action>
          <Button size="small" @click="loadHistory">重试</Button>
        </template>
      </Alert>
      <VbenLoading
        v-else-if="loading && versions.length === 0"
        spinning
        text="正在读取版本…"
      />
      <EmptyState
        v-else-if="versions.length === 0"
        compact
        description="现在用的是内置默认内容。在列表里点「新建版本」保存你修改后的提示词。"
        title="还没有保存过版本"
      />
      <ol v-else class="prompt-history__list">
        <li
          v-for="version in versions"
          :key="version.id"
          :class="{ 'is-active': version.active }"
          class="prompt-history__item"
        >
          <div class="prompt-history__head">
            <span class="prompt-history__version">
              第 {{ version.version }} 版
            </span>
            <StatusTag v-bind="getPromptVersionMeta(version.active)" />
          </div>
          <p class="prompt-history__meta">
            {{ version.creatorName || '保存人未知' }} ·
            {{ formatDateTime(version.createTime) }}
          </p>
          <p class="prompt-history__remark">
            {{ version.remark || '没有备注' }}
          </p>
          <TableAction
            :actions="[
              {
                label: expanded.has(version.id) ? '收起内容' : '查看内容',
                type: 'link',
                icon: expanded.has(version.id)
                  ? 'lucide:chevron-up'
                  : 'lucide:chevron-down',
                onClick: toggleContent.bind(null, version),
              },
              {
                label: '对比默认',
                type: 'link',
                icon: 'lucide:git-compare',
                onClick: handleCompare.bind(null, version),
              },
              {
                label: '启用',
                type: 'link',
                icon: 'lucide:circle-check',
                auth: ['ai:prompt-template:activate'],
                ifShow: !version.active,
                loading: activatingId === version.id,
                disabled:
                  activatingId !== undefined && activatingId !== version.id,
                popConfirm: summary
                  ? {
                      ...activateVersionConfirm(summary, version),
                      confirm: handleActivate.bind(null, version),
                    }
                  : undefined,
              },
            ]"
          />
          <pre
            v-if="expanded.has(version.id)"
            class="prompt-history__content"
            v-text="version.content"
          ></pre>
        </li>
      </ol>
    </div>
  </Drawer>
</template>

<style scoped>
.prompt-history__list {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-3);
  padding: 0;
  margin: 0;
  list-style: none;
}

.prompt-history__item {
  padding: var(--nus-space-3) var(--nus-space-4);
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius-lg);
}

.prompt-history__item.is-active {
  background: var(--nus-bg-sunken);
}

.prompt-history__head {
  display: flex;
  gap: var(--nus-space-3);
  align-items: center;
}

.prompt-history__version {
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.prompt-history__meta {
  margin: var(--nus-space-1) 0 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-secondary);
}

.prompt-history__remark {
  margin: var(--nus-space-2) 0 var(--nus-space-1);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text);
  overflow-wrap: anywhere;
}

.prompt-history__content {
  max-height: 360px;
  padding: var(--nus-space-3);
  margin: var(--nus-space-2) 0 0;
  overflow: auto;
  font-family: var(--nus-font-mono);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  background: var(--nus-bg-surface);
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius);
}
</style>
