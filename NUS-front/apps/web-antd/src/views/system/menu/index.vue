<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemMenuApi } from '#/api/system/menu';

import { ref } from 'vue';

import { confirm, Page, useVbenModal } from '@vben/common-ui';
import { SystemMenuTypeEnum } from '@vben/constants';
import { IconifyIcon } from '@vben/icons';
import { isEmpty } from '@vben/utils';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteMenu, deleteMenuList, getMenuList } from '#/api/system/menu';
import { $t } from '#/locales';
import {
  batchConfirm,
  deleteBatchConfirm,
  deleteRowConfirm,
} from '#/utils/confirm-copy';
import GridEmpty from '#/views/system/modules/grid-empty.vue';

import { useGridColumns } from './data';
import Form from './modules/form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 删除菜单的后果 */
const MENU_DELETE_HINT = '删除后不能恢复，各角色里对应的菜单权限也会移除。';

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 创建菜单 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 添加下级菜单 */
function handleAppend(row: SystemMenuApi.Menu) {
  formModalApi.setData({ parentId: row.id }).open();
}

/** 编辑菜单 */
function handleEdit(row: SystemMenuApi.Menu) {
  formModalApi.setData(row).open();
}

/** 删除菜单 */
async function handleDelete(row: SystemMenuApi.Menu) {
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
  });
  try {
    await deleteMenu(row.id!);
    message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 批量删除菜单 */
async function handleDeleteBatch() {
  const selectedIds = [...checkedIds.value];
  if (selectedIds.length === 0) {
    return;
  }
  const menus = await getMenuList();
  const { childrenMap, parentMap } = buildMenuRelationMap(menus);
  const hasChildren = selectedIds.some(
    (id) => (childrenMap.get(id)?.length ?? 0) > 0,
  );
  if (hasChildren) {
    const copy = batchConfirm(
      '删除',
      '菜单及其子菜单',
      selectedIds.length,
      `所选菜单下还有子菜单，会一并删除。${MENU_DELETE_HINT}`,
    );
    await confirm(copy);
  } else {
    await confirm(
      deleteBatchConfirm('菜单', selectedIds.length, MENU_DELETE_HINT),
    );
  }

  const deleteIds = hasChildren
    ? collectSelfAndDescendantIds(selectedIds, childrenMap)
    : new Set(selectedIds);
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deletingBatch'),
    duration: 0,
  });
  try {
    const deleteBatches = buildDeleteBatchesByDepth(deleteIds, parentMap);
    for (const ids of deleteBatches) {
      await deleteMenuList(ids);
    }
    checkedIds.value = [];
    await gridApi.grid.clearCheckboxRow();
    message.success($t('ui.actionMessage.deleteSuccess'));
    handleRefresh();
  } finally {
    hideLoading();
  }
}

function buildMenuRelationMap(menus: SystemMenuApi.Menu[]) {
  const childrenMap = new Map<number, number[]>();
  const parentMap = new Map<number, number>();
  for (const menu of menus) {
    parentMap.set(menu.id, menu.parentId ?? 0);
    const children = childrenMap.get(menu.parentId) ?? [];
    children.push(menu.id);
    childrenMap.set(menu.parentId, children);
  }
  return { childrenMap, parentMap };
}

function collectSelfAndDescendantIds(
  rootIds: number[],
  childrenMap: Map<number, number[]>,
) {
  const ids = new Set<number>();
  const stack = [...rootIds];
  while (stack.length > 0) {
    const currentId = stack.pop()!;
    if (ids.has(currentId)) {
      continue;
    }
    ids.add(currentId);
    const children = childrenMap.get(currentId) ?? [];
    stack.push(...children);
  }
  return ids;
}

function buildDeleteBatchesByDepth(
  ids: Set<number>,
  parentMap: Map<number, number>,
) {
  const depthGroupMap = new Map<number, number[]>();
  for (const id of ids) {
    let depth = 0;
    let parentId = parentMap.get(id) ?? 0;
    while (parentId !== 0) {
      depth += 1;
      parentId = parentMap.get(parentId) ?? 0;
    }
    const groupIds = depthGroupMap.get(depth) ?? [];
    groupIds.push(id);
    depthGroupMap.set(depth, groupIds);
  }
  return [...depthGroupMap.entries()]
    .toSorted(([depthA], [depthB]) => depthB - depthA)
    .map(([, groupIds]) => groupIds);
}

/** 切换树形展开/收缩状态 */
const isExpanded = ref(false);
function handleExpand() {
  isExpanded.value = !isExpanded.value;
  gridApi.grid.setAllTreeExpand(isExpanded.value);
}

const checkedIds = ref<number[]>([]);
function handleRowCheckboxChange({
  records,
}: {
  records: SystemMenuApi.Menu[];
}) {
  checkedIds.value = records.map((item) => item.id!);
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
        query: async (_params) => {
          return await getMenuList();
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
    },
    treeConfig: {
      parentField: 'parentId',
      rowField: 'id',
      transform: true,
      reserve: true,
    },
  } as VxeTableGridOptions<SystemMenuApi.Menu>,
  gridEvents: {
    checkboxAll: handleRowCheckboxChange,
    checkboxChange: handleRowCheckboxChange,
  },
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="菜单列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: $t('ui.actionTitle.create', ['菜单']),
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['system:menu:create'],
              onClick: handleCreate,
            },
            {
              label: isExpanded ? '收起全部' : '展开全部',
              type: 'default',
              onClick: handleExpand,
            },
            {
              label: $t('ui.actionTitle.deleteBatch'),
              type: 'default',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['system:menu:delete'],
              disabled: isEmpty(checkedIds),
              onClick: handleDeleteBatch,
            },
          ]"
        />
      </template>
      <template #name="{ row }">
        <div class="menu-name">
          <span class="menu-name__icon">
            <IconifyIcon
              v-if="row.type === SystemMenuTypeEnum.BUTTON"
              icon="carbon:square-outline"
              class="size-full"
            />
            <IconifyIcon
              v-else-if="row.icon"
              :icon="row.icon || 'carbon:circle-dash'"
              class="size-full"
            />
          </span>
          <span class="menu-name__text">{{ $t(row.name) }}</span>
        </div>
      </template>
      <template #empty>
        <GridEmpty
          hint="新建目录或菜单后，会按层级显示在这里。"
          subject="菜单"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '新建下级',
              type: 'link',
              icon: ACTION_ICON.ADD,
              auth: ['system:menu:create'],
              onClick: handleAppend.bind(null, row),
            },
            {
              label: $t('common.edit'),
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['system:menu:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: $t('common.delete'),
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['system:menu:delete'],
              popConfirm: {
                ...deleteRowConfirm('菜单', $t(row.name), MENU_DELETE_HINT),
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>

<style scoped>
.menu-name {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  width: 100%;
  min-width: 0;
}

.menu-name__icon {
  flex: none;
  width: 16px;
  height: 16px;
  color: var(--nus-text-secondary);
}

.menu-name__text {
  flex: auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
