<script lang="ts" setup>
import type { HomeworkRow } from './data';
import type { RowConfirmCopy } from './display';

import type { PopConfirm, VxeTableGridOptions } from '#/adapter/vxe-table';
import type { EduHomeworkApi } from '#/api/edu/homework';

import { computed, defineAsyncComponent, ref } from 'vue';
import { useRouter } from 'vue-router';

import { confirm, Page, useVbenModal, VbenLoading } from '@vben/common-ui';
import { isEmpty } from '@vben/utils';

import { Button, message, Modal, Switch, Tooltip } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteHomework,
  getHomeworkPage,
  getHomeworkRemoveImpact,
  removeHomework,
  removeHomeworkList,
  restoreHomework,
} from '#/api/edu/homework';
import { DictTag } from '#/components/dict-tag';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import { $t } from '#/locales';
import {
  ARCHIVE_COPY,
  archivedMessage,
  archivingMessage,
  restoredMessage,
} from '#/utils/archive';
import { HOMEWORK_STATUS, isHomeworkArchived } from '#/utils/status';

import {
  EDU_HOMEWORK_STATUS_DICT,
  getClassNameMap,
  getCourseNameMap,
  getIndicatorNameMap,
  useGridColumns,
  useGridFormSchema,
} from './data';
import {
  hasActiveFilters,
  HOMEWORK_ARCHIVE_HINT,
  HOMEWORK_PENDING_HINT,
  HOMEWORK_PENDING_LABEL,
  HOMEWORK_RESTORE_HINT,
  homeworkBatchConfirm,
  homeworkRowConfirm,
  isHomeworkPending,
  resolveIncludeArchived,
} from './display';
import {
  buildRemoveConfirm,
  REMOVE_CANCEL_TEXT,
  REMOVE_OK_TEXT,
} from './remove-confirm';

const HomeworkForm = defineAsyncComponent(() => import('./HomeworkForm.vue'));

/** 批量删除一次最多的任务数（与后端一致） */
const REMOVE_BATCH_LIMIT = 100;

const router = useRouter();
const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: HomeworkForm,
  destroyOnClose: true,
});

const createLabel = '新建实训任务';
const checkedRows = ref<EduHomeworkApi.Homework[]>([]);
const includeArchived = ref(false);
/** 最近一次查询是否带了筛选条件：决定空状态是“还没有”还是“没有符合条件的” */
const filtered = ref(false);
const archiveIds = computed(() =>
  checkedRows.value
    .filter((row) => !isHomeworkArchived(row.status))
    .map((row) => row.id!),
);
const restoreIds = computed(() =>
  checkedRows.value
    .filter((row) => isHomeworkArchived(row.status))
    .map((row) => row.id!),
);
/** 可以删除的任务：任何状态都能删（含已封存与待发布） */
const removeIds = computed(() =>
  checkedRows.value.map((row) => row.id!).filter(Boolean),
);
const emptyHint = computed(() =>
  includeArchived.value
    ? '新建的实训任务会显示在这里。'
    : `新建的实训任务会显示在这里；${ARCHIVE_COPY.archived}的任务打开「${ARCHIVE_COPY.showArchived}」查看。`,
);

/** 气泡确认：标题与正文分两行（TableAction 会把 description 原样交给 Popconfirm） */
type RowPopConfirm = Pick<RowConfirmCopy, 'description'> & PopConfirm;

function archivePopConfirm(row: HomeworkRow): RowPopConfirm {
  return {
    ...homeworkRowConfirm(
      ARCHIVE_COPY.archive,
      row.homeworkName,
      HOMEWORK_ARCHIVE_HINT,
    ),
    confirm: () => handleArchive(row),
  };
}

function restorePopConfirm(row: HomeworkRow): RowPopConfirm {
  return {
    ...homeworkRowConfirm(
      ARCHIVE_COPY.restore,
      row.homeworkName,
      HOMEWORK_RESTORE_HINT,
    ),
    confirm: () => handleRestore(row),
  };
}

/** 刷新表格 */
function handleRefresh() {
  checkedRows.value = [];
  gridApi.query();
}

/** 关掉「显示已封存」时，状态筛选若是已封存一并清掉，否则查询又会按已封存带上 */
async function handleArchivedSwitch(checked: boolean | number | string) {
  if (!checked) {
    const values = await gridApi.formApi.getValues();
    if (isHomeworkArchived(values?.status)) {
      await gridApi.formApi.setFieldValue('status', undefined);
    }
  }
  handleRefresh();
}

/** 创建实训任务 */
function handleCreate() {
  formModalApi.setData({}).open();
}

/** 编辑实训任务 */
function handleEdit(row: EduHomeworkApi.Homework) {
  formModalApi.setData(row).open();
}

/** 复制任务：打开预填好原任务内容的表单，选班级和截止时间后新建 */
function handleCopy(row: EduHomeworkApi.Homework) {
  formModalApi.setData({ scene: 'copy', sourceHomeworkId: row.id }).open();
}

/** 查看详情 */
function handleDetail(row: EduHomeworkApi.Homework) {
  router.push({
    path: `/capacity/homework_manager/homework/detail/${row.id}`,
  });
}

/** 进入批改 */
function handleCorrect(row: EduHomeworkApi.Homework) {
  router.push({
    path: `/capacity/homework_manager/homework/correct/${row.id}`,
  });
}

/** 封存实训任务（接口沿用 delete） */
async function handleArchive(row: EduHomeworkApi.Homework) {
  const hideLoading = message.loading({
    content: archivingMessage(row.homeworkName),
    duration: 0,
  });
  try {
    await deleteHomework(row.id!);
    message.success(archivedMessage(row.homeworkName));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 批量封存 */
async function handleArchiveBatch() {
  await confirm(
    homeworkBatchConfirm(
      ARCHIVE_COPY.archive,
      archiveIds.value.length,
      HOMEWORK_ARCHIVE_HINT,
    ),
  );
  const hideLoading = message.loading({
    content: ARCHIVE_COPY.archivingBatch,
    duration: 0,
  });
  try {
    await Promise.all(archiveIds.value.map((id) => deleteHomework(id)));
    checkedRows.value = [];
    message.success(ARCHIVE_COPY.archivedBatch);
    handleRefresh();
  } finally {
    hideLoading();
  }
}

async function handleRestore(row: EduHomeworkApi.Homework) {
  await restoreHomework(row.id!);
  message.success(restoredMessage(row.homeworkName));
  handleRefresh();
}

async function handleRestoreBatch() {
  await confirm(
    homeworkBatchConfirm(
      ARCHIVE_COPY.restore,
      restoreIds.value.length,
      HOMEWORK_RESTORE_HINT,
    ),
  );
  await Promise.all(restoreIds.value.map((id) => restoreHomework(id)));
  checkedRows.value = [];
  message.success(ARCHIVE_COPY.restoredBatch);
  handleRefresh();
}

/**
 * 删除任务（连同提交与成绩，不能恢复）：先查影响范围，再弹危险确认框，确认后删除并刷新。
 * 只删一个时标题写任务名称。
 */
async function openRemoveConfirm(ids: number[], name?: string) {
  if (ids.length === 0) {
    return;
  }
  if (ids.length > REMOVE_BATCH_LIMIT) {
    message.warning(
      `一次最多删除 ${REMOVE_BATCH_LIMIT} 个任务，减少勾选后再删除。`,
    );
    return;
  }
  const impact = await getHomeworkRemoveImpact(ids);
  const copy = buildRemoveConfirm(impact, ids.length === 1 ? name : undefined);
  Modal.confirm({
    autoFocusButton: 'cancel',
    cancelText: REMOVE_CANCEL_TEXT,
    content: copy.content,
    okButtonProps: { danger: copy.danger },
    okText: REMOVE_OK_TEXT,
    title: copy.title,
    async onOk() {
      await (ids.length === 1
        ? removeHomework(ids[0]!)
        : removeHomeworkList(ids));
      message.success(
        ids.length === 1 && name
          ? `已删除「${name}」`
          : `已删除 ${ids.length} 个实训任务`,
      );
      handleRefresh();
    },
  });
}

function handleRemove(row: EduHomeworkApi.Homework) {
  return openRemoveConfirm([row.id!], row.homeworkName);
}

function handleRemoveBatch() {
  const names = checkedRows.value.map((row) => row.homeworkName);
  return openRemoveConfirm(
    removeIds.value,
    removeIds.value.length === 1 ? names[0] : undefined,
  );
}

function handleRowCheckboxChange({
  records,
}: {
  records: EduHomeworkApi.Homework[];
}) {
  checkedRows.value = records;
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
    showCollapseButton: false,
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          filtered.value = hasActiveFilters(formValues);
          // 只选了「已封存」却没开开关时，结果会永远为空：自动打开开关
          if (
            resolveIncludeArchived(formValues?.status, includeArchived.value)
          ) {
            includeArchived.value = true;
          }
          const [pageResult, courseNameMap, classNameMap, indicatorNameMap] =
            await Promise.all([
              getHomeworkPage({
                pageNo: page.currentPage,
                pageSize: page.pageSize,
                ...formValues,
                includeArchived: includeArchived.value,
              }),
              getCourseNameMap(),
              getClassNameMap(),
              getIndicatorNameMap(),
            ]);
          return {
            ...pageResult,
            list: (pageResult.list ?? []).map((item) => ({
              ...item,
              courseName: courseNameMap.get(Number(item.courseId)) ?? '-',
              className: classNameMap.get(Number(item.classId)) ?? '-',
              indicatorFirstName:
                indicatorNameMap.get(Number(item.indicatorFirstId)) ?? '-',
              indicatorSecondName:
                indicatorNameMap.get(Number(item.indicatorSecondId)) ?? '-',
            })),
          };
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
  } as VxeTableGridOptions<HomeworkRow>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});
</script>

<template>
  <Page
    auto-content-height
    description="为课程班级布置实训任务，跟进提交与批改进度。"
    title="实训任务管理"
  >
    <template #extra>
      <TableAction
        :actions="[
          {
            label: createLabel,
            type: 'primary',
            icon: ACTION_ICON.ADD,
            auth: ['edu:homework:create'],
            onClick: handleCreate,
          },
        ]"
      />
    </template>
    <FormModal @success="handleRefresh" />
    <Grid>
      <template #toolbar-actions>
        <TableAction
          :actions="[
            {
              label: ARCHIVE_COPY.archiveBatch,
              type: 'default',
              auth: ['edu:homework:delete'],
              disabled: isEmpty(archiveIds),
              onClick: handleArchiveBatch,
            },
            {
              label: ARCHIVE_COPY.restoreBatch,
              type: 'default',
              auth: ['edu:homework:delete'],
              disabled: isEmpty(restoreIds),
              onClick: handleRestoreBatch,
            },
            {
              label: '批量删除',
              type: 'default',
              danger: true,
              auth: ['edu:homework:delete'],
              disabled: isEmpty(removeIds),
              onClick: handleRemoveBatch,
            },
          ]"
        />
      </template>
      <template #toolbar-tools>
        <label class="homework-list__archived">
          <span>{{ ARCHIVE_COPY.showArchived }}</span>
          <Switch
            v-model:checked="includeArchived"
            size="small"
            @change="handleArchivedSwitch"
          />
        </label>
      </template>
      <template #status="{ row }">
        <Tooltip
          v-if="isHomeworkPending(row.status)"
          :title="HOMEWORK_PENDING_HINT"
        >
          <StatusTag :label="HOMEWORK_PENDING_LABEL" tone="waiting" />
        </Tooltip>
        <DictTag v-else :type="EDU_HOMEWORK_STATUS_DICT" :value="row.status" />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '详情',
              type: 'link',
              auth: ['edu:homework:query'],
              onClick: handleDetail.bind(null, row),
            },
            {
              label: '批改',
              type: 'link',
              auth: ['edu:homework-record:correct'],
              ifShow:
                !isHomeworkArchived(row.status) &&
                Number(row.status) !== HOMEWORK_STATUS.DRAFT,
              onClick: handleCorrect.bind(null, row),
            },
            {
              label: $t('common.edit'),
              type: 'link',
              auth: ['edu:homework:update'],
              ifShow: !isHomeworkArchived(row.status),
              tooltip: isHomeworkPending(row.status)
                ? HOMEWORK_PENDING_HINT
                : undefined,
              onClick: handleEdit.bind(null, row),
            },
          ]"
          :drop-down-actions="[
            {
              label: '复制',
              type: 'link',
              auth: ['edu:homework:copy'],
              onClick: handleCopy.bind(null, row),
            },
            {
              label: ARCHIVE_COPY.archive,
              type: 'link',
              auth: ['edu:homework:delete'],
              ifShow: !isHomeworkArchived(row.status),
              popConfirm: archivePopConfirm(row),
            },
            {
              label: ARCHIVE_COPY.restore,
              type: 'link',
              auth: ['edu:homework:delete'],
              ifShow: isHomeworkArchived(row.status),
              popConfirm: restorePopConfirm(row),
            },
            {
              label: '删除',
              type: 'link',
              danger: true,
              auth: ['edu:homework:delete'],
              onClick: handleRemove.bind(null, row),
            },
          ]"
        />
      </template>
      <template #loading>
        <VbenLoading spinning text="正在读取实训任务…" />
      </template>
      <template #empty>
        <EmptyState
          v-if="filtered"
          compact
          description="换个筛选条件，或点「重置」查看全部。"
          kind="filtered"
          title="没有符合条件的实训任务"
        />
        <EmptyState
          v-else
          :description="emptyHint"
          compact
          kind="empty"
          title="还没有实训任务"
        >
          <template #action>
            <Button
              v-access:code="['edu:homework:create']"
              @click="handleCreate"
            >
              {{ createLabel }}
            </Button>
          </template>
        </EmptyState>
      </template>
    </Grid>
  </Page>
</template>

<style scoped>
.homework-list__archived {
  display: inline-flex;
  gap: var(--nus-space-2);
  align-items: center;
  margin-right: var(--nus-space-3);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
  cursor: pointer;
}
</style>
