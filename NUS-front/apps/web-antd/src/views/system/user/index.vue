<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemDeptApi } from '#/api/system/dept';
import type { SystemUserApi } from '#/api/system/user';

import { ref } from 'vue';

import { confirm, Page, useVbenModal } from '@vben/common-ui';
import { CommonStatusEnum } from '@vben/constants';
import { downloadFileFromBlobPart, isEmpty } from '@vben/utils';

import { Card, message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteUser,
  deleteUserList,
  exportUser,
  getUserPage,
  updateUserStatus,
} from '#/api/system/user';
import ImportWizard from '#/components/data-exchange/import-wizard.vue';
import { $t } from '#/locales';
import {
  deleteBatchConfirm,
  deleteRowConfirm,
  hasActiveFilters,
  rowConfirm,
} from '#/utils/confirm-copy';
import GridEmpty from '#/views/system/modules/grid-empty.vue';

import { useGridColumns, useGridFormSchema } from './data';
import AssignRoleForm from './modules/assign-role-form.vue';
import DeptTree from './modules/dept-tree.vue';
import Form from './modules/form.vue';
import ResetPasswordForm from './modules/reset-password-form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [ResetPasswordModal, resetPasswordModalApi] = useVbenModal({
  connectedComponent: ResetPasswordForm,
  destroyOnClose: true,
});

const [AssignRoleModal, assignRoleModalApi] = useVbenModal({
  connectedComponent: AssignRoleForm,
  destroyOnClose: true,
});

const importOpen = ref(false);
/** 筛选条件或组织是否生效：决定空状态的写法 */
const filtered = ref(false);

/** 删除用户的后果 */
const USER_DELETE_HINT =
  '删除后账号不能再登录，档案与班级关联一并删除，不能恢复。';

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 导出表格 */
async function handleExport() {
  const data = await exportUser(await gridApi.formApi.getValues());
  downloadFileFromBlobPart({ fileName: '用户.xls', source: data });
}

/** 选择组织 */
const searchDeptId = ref<number | undefined>(undefined);
async function handleDeptSelect(dept: SystemDeptApi.Dept) {
  searchDeptId.value = dept.id;
  handleRefresh();
}

/** 创建用户 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 导入用户 */
function handleImport() {
  importOpen.value = true;
}

/** 编辑用户 */
function handleEdit(row: SystemUserApi.User) {
  formModalApi.setData(row).open();
}

/** 删除用户 */
async function handleDelete(row: SystemUserApi.User) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.username]),
    duration: 0,
  });
  try {
    await deleteUser(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.username]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 批量删除用户 */
async function handleDeleteBatch() {
  await confirm(
    deleteBatchConfirm('用户', checkedIds.value.length, USER_DELETE_HINT),
  );
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deletingBatch'),
    duration: 0,
  });
  try {
    await deleteUserList(checkedIds.value);
    checkedIds.value = [];
    message.success($t('ui.actionMessage.deleteSuccess'));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

const checkedIds = ref<number[]>([]);
function handleRowCheckboxChange({
  records,
}: {
  records: SystemUserApi.User[];
}) {
  checkedIds.value = records.map((item) => item.id!);
}

/** 重置密码 */
function handleResetPassword(row: SystemUserApi.User) {
  resetPasswordModalApi.setData(row).open();
}

/** 分配角色 */
function handleAssignRole(row: SystemUserApi.User) {
  assignRoleModalApi.setData(row).open();
}

/** 更新用户状态 */
async function handleStatusChange(
  newStatus: number,
  row: SystemUserApi.User,
): Promise<boolean | undefined> {
  const enabling = newStatus === CommonStatusEnum.ENABLE;
  return new Promise((resolve, reject) => {
    const copy = rowConfirm(
      enabling ? '启用' : '停用',
      '用户',
      row.username,
      enabling
        ? '启用后该账号可以正常登录。'
        : '停用后该账号不能登录，可随时重新启用。',
    );
    confirm({
      cancelText: copy.cancelText,
      confirmText: copy.okText,
      content: copy.description,
      title: copy.title,
    })
      .then(async () => {
        // 更新用户状态
        await updateUserStatus(row.id!, newStatus);
        // 提示并返回成功
        message.success($t('ui.actionMessage.operationSuccess'));
        resolve(true);
      })
      .catch(() => {
        reject(new Error('取消操作'));
      });
  });
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    // 左侧有组织树，1280 以下筛选项每行两个
    wrapperClass: 'grid-cols-1 md:grid-cols-2 xl:grid-cols-3',
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(handleStatusChange),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          filtered.value =
            hasActiveFilters(formValues) || searchDeptId.value !== undefined;
          return await getUserPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
            deptId: searchDeptId.value,
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
  } as VxeTableGridOptions<SystemUserApi.User>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <ResetPasswordModal @success="handleRefresh" />
    <AssignRoleModal @success="handleRefresh" />
    <ImportWizard
      v-model:open="importOpen"
      allow-update
      biz-type="SYSTEM_USER"
      template-file-name="系统用户导入模板.xlsx"
      title="导入系统用户"
      @success="handleRefresh"
    />

    <div class="flex h-full w-full">
      <!-- 左侧组织树 -->
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
      <!-- 右侧用户列表 -->
      <div class="tree-grid min-w-0 flex-1">
        <Grid table-title="用户列表">
          <template #toolbar-tools>
            <TableAction
              :actions="[
                {
                  label: $t('ui.actionTitle.create', ['用户']),
                  type: 'primary',
                  icon: ACTION_ICON.ADD,
                  auth: ['system:user:create'],
                  onClick: handleCreate,
                },
                {
                  label: $t('ui.actionTitle.export'),
                  type: 'default',
                  icon: ACTION_ICON.DOWNLOAD,
                  auth: ['system:user:export'],
                  onClick: handleExport,
                },
                {
                  label: $t('ui.actionTitle.import', ['用户']),
                  type: 'default',
                  icon: ACTION_ICON.UPLOAD,
                  auth: ['system:user:import'],
                  onClick: handleImport,
                },
                {
                  label: $t('ui.actionTitle.deleteBatch'),
                  type: 'default',
                  danger: true,
                  icon: ACTION_ICON.DELETE,
                  disabled: isEmpty(checkedIds),
                  auth: ['system:user:delete'],
                  onClick: handleDeleteBatch,
                },
              ]"
            />
          </template>
          <template #empty>
            <GridEmpty
              :filtered="filtered"
              hint="新建或导入用户后，会显示在这里。"
              subject="用户"
            />
          </template>
          <template #actions="{ row }">
            <TableAction
              :actions="[
                {
                  label: $t('common.edit'),
                  type: 'link',
                  icon: ACTION_ICON.EDIT,
                  auth: ['system:user:update'],
                  onClick: handleEdit.bind(null, row),
                },
                {
                  label: $t('common.delete'),
                  type: 'link',
                  danger: true,
                  icon: ACTION_ICON.DELETE,
                  auth: ['system:user:delete'],
                  popConfirm: {
                    ...deleteRowConfirm('用户', row.username, USER_DELETE_HINT),
                    confirm: handleDelete.bind(null, row),
                  },
                },
              ]"
              :drop-down-actions="[
                {
                  label: '分配角色',
                  type: 'link',
                  auth: ['system:permission:assign-user-role'],
                  onClick: handleAssignRole.bind(null, row),
                },
                {
                  label: '重置密码',
                  type: 'link',
                  auth: ['system:user:update-password'],
                  onClick: handleResetPassword.bind(null, row),
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
</style>
