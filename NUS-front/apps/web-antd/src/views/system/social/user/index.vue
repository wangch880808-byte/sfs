<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemSocialUserApi } from '#/api/system/social/user';

import { ref } from 'vue';

import { confirm, Page, useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteSocialUser, getSocialUserPage } from '#/api/system/social/user';
import { $t } from '#/locales';
import { hasActiveFilters } from '#/utils/confirm-copy';
import GridEmpty from '#/views/system/modules/grid-empty.vue';

import { useGridColumns, useGridFormSchema } from './data';
import Detail from './modules/detail.vue';

const [DetailModal, detailModalApi] = useVbenModal({
  connectedComponent: Detail,
  destroyOnClose: true,
});

/** 筛选条件是否生效：决定空状态的写法 */
const filtered = ref(false);

/** 查看详情 */
function handleDetail(row: SystemSocialUserApi.SocialUser) {
  detailModalApi.setData(row).open();
}

async function handleDelete(row: SystemSocialUserApi.SocialUser) {
  if (!row.id) {
    return;
  }
  const accountText = row.userAccount
    ? `${row.userAccount}${row.userNickname ? `（${row.userNickname}）` : ''}`
    : '未关联平台账号';
  await confirm({
    cancelText: '返回',
    confirmText: '解除绑定',
    content: `解除后与平台账号「${accountText}」的绑定关系会删除，这条记录从列表移除；对方需要重新绑定才能再用三方账号登录。`,
    title: `解除三方账号「${row.nickname || row.openid}」的绑定？`,
  });
  await deleteSocialUser(row.id);
  message.success('已解除绑定');
  gridApi.query();
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
          filtered.value = hasActiveFilters(formValues);
          return await getSocialUserPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
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
  } as VxeTableGridOptions<SystemSocialUserApi.SocialUser>,
});
</script>

<template>
  <Page auto-content-height>
    <DetailModal />
    <Grid table-title="社交用户列表">
      <template #empty>
        <GridEmpty
          :filtered="filtered"
          hint="用户用三方账号登录或绑定后，会显示在这里。"
          subject="社交用户"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: $t('common.detail'),
              type: 'link',
              icon: ACTION_ICON.VIEW,
              auth: ['system:social-user:query'],
              onClick: handleDetail.bind(null, row),
            },
            {
              label: '解除绑定',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['system:social-user:delete'],
              ifShow: !!row.id,
              onClick: handleDelete.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
