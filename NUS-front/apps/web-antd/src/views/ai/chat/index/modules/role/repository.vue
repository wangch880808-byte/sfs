<script setup lang="ts">
import type { AiChatConversationApi } from '#/api/ai/chat/conversation';
import type { AiModelChatRoleApi } from '#/api/ai/model/chatRole';

import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { confirm, useVbenDrawer, useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Button, Input, Tabs } from 'ant-design-vue';

import { createChatConversationMy } from '#/api/ai/chat/conversation';
import { deleteMy, getCategoryList, getMyPage } from '#/api/ai/model/chatRole';

import Form from '../../../../model/chatRole/modules/form.vue';
import RoleCategoryList from './category-list.vue';
import RoleList from './list.vue';

const router = useRouter();

const [Drawer] = useVbenDrawer({
  title: '角色仓库',
  footer: false,
  class: 'w-2/5',
});

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const loading = ref<boolean>(false); // 加载中
const activeTab = ref<string>('my-role'); // 选中的角色 Tab
const search = ref<string>(''); // 加载中
const myRoleParams = reactive({
  pageNo: 1,
  pageSize: 50,
});
const myRoleList = ref<AiModelChatRoleApi.ChatRole[]>([]); // my 分页大小
const publicRoleParams = reactive({
  pageNo: 1,
  pageSize: 50,
});
const publicRoleList = ref<AiModelChatRoleApi.ChatRole[]>([]); // public 分页大小
const activeCategory = ref<string>('全部'); // 选择中的分类
const categoryList = ref<string[]>([]); // 角色分类类别

/** tabs 点击 */
async function handleTabsClick(tab: any) {
  // 设置切换状态
  activeTab.value = tab;
  // 切换的时候重新加载数据
  await getActiveTabsRole();
}

/** 获取 my role 我的角色 */
async function getMyRole(append?: boolean) {
  const params: AiModelChatRoleApi.ChatRolePageReqVO = {
    ...myRoleParams,
    name: search.value,
    publicStatus: false,
  };
  const { list } = await getMyPage(params);
  if (append) {
    myRoleList.value.push(...list);
  } else {
    myRoleList.value = list;
  }
}

/** 获取 public role 公共角色 */
async function getPublicRole(append?: boolean) {
  const params: AiModelChatRoleApi.ChatRolePageReqVO = {
    ...publicRoleParams,
    category: activeCategory.value === '全部' ? '' : activeCategory.value,
    name: search.value,
    publicStatus: true,
  };
  const { list } = await getMyPage(params);
  if (append) {
    publicRoleList.value.push(...list);
  } else {
    publicRoleList.value = list;
  }
}

/** 获取选中的 tabs 角色 */
async function getActiveTabsRole() {
  if (activeTab.value === 'my-role') {
    myRoleParams.pageNo = 1;
    await getMyRole();
  } else {
    publicRoleParams.pageNo = 1;
    await getPublicRole();
  }
}

/** 获取角色分类列表 */
async function getRoleCategoryList() {
  categoryList.value = ['全部', ...(await getCategoryList())];
}

/** 处理分类点击 */
async function handlerCategoryClick(category: string) {
  // 切换选择的分类
  activeCategory.value = category;
  // 筛选
  await getActiveTabsRole();
}

async function handlerAddRole() {
  formModalApi.setData({ formType: 'my-create' }).open();
}

/** 编辑角色 */
async function handlerCardEdit(role: any) {
  formModalApi.setData({ formType: 'my-update', id: role.id }).open();
}

/** 添加角色成功 */
async function handlerAddRoleSuccess() {
  // 刷新数据
  await getActiveTabsRole();
}

/** 删除角色 */
async function handlerCardDelete(role: any) {
  await confirm({
    cancelText: '返回',
    confirmText: '删除',
    content: '删除后不能恢复；已经用这个角色开始的对话会保留。',
    icon: 'warning',
    title: `删除角色「${role.name}」？`,
  });
  await deleteMy(role.id);
  // 刷新数据
  await getActiveTabsRole();
}

/** 角色分页：获取下一页 */
async function handlerCardPage(type: string) {
  try {
    loading.value = true;
    if (type === 'public') {
      publicRoleParams.pageNo++;
      await getPublicRole(true);
    } else {
      myRoleParams.pageNo++;
      await getMyRole(true);
    }
  } finally {
    loading.value = false;
  }
}

/** 选择 card 角色：新建聊天对话 */
async function handlerCardUse(role: any) {
  // 1. 创建对话
  const data: AiChatConversationApi.ChatConversation = {
    roleId: role.id,
  } as unknown as AiChatConversationApi.ChatConversation;
  const conversationId = await createChatConversationMy(data);

  // 2. 跳转页面
  await router.push({
    path: '/ai/chat',
    query: {
      conversationId,
    },
  });
}

/** 初始化 */
onMounted(async () => {
  // 获取分类
  await getRoleCategoryList();
  // 获取 role 数据
  await getActiveTabsRole();
});
</script>

<template>
  <Drawer>
    <div class="role-repo">
      <FormModal @success="handlerAddRoleSuccess" />

      <div class="role-repo__toolbar">
        <!-- 搜索输入框 -->
        <Input.Search
          :loading="loading"
          v-model:value="search"
          class="w-60"
          placeholder="搜索角色"
          @search="getActiveTabsRole"
        />
        <Button
          v-if="activeTab === 'my-role'"
          type="primary"
          @click="handlerAddRole"
        >
          <IconifyIcon icon="lucide:plus" class="size-4" />
          新建角色
        </Button>
      </div>
      <!-- 标签页内容 -->
      <Tabs
        v-model:value="activeTab"
        class="role-repo__tabs"
        @tab-click="handleTabsClick"
      >
        <Tabs.TabPane key="my-role" class="role-repo__pane" tab="我的角色">
          <RoleList
            :loading="loading"
            :role-list="myRoleList"
            :show-more="true"
            @on-delete="handlerCardDelete"
            @on-edit="handlerCardEdit"
            @on-use="handlerCardUse"
            @on-page="handlerCardPage('my')"
          />
        </Tabs.TabPane>
        <Tabs.TabPane key="public-role" class="role-repo__pane" tab="公共角色">
          <RoleCategoryList
            :category-list="categoryList"
            :active="activeCategory"
            @on-category-click="handlerCategoryClick"
          />
          <RoleList
            :role-list="publicRoleList"
            @on-delete="handlerCardDelete"
            @on-edit="handlerCardEdit"
            @on-use="handlerCardUse"
            @on-page="handlerCardPage('public')"
            class="mt-4"
            loading
          />
        </Tabs.TabPane>
      </Tabs>
    </div>
  </Drawer>
</template>

<style scoped>
/* 角色仓库抽屉（V1.0.1.6 界面规范）：搜索与添加放在页签栏右侧，内容区自己滚动 */
.role-repo {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--nus-bg-elevated);
}

.role-repo__toolbar {
  position: absolute;
  top: var(--nus-space-3);
  right: var(--nus-space-4);
  z-index: 10;
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
}

.role-repo__toolbar :deep(.ant-btn-primary) {
  display: inline-flex;
  gap: var(--nus-space-1);
  align-items: center;
}

.role-repo__tabs {
  height: 100%;
  padding: 0 var(--nus-space-4);
}

.role-repo__tabs :deep(.ant-tabs-nav) {
  height: 56px;
  margin-bottom: var(--nus-space-4);
}

.role-repo__tabs :deep(.ant-tabs-content-holder),
.role-repo__tabs :deep(.ant-tabs-content) {
  height: 100%;
}

.role-repo__pane {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow-y: auto;
}
</style>
