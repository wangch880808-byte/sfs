<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { EduClassApi } from '#/api/edu/class';
import type { SystemDeptApi } from '#/api/system/dept';
import type { SystemUserApi } from '#/api/system/user';
import type { SystemUserProfileExtApi } from '#/api/system/user-profile';

import { computed, onMounted, ref } from 'vue';

import { confirm, Page, useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart, isEmpty } from '@vben/utils';

import { Card, message, Switch } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getSimpleClassList } from '#/api/edu/class';
import { exportStudentMasterData } from '#/api/edu/data-exchange';
import {
  disableEduUser,
  disableEduUserList,
  getEduUserPage,
  restoreEduUser,
  restoreEduUserList,
} from '#/api/edu/user';
import { getSimpleRoleList } from '#/api/system/role';
import { exportUser } from '#/api/system/user';
import { getSimpleUserProfileList } from '#/api/system/user-profile';
import ImportWizard from '#/components/data-exchange/import-wizard.vue';
import { StatusTag } from '#/components/status-tag';
import { $t } from '#/locales';
import {
  ACCOUNT_COPY,
  disabledMessage,
  disablingMessage,
  isArchived,
  restoredMessage,
} from '#/utils/archive';
import {
  batchConfirm,
  hasActiveFilters,
  rowConfirm,
} from '#/utils/confirm-copy';
import {
  IDENTITY_ROLE_CODE_BY_USER_TYPE,
  STUDENT_ROLE_CODE,
} from '#/utils/role-codes';
import { getAccountStatusMeta } from '#/utils/status-admin';
import GridEmpty from '#/views/system/modules/grid-empty.vue';
import { useGridColumns, useGridFormSchema } from '#/views/system/user/data';
import AssignRoleForm from '#/views/system/user/modules/assign-role-form.vue';
import DeptTree from '#/views/system/user/modules/dept-tree.vue';
import Form from '#/views/system/user/modules/form.vue';
import ResetPasswordForm from '#/views/system/user/modules/reset-password-form.vue';

import { adaptEduUserColumns } from './user-columns';

interface Props {
  entityName: string;
  userType: 1 | 2;
}

interface UserTableRow extends SystemUserApi.User {
  classNames?: string;
  professionalTitle?: number;
}

const props = defineProps<Props>();
const TEACHER_USER_TYPE = 1;
const STUDENT_USER_TYPE = 2;

const accountLabel = computed(() =>
  props.userType === TEACHER_USER_TYPE ? '教师工号' : '学生学号',
);
const identityRoleCode = computed(
  () => IDENTITY_ROLE_CODE_BY_USER_TYPE[props.userType] ?? STUDENT_ROLE_CODE,
);

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

const tableTitle = computed(() => `${props.entityName}列表`);
const exportFileName = computed(() => `${props.entityName}.xls`);
const accountImportOpen = ref(false);
const importBizType = computed(() =>
  props.userType === TEACHER_USER_TYPE ? 'TEACHER_MASTER' : 'STUDENT_MASTER',
);
const importTitle = computed(() => `导入${props.entityName}`);
const importTemplateFileName = computed(() =>
  props.userType === TEACHER_USER_TYPE
    ? '教师账号导入模板.xlsx'
    : '学生主数据导入模板.xlsx',
);

const searchDeptId = ref<number | undefined>(undefined);
/** 筛选条件或组织是否生效：决定空状态的写法 */
const filtered = ref(false);
/** 账号确认框里的对象与后果 */
const accountSubject = computed(() => `${props.entityName}账号`);
const DISABLE_HINT = '停用后无法登录，可随时恢复。';
const RESTORE_HINT = '恢复后可以正常登录。';
const emptyHint = computed(() =>
  props.userType === TEACHER_USER_TYPE
    ? '新建或导入教师后，会显示在这里。'
    : '新建或导入学生后，会显示在这里。',
);
const checkedRows = ref<UserTableRow[]>([]);
const includeArchived = ref(false);
const disableIds = computed(() =>
  checkedRows.value
    .filter((row) => !isArchived(row.status))
    .map((row) => row.id!),
);
const restoreIds = computed(() =>
  checkedRows.value
    .filter((row) => isArchived(row.status))
    .map((row) => row.id!),
);
const userProfileMap = ref<
  Map<number, SystemUserProfileExtApi.UserProfileSimple>
>(new Map());
const classNameMap = ref<Map<number, string>>(new Map());
const shouldReloadProfile = ref(true);
const shouldReloadClass = ref(true);
let profileLoadingPromise: null | Promise<void> = null;
let classLoadingPromise: null | Promise<void> = null;

async function loadUserProfileMap(force = false) {
  if (!force && !shouldReloadProfile.value) {
    return;
  }
  if (profileLoadingPromise) {
    return profileLoadingPromise;
  }
  profileLoadingPromise = (async () => {
    const list = await getSimpleUserProfileList({ userType: props.userType });
    const nextMap = new Map<
      number,
      SystemUserProfileExtApi.UserProfileSimple
    >();
    (list || []).forEach((item) => {
      nextMap.set(Number(item.userId), item);
    });
    userProfileMap.value = nextMap;
    shouldReloadProfile.value = false;
  })().finally(() => {
    profileLoadingPromise = null;
  });
  return profileLoadingPromise;
}

async function loadClassNameMap(force = false) {
  if (props.userType !== STUDENT_USER_TYPE) {
    return;
  }
  if (!force && !shouldReloadClass.value) {
    return;
  }
  if (classLoadingPromise) {
    return classLoadingPromise;
  }
  classLoadingPromise = (async () => {
    const list = await getSimpleClassList();
    const nextMap = new Map<number, string>();
    (list || []).forEach((item: EduClassApi.ClazzSimple) => {
      nextMap.set(Number(item.id), item.className);
    });
    classNameMap.value = nextMap;
    shouldReloadClass.value = false;
  })().finally(() => {
    classLoadingPromise = null;
  });
  return classLoadingPromise;
}

async function ensureExtraData() {
  await Promise.all([loadUserProfileMap(), loadClassNameMap()]);
}

function resolveClassNames(classIds?: number[]) {
  if (!Array.isArray(classIds) || classIds.length === 0) {
    return '-';
  }
  const names = classIds
    .map((id) => classNameMap.value.get(Number(id)))
    .filter(Boolean);
  return names.length > 0 ? names.join('、') : '-';
}

function mergeUserRows(rows: SystemUserApi.User[] = []): UserTableRow[] {
  return rows.map((row) => {
    const profile = userProfileMap.value.get(Number(row.id));
    return {
      ...row,
      profile,
      professionalTitle: profile?.professionalTitle,
      classNames:
        props.userType === STUDENT_USER_TYPE
          ? resolveClassNames(profile?.classIds)
          : undefined,
    };
  });
}

/** 刷新表格 */
function handleRefresh() {
  checkedRows.value = [];
  shouldReloadProfile.value = true;
  shouldReloadClass.value = true;
  gridApi.query();
}

/** 导出表格 */
async function handleExport() {
  const params = {
    ...(await gridApi.formApi.getValues()),
    deptId: searchDeptId.value,
    userType: props.userType,
  };
  const data =
    props.userType === STUDENT_USER_TYPE
      ? await exportStudentMasterData(params)
      : await exportUser(params);
  downloadFileFromBlobPart({
    fileName:
      props.userType === STUDENT_USER_TYPE
        ? '学生主数据.xlsx'
        : exportFileName.value,
    source: data,
  });
}

/** 选择组织 */
async function handleDeptSelect(dept: SystemDeptApi.Dept) {
  searchDeptId.value = dept.id;
  handleRefresh();
}

/** 新建用户 */
async function handleCreate() {
  const roles = await getSimpleRoleList();
  const identityRole = roles.find(
    (role) => role.code === identityRoleCode.value,
  );
  if (!identityRole?.id) {
    message.error(
      `无法新建${props.entityName}账号：系统里找不到${props.entityName}角色。请管理员在「角色管理」里检查角色配置。`,
    );
    return;
  }
  formModalApi
    .setData({
      userType: props.userType,
      roleIds: [identityRole.id],
      lockUserType: true,
      accountLabel: accountLabel.value,
      entityName: props.entityName,
    } as unknown as SystemUserApi.User)
    .open();
}

/** 导入用户 */
function handleImport() {
  accountImportOpen.value = true;
}

/** 编辑用户 */
function handleEdit(row: SystemUserApi.User) {
  formModalApi
    .setData({
      ...row,
      userType: props.userType,
      lockUserType: true,
      accountLabel: accountLabel.value,
      entityName: props.entityName,
    } as unknown as SystemUserApi.User)
    .open();
}

/** 停用教务账号 */
async function handleDelete(row: SystemUserApi.User) {
  const hideLoading = message.loading({
    content: disablingMessage(row.username),
    duration: 0,
  });
  try {
    await disableEduUser(row.id!);
    message.success(disabledMessage(row.username));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 批量停用教务账号 */
async function handleDeleteBatch() {
  await confirm(
    batchConfirm(
      '停用',
      accountSubject.value,
      disableIds.value.length,
      DISABLE_HINT,
    ),
  );
  const hideLoading = message.loading({
    content: ACCOUNT_COPY.disablingBatch,
    duration: 0,
  });
  try {
    await disableEduUserList(disableIds.value);
    checkedRows.value = [];
    message.success(ACCOUNT_COPY.disabledBatch);
    handleRefresh();
  } finally {
    hideLoading();
  }
}

async function handleRestore(row: SystemUserApi.User) {
  await restoreEduUser(row.id!);
  message.success(restoredMessage(row.username));
  handleRefresh();
}

async function handleRestoreBatch() {
  await confirm(
    batchConfirm(
      '恢复',
      accountSubject.value,
      restoreIds.value.length,
      RESTORE_HINT,
    ),
  );
  await restoreEduUserList(restoreIds.value);
  checkedRows.value = [];
  message.success(ACCOUNT_COPY.restoredBatch);
  handleRefresh();
}

function handleRowCheckboxChange({ records }: { records: UserTableRow[] }) {
  checkedRows.value = records;
}

/** 重置密码 */
function handleResetPassword(row: SystemUserApi.User) {
  resetPasswordModalApi.setData(row).open();
}

/** 分配角色 */
function handleAssignRole(row: SystemUserApi.User) {
  assignRoleModalApi.setData(row).open();
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    // 左侧有组织树，1280 以下筛选项每行两个
    wrapperClass: 'grid-cols-1 md:grid-cols-2 xl:grid-cols-3',
    schema: useGridFormSchema({
      accountLabel: accountLabel.value,
    }).filter((item) => item.fieldName !== 'userType'),
  },
  gridOptions: {
    columns: adaptEduUserColumns(
      useGridColumns(() => Promise.resolve(false), {
        accountLabel: accountLabel.value,
        showProfessionalTitle: props.userType === TEACHER_USER_TYPE,
        showClassNames: props.userType === STUDENT_USER_TYPE,
      }),
    ),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          filtered.value =
            hasActiveFilters(formValues) || searchDeptId.value !== undefined;
          await ensureExtraData();
          const pageResult = await getEduUserPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
            deptId: searchDeptId.value,
            userType: props.userType,
            includeArchived: includeArchived.value,
          });
          return {
            ...pageResult,
            list: mergeUserRows(pageResult.list),
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
  } as VxeTableGridOptions<UserTableRow>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});

onMounted(() => {
  void ensureExtraData();
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <ResetPasswordModal @success="handleRefresh" />
    <AssignRoleModal @success="handleRefresh" />
    <ImportWizard
      v-model:open="accountImportOpen"
      :allow-update="props.userType === TEACHER_USER_TYPE"
      :biz-type="importBizType"
      :template-file-name="importTemplateFileName"
      :title="importTitle"
      @success="handleRefresh"
    />

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
                {{ ACCOUNT_COPY.showDisabled }}
              </span>
              <Switch
                v-model:checked="includeArchived"
                :aria-label="ACCOUNT_COPY.showDisabled"
                @change="handleRefresh"
              />
            </span>
            <TableAction
              :actions="[
                {
                  label: `新建${props.entityName}`,
                  type: 'primary',
                  icon: ACTION_ICON.ADD,
                  auth: ['system:user:create'],
                  onClick: handleCreate,
                },
                {
                  label: $t('ui.actionTitle.export'),
                  type: 'default',
                  icon: ACTION_ICON.DOWNLOAD,
                  auth:
                    props.userType === STUDENT_USER_TYPE
                      ? ['system:user:query']
                      : ['system:user:export'],
                  onClick: handleExport,
                },
                {
                  label: $t('ui.actionTitle.import', [props.entityName]),
                  type: 'default',
                  icon: ACTION_ICON.UPLOAD,
                  auth:
                    props.userType === STUDENT_USER_TYPE
                      ? ['edu:data-exchange:import']
                      : ['system:user:import'],
                  onClick: handleImport,
                },
                {
                  label: ACCOUNT_COPY.disableBatch,
                  type: 'default',
                  icon: 'lucide:user-x',
                  disabled: isEmpty(disableIds),
                  auth: ['system:user:delete'],
                  onClick: handleDeleteBatch,
                },
                {
                  label: ACCOUNT_COPY.restoreBatch,
                  type: 'default',
                  icon: 'lucide:user-check',
                  disabled: isEmpty(restoreIds),
                  auth: ['system:user:delete'],
                  onClick: handleRestoreBatch,
                },
              ]"
            />
          </template>
          <template #status="{ row }">
            <StatusTag v-bind="getAccountStatusMeta(row.status)" />
          </template>
          <template #empty>
            <GridEmpty
              :filtered="filtered"
              :hint="emptyHint"
              :subject="accountSubject"
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
                  ifShow: !isArchived(row.status),
                  onClick: handleEdit.bind(null, row),
                },
                {
                  label: ACCOUNT_COPY.disable,
                  type: 'link',
                  icon: 'lucide:user-x',
                  auth: ['system:user:delete'],
                  ifShow: !isArchived(row.status),
                  popConfirm: {
                    ...rowConfirm('停用', '账号', row.username, DISABLE_HINT),
                    confirm: handleDelete.bind(null, row),
                  },
                },
                {
                  label: ACCOUNT_COPY.restore,
                  type: 'link',
                  icon: 'lucide:user-check',
                  auth: ['system:user:delete'],
                  ifShow: isArchived(row.status),
                  popConfirm: {
                    ...rowConfirm('恢复', '账号', row.username, RESTORE_HINT),
                    confirm: handleRestore.bind(null, row),
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
