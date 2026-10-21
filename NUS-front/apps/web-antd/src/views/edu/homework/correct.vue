<script lang="ts" setup>
import type { EduHomeworkRecordApi } from '#/api/edu/homework-record';

import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useAccess } from '@vben/access';
import { confirm, Page } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Alert, Button, message, Spin } from 'ant-design-vue';

import { aiCorrectAllAsync } from '#/api/edu/ai-review';
import { getHomework } from '#/api/edu/homework';
import { exportHomeworkGradebook } from '#/api/edu/homework-record';
import ImportWizard from '#/components/data-exchange/import-wizard.vue';
import { EmptyState } from '#/components/empty-state';
import { AI_REVIEW_TEXT } from '#/utils/ai-calibration';
import { HOMEWORK_RECORD_STATUS } from '#/utils/status';
import { isCorrectableStatus, isRedoStatus } from '#/utils/status-grading';
import {
  getCorrectBlockedReason,
  getGradingSummary,
} from '#/views/edu/homework/components/grading-display';

import {
  HOMEWORK_VIEW_ONLY_NOTICE,
  HOMEWORK_VIEW_ONLY_REASON,
  isHomeworkViewOnly,
} from './grade-access';
import GradingAiPanel from './grading-ai-panel.vue';
import GradingAiTaskModal from './grading-ai-task-modal.vue';
import GradingDetailFooter from './grading-detail-footer.vue';
import GradingDetailHeader from './grading-detail-header.vue';
import GradingRecordList from './grading-record-list.vue';
import GradingReviewForm from './grading-review-form.vue';
import GradingSubmission from './grading-submission.vue';
import { useAiBatchTask } from './use-ai-batch-task';
import { useGradingActions } from './use-grading-actions';
import { useGradingDraft } from './use-grading-draft';
import { useGradingKeys } from './use-grading-keys';
import { getAdjacentRecordId, useGradingQueue } from './use-grading-queue';
import { useGradingSelection } from './use-grading-selection';
import { useGradingUndo } from './use-grading-undo';
import { useLeaveGuard } from './use-leave-guard';

import './grading-shared.css';

/**
 * 批改工作台（V1.0.1.6 界面规范）：左栏批改队列，右栏当前提交。保存后只更新这一行，撤销期内可以撤销，
 * 学生通知在撤销期过后才发出；切换学生时已填的评分与评语留作草稿。逻辑拆在同目录的 use-* 与 grading-* 文件里。
 * 不是布置者（任务详情 canGrade 为 false）时只读：只能查看提交、成绩与 AI 初评，批改、打回、AI 初评与导入都不显示。
 */
const route = useRoute();
const { hasAccessByCodes } = useAccess();
const homeworkId = computed(() => Number(route.params.homeworkId));
const canImportGradebook = computed(() =>
  hasAccessByCodes(['edu:data-exchange:import']),
);
const canAiReview = computed(() => hasAccessByCodes(['edu:ai-review:correct']));
const canViewHistory = computed(() =>
  hasAccessByCodes(['edu:homework-record:correct']),
);

const homeworkName = ref('');
/** 只读：当前用户不是布置者，只能查看；任务信息读不到或旧版后端时按可批改处理 */
const viewOnly = ref(false);
const homeworkRequirement = ref('');
/** 任务截止时间，用于计算打回重做的默认期限 */
const homeworkDeadline = ref<number | string>();
const checkedRecordIds = ref<number[]>([]);
const historyOpen = ref(false);
const gradebookImportOpen = ref(false);
const exporting = ref(false);
const aiApplying = ref(false);
const aiPanel = ref<InstanceType<typeof GradingAiPanel>>();

const queue = useGradingQueue(homeworkId);
const drafts = useGradingDraft(homeworkId);
const selection = useGradingSelection(queue.findRecord, drafts);
const { detail, detailError, errors, form, formReady, selectedId } = selection;
const undo = useGradingUndo();
const batch = useAiBatchTask((succeeded) => {
  if (succeeded) message.success('批量 AI 初评已完成');
  else message.warning(AI_REVIEW_TEXT.taskFailure);
  void reloadQueue();
});

/** 右栏显示用：详情读到前先用列表行 */
const current = computed<EduHomeworkRecordApi.HomeworkRecord | undefined>(
  () =>
    detail.value ??
    (selection.selectedRow.value as EduHomeworkRecordApi.HomeworkRecord),
);
const canCorrect = computed(
  () => !viewOnly.value && isCorrectableStatus(current.value?.status),
);
const blockedReason = computed(() => {
  if (!current.value) return '';
  if (viewOnly.value) return HOMEWORK_VIEW_ONLY_REASON;
  return canCorrect.value ? '' : getCorrectBlockedReason(current.value.status);
});

const actions = useGradingActions({
  canCorrect,
  checkedIds: checkedRecordIds,
  current,
  drafts,
  homeworkDeadline,
  queue,
  reloadQueue,
  select: handleSelect,
  selection,
  undo,
});
const { conflictText, refreshing, reloadToken, submitting } = actions;

const pageDescription = computed(() =>
  queue.loading.value && queue.records.value.length === 0
    ? '正在读取提交记录…'
    : getGradingSummary({
        ...queue.counts.value,
        deadline: homeworkDeadline.value,
        total: queue.total.value,
      }),
);
const emptyDescription = computed(() =>
  viewOnly.value
    ? '学生提交后会出现在这里。'
    : '学生提交后会出现在这里，可以逐份批改，也可以打回重做。',
);
const showWorkspaceEmpty = computed(
  () =>
    !queue.loading.value &&
    !queue.loadError.value &&
    queue.records.value.length === 0,
);
/** 只算还能批改的记录上的草稿：已打回记录上的草稿保留，但不拦离开 */
const unsavedCount = computed(
  () =>
    [...drafts.draftIds.value].filter((id) =>
      isCorrectableStatus(queue.findRecord(id)?.status),
    ).length,
);

function handleSelect(id: number | undefined) {
  if (id === selectedId.value) return;
  conflictText.value = '';
  selection.select(id);
}

async function reloadQueue() {
  if (!(await queue.load())) return;
  const ids = new Set(queue.records.value.map((r) => r.id));
  drafts.refresh(ids);
  checkedRecordIds.value = checkedRecordIds.value.filter((id) => ids.has(id));
  if (selectedId.value !== undefined && ids.has(selectedId.value)) {
    await selection.refreshDetail();
    reloadToken.value++;
  } else {
    selection.select(queue.filteredRecords.value[0]?.id);
  }
}

async function loadPage() {
  const id = homeworkId.value;
  if (!id) return;
  try {
    const hw = await getHomework(id);
    if (id !== homeworkId.value) return;
    homeworkName.value = hw.homeworkName;
    homeworkRequirement.value = hw.requirement || '';
    homeworkDeadline.value = hw.deadline;
    viewOnly.value = isHomeworkViewOnly(hw);
  } catch {
    // 任务信息读不到时只影响页头，队列照常读取
  }
  await reloadQueue();
}

async function handleAiApply() {
  if (viewOnly.value) return;
  aiApplying.value = true;
  try {
    await aiPanel.value?.applyOrTrigger();
  } finally {
    aiApplying.value = false;
  }
}

function handleAiFill(draft: { comment?: string; score?: number }) {
  if (draft.score !== undefined) form.score = draft.score;
  if (draft.comment) form.comment = draft.comment;
}

function handleCheck(id: number, checked: boolean) {
  const rest = checkedRecordIds.value.filter((value) => value !== id);
  checkedRecordIds.value = checked ? [...rest, id] : rest;
}

async function handleAiCorrectAll() {
  if (viewOnly.value) return;
  const currentHomeworkId = homeworkId.value;
  try {
    await confirm({
      cancelText: '返回',
      confirmText: '批量生成',
      content:
        '只为还没有 AI 初评、初评失败或提交已更新的记录生成，已有的 AI 初评不会被覆盖。AI 初评只供参考，不会直接成为成绩。',
      title: '为还没有 AI 初评或初评失败的提交生成 AI 初评？',
    });
  } catch {
    return;
  }
  if (homeworkId.value !== currentHomeworkId) return;
  try {
    const result = await aiCorrectAllAsync({ homeworkId: currentHomeworkId });
    if (homeworkId.value === currentHomeworkId) batch.start(result.taskId);
  } catch {
    // 全局已提示失败原因
  }
}

function downloadGradebookTemplate() {
  return exportHomeworkGradebook(homeworkId.value);
}

async function handleExportGradebook() {
  exporting.value = true;
  try {
    downloadFileFromBlobPart({
      fileName: `${homeworkName.value || '任务'}成绩册.xlsx`,
      source: await downloadGradebookTemplate(),
    });
  } finally {
    exporting.value = false;
  }
}

watch([queue.statusFilter, queue.keyword], () => {
  const list = queue.filteredRecords.value;
  if (!list.some((item) => item.id === selectedId.value)) {
    handleSelect(list[0]?.id);
  }
});

watch(
  homeworkId,
  () => {
    batch.close();
    undo.clear();
    queue.reset();
    queue.resetFilters();
    selection.select(undefined);
    drafts.refresh();
    checkedRecordIds.value = [];
    historyOpen.value = false;
    conflictText.value = '';
    viewOnly.value = false;
    void loadPage();
  },
  { immediate: true },
);

useGradingKeys((action) => {
  if (action === 'save-next') {
    if (!viewOnly.value) void actions.save(true);
    return;
  }
  const step = action === 'next' ? 1 : -1;
  const id = getAdjacentRecordId(
    queue.filteredRecords.value,
    selectedId.value,
    step,
  );
  if (id !== undefined) handleSelect(id);
});

useLeaveGuard(
  () => !viewOnly.value && unsavedCount.value > 0,
  async () => {
    try {
      await confirm({
        cancelText: '返回',
        confirmText: '离开',
        content: `还有 ${unsavedCount.value} 份提交的评分或评语没有保存，学生看不到。草稿留在这个浏览器标签页里，回来可以接着改。`,
        title: '离开批改页？',
      });
      return true;
    } catch {
      return false;
    }
  },
);
</script>

<template>
  <Page
    :title="homeworkName || '实训任务批改'"
    class="grading-page"
    content-class="grading-page__content"
  >
    <!-- 只读说明放在页头里，不占批改工作台的高度 -->
    <template #description>
      <p class="grading-page__summary">{{ pageDescription }}</p>
      <Alert
        v-if="viewOnly"
        :message="HOMEWORK_VIEW_ONLY_NOTICE"
        class="grading-page__notice"
        show-icon
        type="info"
      />
    </template>
    <template #extra>
      <Button
        v-if="!viewOnly"
        :disabled="queue.records.value.length === 0"
        @click="handleAiCorrectAll"
      >
        批量生成 AI 初评
      </Button>
      <Button :loading="exporting" @click="handleExportGradebook">
        导出成绩册
      </Button>
      <Button
        v-if="canImportGradebook && !viewOnly"
        @click="gradebookImportOpen = true"
      >
        导入成绩册
      </Button>
    </template>

    <ImportWizard
      v-if="canImportGradebook && !viewOnly"
      v-model:open="gradebookImportOpen"
      :options="{ homeworkId }"
      :template-downloader="downloadGradebookTemplate"
      :title="`导入成绩册：${homeworkName}`"
      biz-type="HOMEWORK_GRADEBOOK"
      template-file-name="任务成绩册导入模板.xlsx"
      @success="reloadQueue"
    />

    <Spin
      :spinning="queue.loading.value"
      tip="正在读取提交记录…"
      wrapper-class-name="grading-spin"
    >
      <div v-if="queue.loadError.value" class="surface-panel grading-empty">
        <EmptyState
          description="网络不稳定或服务暂时不可用，稍后重试。"
          kind="blocked"
          title="提交记录没有读取成功"
        >
          <template #action>
            <Button size="small" @click="reloadQueue">重试</Button>
          </template>
        </EmptyState>
      </div>
      <div v-else-if="showWorkspaceEmpty" class="surface-panel grading-empty">
        <EmptyState :description="emptyDescription" title="还没有学生提交" />
      </div>

      <div v-else class="grading-workspace">
        <GradingRecordList
          v-model:keyword="queue.keyword.value"
          v-model:status-filter="queue.statusFilter.value"
          :checked-ids="checkedRecordIds"
          :draft-ids="drafts.draftIds.value"
          :loading="queue.loading.value"
          :read-only="viewOnly"
          :records="queue.filteredRecords.value"
          :selected-id="selectedId"
          :submitting="submitting"
          @check="handleCheck"
          @redo-batch="actions.redoBatch"
          @reset-filters="queue.resetFilters"
          @select="handleSelect"
        />

        <section aria-label="当前提交" class="surface-panel grading-detail">
          <template v-if="current">
            <GradingDetailHeader
              v-model:history-open="historyOpen"
              :can-view-history="canViewHistory"
              :record="current"
            />

            <div class="grading-detail__body">
              <GradingSubmission
                v-if="detail"
                :history-open="historyOpen && canViewHistory"
                :record="detail"
                :reload-token="reloadToken"
                :requirement="homeworkRequirement"
              />
              <div v-else class="grading-detail__submission">
                <Alert
                  v-if="detailError"
                  message="这份提交没有读取成功：网络不稳定或记录已被删除。"
                  show-icon
                  type="warning"
                >
                  <template #action>
                    <Button size="small" @click="selection.refreshDetail">
                      重试
                    </Button>
                  </template>
                </Alert>
                <p v-else class="grading-note">正在读取这份提交…</p>
              </div>

              <div class="grading-detail__review">
                <GradingReviewForm
                  v-model:comment="form.comment"
                  v-model:score="form.score"
                  :blocked-reason="blockedReason"
                  :comment-error="errors.comment"
                  :conflict-text="conflictText"
                  :disabled="!canCorrect || !formReady"
                  :refreshing="refreshing"
                  :score-error="errors.score"
                  @refresh="actions.refreshConflict"
                />
                <GradingAiPanel
                  ref="aiPanel"
                  :can-ai-review="canAiReview && !viewOnly"
                  :read-only="viewOnly"
                  :homework-id="homeworkId"
                  :record-id="selectedId"
                  :record-status="current.status"
                  :reload-token="reloadToken"
                  @fill="handleAiFill"
                />
              </div>
            </div>

            <GradingDetailFooter
              :ai-applying="aiApplying"
              :can-correct="canCorrect"
              :form-ready="formReady"
              :read-only="viewOnly"
              :redo-disabled="
                current.status === HOMEWORK_RECORD_STATUS.NOT_SUBMITTED
              "
              :redo-label="
                isRedoStatus(current.status) ? '调整重做期限' : '打回重做'
              "
              :submitting="submitting"
              :undo-error="undo.errorText.value"
              :undo-offer="undo.offer.value"
              :undoing="undo.undoing.value"
              @ai-apply="handleAiApply"
              @redo="actions.redo"
              @save="actions.save"
              @undo="actions.undoLast"
            />
          </template>
          <EmptyState
            v-else-if="!queue.loading.value"
            class="grading-pane-empty"
            description="左侧没有符合条件的记录，调整筛选后再选择。"
            kind="filtered"
            title="没有选中的记录"
          />
        </section>
      </div>
    </Spin>

    <GradingAiTaskModal
      v-model:open="batch.open.value"
      :completed="batch.completed.value"
      :error-text="batch.errorText.value"
      :failed="batch.failed.value"
      :polling="Boolean(batch.polling.value)"
      :progress="batch.progress.value"
      :progress-text="batch.progressText.value"
      :timed-out="batch.timedOut.value"
      :total="batch.total.value"
      @close="batch.close"
      @refresh="batch.refresh"
    />
  </Page>
</template>

<!--
  批改工作台（V1.0.1.6 界面规范）：左栏学生记录，右栏当前提交；桌面宽度下页面占满内容区，两栏各自滚动。
  右栏够宽时再分成“提交内容 | 批改”两列，评分与评语始终在眼前；底部操作栏常驻。
-->
<style scoped>
@container (width >= 760px) {
  .grading-detail .grading-detail__body {
    display: grid;
    grid-template-rows: minmax(0, 1fr);
    grid-template-columns: minmax(0, 1fr) minmax(300px, 380px);
    overflow: hidden;
  }

  .grading-detail .grading-detail__submission,
  .grading-detail .grading-detail__review {
    min-height: 0;
    overflow-y: auto;
  }

  .grading-detail .grading-detail__review {
    border-top: 0;
    border-left: 1px solid var(--nus-border-subtle);
  }
}

@media (width >= 768px) {
  .grading-page {
    height: var(--vben-content-height, auto);
  }

  .grading-page :deep(.grading-page__content) {
    flex: 1 1 0;
    min-height: 0;
  }

  .grading-page .grading-spin,
  .grading-page .grading-spin :deep(.ant-spin-container),
  .grading-page .grading-empty {
    height: 100%;
  }

  .grading-page .grading-workspace {
    grid-template-rows: minmax(0, 1fr);
    grid-template-columns: 312px minmax(0, 1fr);
    height: 100%;
  }
}

@media (width >= 1440px) {
  .grading-page .grading-spin .grading-workspace {
    grid-template-columns: 320px minmax(0, 1fr);
  }
}

/* 页头说明用了 description 插槽，样式与 Page 自带的说明一致 */
.grading-page__summary {
  margin: 2px 0 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.grading-page__notice {
  margin-top: var(--nus-space-3);
}

.grading-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--nus-space-4);
}

.grading-empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

.grading-detail {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  container-type: inline-size;
  overflow: hidden;
}

.grading-pane-empty {
  flex: 1;
}

.grading-detail__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.grading-detail__submission,
.grading-detail__review {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-6);
  min-width: 0;
  padding: var(--nus-space-5);
}

.grading-detail__review {
  border-top: 1px solid var(--nus-border-subtle);
}
</style>
