<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { EduClassApi } from '#/api/edu/class';
import type { SystemDeptApi } from '#/api/system/dept';

import { computed, ref } from 'vue';

import { confirm, Page, useVbenModal } from '@vben/common-ui';
import { isEmpty } from '@vben/utils';

import { Card, message, Switch } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteClass,
  deleteClassList,
  getClassPage,
  restoreClass,
  restoreClassList,
} from '#/api/edu/class';
import { StatusTag } from '#/components/status-tag';
import { $t } from '#/locales';
import {
  ARCHIVE_COPY,
  archivedMessage,
  archivingMessage,
  isArchived,
  restoredMessage,
} from '#/utils/archive';
import {
  batchConfirm,
  hasActiveFilters,
  rowConfirm,
} from '#/utils/confirm-copy';
import { getArchiveStatusMeta } from '#/utils/status-admin';
import GridEmpty from '#/views/system/modules/grid-empty.vue';
import DeptTree from '#/views/system/user/modules/dept-tree.vue';

import { getDeptNameMap, useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';
import StudentManageModal from './modules/student-manage-modal.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});
const [StudentManageModalInstance, studentManageModalApi] = useVbenModal({
  connectedComponent: StudentManageModal,
  destroyOnClose: true,
});

interface ClassRow extends EduClassApi.Clazz {
  deptName?: string;
}

const tableTitle = computed(() => '班级列表');
const searchDeptId = ref<number | undefined>(undefined);
/** 筛选条件或组织是否生效：决定空状态的写法 */
const filtered = ref(false);
/** 封存与恢复的后果 */
const ARCHIVE_HINT = `${ARCHIVE_COPY.archiveHint}。`;
const RESTORE_HINT = '恢复后可以继续布置任务，学生的待办照常显示。';
const checkedRows = ref<EduClassApi.Clazz[]>([]);
const includeArchived = ref(false);
const archiveIds = computed(() =>
  checkedRows.value
    .filter((row) => !isArchived(row.status))
    .map((row) => row.id!),
);
const restoreIds = computed(() =>
  checkedRows.value
    .filter((row) => isArchived(row.status))
    .map((row) => row.id!),
);

/** 刷新表格 */
function handleRefresh() {
  checkedRows.value = [];
  gridApi.query();
}

/** 新建班级 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑班级 */
function handleEdit(row: EduClassApi.Clazz) {
  formModalApi.setData(row).open();
}

/** 班级学生管理 */
function handleManageStudents(row: EduClassApi.Clazz) {
  studentManageModalApi.setData(row).open();
}

/** 封存班级 */
async function handleDelete(row: EduClassApi.Clazz) {
  const hideLoading = message.loading({
    content: archivingMessage(row.className),
    duration: 0,
  });
  try {
    await deleteClass(row.id!);
    message.success(archivedMessage(row.className));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 批量封存班级 */
async function handleDeleteBatch() {
  await confirm(
    batchConfirm('封存', '班级', archiveIds.value.length, ARCHIVE_HINT),
  );
  const hideLoading = message.loading({
    content: ARCHIVE_COPY.archivingBatch,
    duration: 0,
  });
  try {
    await deleteClassList(archiveIds.value);
    checkedRows.value = [];
    message.success(ARCHIVE_COPY.archivedBatch);
    handleRefresh();
  } finally {
    hideLoading();
  }
}

async function handleRestore(row: EduClassApi.Clazz) {
  await restoreClass(row.id!);
  message.success(restoredMessage(row.className));
  handleRefresh();
}

async function handleRestoreBatch() {
  await confirm(
    batchConfirm('恢复', '班级', restoreIds.value.length, RESTORE_HINT),
  );
  await restoreClassList(restoreIds.value);
  checkedRows.value = [];
  message.success(ARCHIVE_COPY.restoredBatch);
  handleRefresh();
}

function handleRowCheckboxChange({
  records,
}: {
  records: EduClassApi.Clazz[];
}) {
  checkedRows.value = records;
}

/** 选择组织 */
function handleDeptSelect(dept: SystemDeptApi.Dept) {
  searchDeptId.value = dept.id;
  handleRefresh();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    // 左侧有组织树，1280 以下筛选项每行两个
    wrapperClass: 'grid-cols-1 md:grid-cols-2 xl:grid-cols-3',
    schema: useGridFormSchema().filter((item) => item.fieldName !== 'deptId'),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          filtered.value =
            hasActiveFilters(formValues) || searchDeptId.value !== undefined;
          const deptNameMap = await getDeptNameMap();
          const pageResult = await getClassPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
            deptId: searchDeptId.value,
            includeArchived: includeArchived.value,
          });
          return {
            ...pageResult,
            list: (pageResult.list ?? []).map((item) => ({
              ...item,
              deptName: deptNameMap.get(Number(item.deptId)) ?? '-',
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
  } as VxeTableGridOptions<ClassRow>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <StudentManageModalInstance @success="handleRefresh" />

    <div class="flex h-full w-full">
      <Card
        class="mr-4 h-full w-[200px] shrink-0 overflow-hidden 2xl:w-[240px]"
        :body-style="{
          height: '100%',
          minHeight: 0,
          overflow: 'hidden',
          padding: '16px',
        }"
      >
        <DeptTree @select="handleDeptSelect" />
      </Card>
      <div class="tree-grid min-w-0 flex-1">
        <Grid :table-title="tableTitle">
          <template #toolbar-tools>
            <span class="toolbar-switch">
              <span class="toolbar-switch-label">
                {{ ARCHIVE_COPY.showArchived }}
              </span>
              <Switch
                v-model:checked="includeArchived"
                :aria-label="ARCHIVE_COPY.showArchived"
                @change="handleRefresh"
              />
            </span>
            <TableAction
              :actions="[
                {
                  label: '新建班级',
                  type: 'primary',
                  icon: ACTION_ICON.ADD,
                  auth: ['edu:class:create'],
                  onClick: handleCreate,
                },
                {
                  label: ARCHIVE_COPY.archiveBatch,
                  type: 'default',
                  icon: 'lucide:archive',
                  auth: ['edu:class:delete'],
                  disabled: isEmpty(archiveIds),
                  onClick: handleDeleteBatch,
                },
                {
                  label: ARCHIVE_COPY.restoreBatch,
                  type: 'default',
                  icon: 'lucide:archive-restore',
                  auth: ['edu:class:delete'],
                  disabled: isEmpty(restoreIds),
                  onClick: handleRestoreBatch,
                },
              ]"
            />
          </template>
          <template #status="{ row }">
            <StatusTag v-bind="getArchiveStatusMeta(row.status)" />
          </template>
          <template #empty>
            <GridEmpty
              :filtered="filtered"
              hint="新建班级后，可以在这里管理班级学生，再到课程详情里绑定课程。"
              subject="班级"
            />
          </template>
          <template #actions="{ row }">
            <TableAction
              :actions="[
                {
                  label: '学生管理',
                  type: 'link',
                  size: 'small',
                  icon: 'lucide:users',
                  auth: ['edu:class:query'],
                  ifShow: !isArchived(row.status),
                  onClick: handleManageStudents.bind(null, row),
                },
                {
                  label: $t('common.edit'),
                  type: 'link',
                  size: 'small',
                  icon: ACTION_ICON.EDIT,
                  auth: ['edu:class:update'],
                  ifShow: !isArchived(row.status),
                  onClick: handleEdit.bind(null, row),
                },
                {
                  label: ARCHIVE_COPY.archive,
                  type: 'link',
                  size: 'small',
                  icon: 'lucide:archive',
                  auth: ['edu:class:delete'],
                  ifShow: !isArchived(row.status),
                  popConfirm: {
                    ...rowConfirm('封存', '班级', row.className, ARCHIVE_HINT),
                    confirm: handleDelete.bind(null, row),
                  },
                },
                {
                  label: ARCHIVE_COPY.restore,
                  type: 'link',
                  size: 'small',
                  icon: 'lucide:archive-restore',
                  auth: ['edu:class:delete'],
                  ifShow: isArchived(row.status),
                  popConfirm: {
                    ...rowConfirm('恢复', '班级', row.className, RESTORE_HINT),
                    confirm: handleRestore.bind(null, row),
                  },
                },
              ]"
            />
          </template>
        </Grid>
      </div>
    </div>
  </Page>
</template>

<style scoped>
/*
 * 左侧组织树占去一块宽度：工具栏放不下时标题不折行，按钮逐个换行并靠右；
 * 1280 以下标题与刷新、全屏等图标留在第一行，操作按钮整行放在下面。
 */
@media (width < 1280px) {
  .tree-grid :deep(.vxe-toolbar .vxe-tools--operate) {
    order: 1;
  }

  .tree-grid :deep(.vxe-toolbar .vxe-tools--wrapper) {
    flex-basis: 100%;
    order: 2;
  }
}

.tree-grid :deep(.vxe-toolbar) {
  flex-wrap: wrap;
}

.tree-grid :deep(.vxe-toolbar .vxe-buttons--wrapper) {
  white-space: nowrap;
}

.tree-grid :deep(.vxe-toolbar .vxe-tools--wrapper) {
  margin-left: auto;
}

.tree-grid :deep(.vxe-toolbar .vxe-tool--item-wrapper) {
  flex-wrap: wrap;
  gap: var(--nus-space-2);
  justify-content: flex-end;
}

.tree-grid :deep(.vxe-tool--item-wrapper .table-actions),
.tree-grid :deep(.vxe-tool--item-wrapper .table-actions > .ant-space) {
  display: contents;
}

.toolbar-switch {
  display: inline-flex;
  align-items: center;
  margin-right: var(--nus-space-2);
}

.toolbar-switch-label {
  margin-right: var(--nus-space-2);
  font-size: var(--nus-fs-dense);
  color: var(--nus-text-secondary);
  white-space: nowrap;
}
</style>
