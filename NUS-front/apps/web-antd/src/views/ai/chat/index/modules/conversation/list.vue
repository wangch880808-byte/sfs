<script setup lang="ts">
import type { PropType } from 'vue';

import type { AiChatConversationApi } from '#/api/ai/chat/conversation';

import { computed, h, onMounted, ref, toRefs, watch } from 'vue';

import { confirm, prompt, useVbenDrawer } from '@vben/common-ui';
import { IconifyIcon, SvgGptIcon } from '@vben/icons';

import { Avatar, Button, Input, message, Spin, Tooltip } from 'ant-design-vue';

import {
  createChatConversationMy,
  deleteChatConversationMy,
  deleteChatConversationMyByUnpinned,
  getChatConversationMyList,
  updateChatConversationMy,
} from '#/api/ai/chat/conversation';
import { EmptyState } from '#/components/empty-state';
import { $t } from '#/locales';

import RoleRepository from '../role/repository.vue';

const props = defineProps({
  activeId: {
    type: [Number, null] as PropType<null | number>,
    default: null,
  },
});

const emits = defineEmits([
  'onConversationCreate',
  'onConversationClick',
  'onConversationClear',
  'onConversationDelete',
]);

const [Drawer, drawerApi] = useVbenDrawer({
  connectedComponent: RoleRepository,
});

const searchName = ref<string>(''); // 对话搜索
const activeConversationId = ref<null | number>(null); // 选中的对话，默认为 null
const conversationList = ref([] as AiChatConversationApi.ChatConversation[]); // 对话列表
const conversationMap = ref<any>({}); // 对话分组 (置顶、今天、三天前、一星期前、一个月前)
const loading = ref<boolean>(false); // 加载中
const loadingTime = ref<any>();
const listReady = ref(false); // 第一次读完列表之前不显示空状态，避免一闪而过

/** 当前分组里一共显示多少个对话（搜索后可能为 0） */
const visibleCount = computed(() =>
  Object.values(conversationMap.value as Record<string, unknown[]>).reduce(
    (total, list) => total + list.length,
    0,
  ),
);

/** 搜索对话 */
async function searchConversation() {
  // 恢复数据
  if (searchName.value.trim().length === 0) {
    conversationMap.value = await getConversationGroupByCreateTime(
      conversationList.value,
    );
  } else {
    // 过滤
    const filterValues = conversationList.value.filter((item) => {
      return item.title.includes(searchName.value.trim());
    });
    conversationMap.value =
      await getConversationGroupByCreateTime(filterValues);
  }
}

/** 点击对话 */
async function handleConversationClick(id: number) {
  // 过滤出选中的对话
  const filterConversation = conversationList.value.find((item) => {
    return item.id === id;
  });
  // 回调 onConversationClick
  // noinspection JSVoidFunctionReturnValueUsed
  const success = emits('onConversationClick', filterConversation) as any;
  // 切换对话
  if (success) {
    activeConversationId.value = id;
  }
}

/** 获取对话列表 */
async function getChatConversationList() {
  try {
    // 加载中
    loadingTime.value = setTimeout(() => {
      loading.value = true;
    }, 50);

    // 1.1 获取 对话数据
    conversationList.value = await getChatConversationMyList();
    // 1.2 排序
    conversationList.value.toSorted((a, b) => {
      return Number(b.createTime) - Number(a.createTime);
    });
    // 1.3 没有任何对话情况
    if (conversationList.value.length === 0) {
      activeConversationId.value = null;
      conversationMap.value = {};
      return;
    }

    // 2. 对话根据时间分组(置顶、今天、一天前、三天前、七天前、30 天前)
    conversationMap.value = await getConversationGroupByCreateTime(
      conversationList.value,
    );
  } finally {
    // 清理定时器
    if (loadingTime.value) {
      clearTimeout(loadingTime.value);
    }
    // 加载完成
    loading.value = false;
    listReady.value = true;
  }
}

/** 按照 creteTime 创建时间，进行分组 */
async function getConversationGroupByCreateTime(
  list: AiChatConversationApi.ChatConversation[],
) {
  // 排序、指定、时间分组(今天、一天前、三天前、七天前、30天前)
  // noinspection NonAsciiCharacters
  const groupMap: any = {
    置顶: [],
    今天: [],
    一天前: [],
    三天前: [],
    七天前: [],
    三十天前: [],
  };
  // 当前时间的时间戳
  const now = Date.now();
  // 定义时间间隔常量（单位：毫秒）
  const oneDay = 24 * 60 * 60 * 1000;
  const threeDays = 3 * oneDay;
  const sevenDays = 7 * oneDay;
  const thirtyDays = 30 * oneDay;
  for (const conversation of list) {
    // 置顶
    if (conversation.pinned) {
      groupMap['置顶'].push(conversation);
      continue;
    }
    // 计算时间差（单位：毫秒）
    const diff = now - Number(conversation.createTime);
    // 根据时间间隔判断
    if (diff < oneDay) {
      groupMap['今天'].push(conversation);
    } else if (diff < threeDays) {
      groupMap['一天前'].push(conversation);
    } else if (diff < sevenDays) {
      groupMap['三天前'].push(conversation);
    } else if (diff < thirtyDays) {
      groupMap['七天前'].push(conversation);
    } else {
      groupMap['三十天前'].push(conversation);
    }
  }
  return groupMap;
}

async function createConversation() {
  // 1. 新建对话
  const conversationId = await createChatConversationMy(
    {} as unknown as AiChatConversationApi.ChatConversation,
  );
  // 2. 获取对话内容
  await getChatConversationList();
  // 3. 选中对话
  await handleConversationClick(conversationId);
  // 4. 回调
  emits('onConversationCreate');
}

/** 修改对话的标题 */
async function updateConversationTitle(
  conversation: AiChatConversationApi.ChatConversation,
) {
  // 1. 二次确认
  await prompt({
    async beforeClose(scope) {
      if (scope.isConfirm) {
        if (scope.value) {
          try {
            // 2. 发起修改
            await updateChatConversationMy({
              id: conversation.id,
              title: scope.value,
            } as AiChatConversationApi.ChatConversation);
            message.success('已重命名对话');
            // 3. 刷新列表
            await getChatConversationList();
            // 4. 过滤当前切换的
            const filterConversationList = conversationList.value.filter(
              (item) => {
                return item.id === conversation.id;
              },
            );
            if (
              filterConversationList.length > 0 &&
              filterConversationList[0] && // tip：避免切换对话
              activeConversationId.value === filterConversationList[0].id!
            ) {
              emits('onConversationClick', filterConversationList[0]);
            }
          } catch {
            return false;
          }
        } else {
          message.warning('请输入对话标题');
          return false;
        }
      }
    },
    component: () => {
      return h(Input, {
        placeholder: '请输入对话标题',
        allowClear: true,
        defaultValue: conversation.title,
        rules: [{ required: true, message: '请输入对话标题' }],
      });
    },
    content: '新的标题会显示在左侧对话列表里。',
    title: '重命名对话',
    confirmText: '保存',
    cancelText: '返回',
    modelPropName: 'value',
  });
}

/** 删除聊天对话 */
async function deleteChatConversation(
  conversation: AiChatConversationApi.ChatConversation,
) {
  // 删除的二次确认
  await confirm({
    cancelText: '返回',
    confirmText: '删除',
    content: '对话和里面的消息会一起删除，删除后不能恢复。',
    icon: 'warning',
    title: `删除对话「${conversation.title || '未命名对话'}」？`,
  });
  // 发起删除
  await deleteChatConversationMy(conversation.id);
  message.success('对话已删除');
  // 刷新列表
  await getChatConversationList();
  // 回调
  emits('onConversationDelete', conversation);
}

/** 清空未置顶的对话 */
async function handleClearConversation() {
  await confirm({
    cancelText: '返回',
    confirmText: '清空',
    content:
      '没有置顶的对话和里面的消息会全部删除，删除后不能恢复；置顶的对话会保留。',
    icon: 'warning',
    title: '清空未置顶的对话？',
  });
  await deleteChatConversationMyByUnpinned();
  message.success($t('ui.actionMessage.operationSuccess'));
  // 清空对话、对话内容
  activeConversationId.value = null;
  // 获取对话列表
  await getChatConversationList();
  // 回调 方法
  emits('onConversationClear');
}

/** 对话置顶 */
async function handleTop(conversation: AiChatConversationApi.ChatConversation) {
  // 更新对话置顶
  conversation.pinned = !conversation.pinned;
  await updateChatConversationMy(conversation);
  // 刷新对话
  await getChatConversationList();
}

// ============ 角色仓库 ============

/** 角色仓库抽屉 */
const handleRoleRepository = async () => {
  drawerApi.open();
};

/** 监听选中的对话 */
const { activeId } = toRefs(props);
watch(activeId, async (newValue) => {
  activeConversationId.value = newValue;
});

defineExpose({ createConversation });

/** 初始化 */
onMounted(async () => {
  // 获取 对话列表
  await getChatConversationList();
  // 默认选中
  if (props.activeId) {
    activeConversationId.value = props.activeId;
  } else {
    // 首次默认选中第一个
    if (conversationList.value.length > 0 && conversationList.value[0]) {
      activeConversationId.value = conversationList.value[0].id;
      // 回调 onConversationClick
      emits('onConversationClick', conversationList.value[0]);
    }
  }
});
</script>

<template>
  <aside class="conv-panel">
    <Drawer />
    <!-- 顶部：新建与搜索 -->
    <div class="conv-panel__top">
      <Button block type="primary" @click="createConversation">
        <IconifyIcon icon="lucide:plus" class="size-4" />
        新建对话
      </Button>
      <Input
        v-model:value="searchName"
        aria-label="搜索对话"
        placeholder="搜索对话"
        @keyup="searchConversation"
      >
        <template #prefix>
          <IconifyIcon icon="lucide:search" class="conv-panel__search-icon" />
        </template>
      </Input>
    </div>

    <!-- 中间：按时间分组的对话列表 -->
    <div class="conv-panel__list">
      <div v-if="loading" class="conv-panel__loading">
        <Spin size="small" />
        <span>正在读取对话列表…</span>
      </div>
      <template v-else-if="listReady && visibleCount === 0">
        <EmptyState
          v-if="searchName.trim()"
          compact
          description="换个关键词再试。"
          kind="filtered"
          title="没有找到相关对话"
        />
        <EmptyState
          v-else
          compact
          description="点上方「新建对话」开始提问。"
          title="还没有对话"
        />
      </template>

      <template
        v-for="conversationKey in Object.keys(conversationMap)"
        :key="conversationKey"
      >
        <div
          v-if="conversationMap[conversationKey].length > 0"
          class="conv-group"
        >
          <p class="conv-group__title">{{ conversationKey }}</p>
          <div
            v-for="conversation in conversationMap[conversationKey]"
            :key="conversation.id"
            :class="{ 'is-active': conversation.id === activeConversationId }"
            class="conv-item"
            role="button"
            tabindex="0"
            @click="handleConversationClick(conversation.id)"
            @keydown.enter="handleConversationClick(conversation.id)"
          >
            <Avatar
              v-if="conversation.roleAvatar"
              :size="20"
              :src="conversation.roleAvatar"
              class="conv-item__avatar"
            />
            <SvgGptIcon v-else class="conv-item__avatar" />
            <span class="conv-item__title">
              {{ conversation.title || '未命名对话' }}
            </span>
            <IconifyIcon
              v-if="conversation.pinned"
              icon="lucide:pin"
              class="conv-item__pin"
            />
            <div class="conv-item__actions">
              <Tooltip :title="conversation.pinned ? '取消置顶' : '置顶'">
                <Button
                  :aria-label="conversation.pinned ? '取消置顶' : '置顶'"
                  size="small"
                  type="text"
                  @click.stop="handleTop(conversation)"
                >
                  <IconifyIcon
                    :icon="
                      conversation.pinned
                        ? 'lucide:arrow-down-from-line'
                        : 'lucide:arrow-up-to-line'
                    "
                    class="size-4"
                  />
                </Button>
              </Tooltip>
              <Tooltip title="重命名">
                <Button
                  aria-label="重命名"
                  size="small"
                  type="text"
                  @click.stop="updateConversationTitle(conversation)"
                >
                  <IconifyIcon icon="lucide:pencil" class="size-4" />
                </Button>
              </Tooltip>
              <Tooltip title="删除">
                <Button
                  aria-label="删除"
                  size="small"
                  type="text"
                  @click.stop="deleteChatConversation(conversation)"
                >
                  <IconifyIcon icon="lucide:trash-2" class="size-4" />
                </Button>
              </Tooltip>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 底部：角色仓库与清空 -->
    <div class="conv-panel__footer">
      <Button size="small" type="text" @click="handleRoleRepository">
        <IconifyIcon icon="lucide:users" class="size-4" />
        角色仓库
      </Button>
      <Button size="small" type="text" @click="handleClearConversation">
        <IconifyIcon icon="lucide:trash" class="size-4" />
        清空未置顶对话
      </Button>
    </div>
  </aside>
</template>

<style scoped>
/* 对话列表（V1.0.1.6 界面规范）：悬停用悬停底，选中用选中底与主色字，过渡 120ms */
.conv-panel {
  display: flex;
  flex-direction: column;
  width: 260px;
  height: 100%;
  min-height: 0;
}

.conv-panel__top {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: var(--nus-space-2);
  padding: var(--nus-space-3);
}

.conv-panel__top :deep(.ant-btn) {
  display: inline-flex;
  gap: var(--nus-space-1);
  align-items: center;
  justify-content: center;
}

.conv-panel__search-icon {
  width: 14px;
  height: 14px;
  color: var(--nus-text-tertiary);
}

.conv-panel__list {
  flex: 1;
  min-height: 0;
  padding: 0 var(--nus-space-2) var(--nus-space-2);
  overflow-y: auto;
}

.conv-panel__loading {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  justify-content: center;
  padding: var(--nus-space-6) 0;
  font-size: var(--nus-fs-dense);
  color: var(--nus-text-tertiary);
}

.conv-group + .conv-group {
  margin-top: var(--nus-space-2);
}

.conv-group__title {
  padding: var(--nus-space-2) var(--nus-space-2) var(--nus-space-1);
  margin: 0;
  font-size: var(--nus-fs-caption);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.conv-item {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  height: 36px;
  padding: 0 var(--nus-space-1) 0 var(--nus-space-2);
  font-size: var(--nus-fs-dense);
  color: var(--nus-text-secondary);
  cursor: pointer;
  outline: none;
  border-radius: var(--nus-radius);
  transition: var(--nus-transition-colors);
}

.conv-item:hover,
.conv-item:focus-visible {
  color: var(--nus-text);
  background: var(--nus-bg-hover);
}

.conv-item.is-active {
  font-weight: var(--nus-fw-medium);
  color: var(--nus-primary);
  background: var(--nus-bg-selected);
}

.conv-item__avatar {
  flex: none;
  width: 20px;
  height: 20px;
}

.conv-item__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-item__pin {
  flex: none;
  width: 12px;
  height: 12px;
  color: var(--nus-text-tertiary);
}

.conv-item__actions {
  display: none;
  flex: none;
  align-items: center;
}

.conv-item:hover .conv-item__actions,
.conv-item:focus-within .conv-item__actions {
  display: flex;
}

.conv-item:hover .conv-item__pin,
.conv-item:focus-within .conv-item__pin {
  display: none;
}

.conv-item__actions :deep(.ant-btn) {
  width: 24px;
  height: 24px;
  padding: 0;
  color: var(--nus-text-tertiary);
}

.conv-item__actions :deep(.ant-btn:hover) {
  color: var(--nus-text);
}

.conv-panel__footer {
  display: flex;
  flex: none;
  gap: var(--nus-space-1);
  align-items: center;
  justify-content: space-between;
  padding: var(--nus-space-2);
  border-top: 1px solid var(--nus-border-subtle);
}

.conv-panel__footer :deep(.ant-btn) {
  display: inline-flex;
  gap: var(--nus-space-1);
  align-items: center;
  color: var(--nus-text-secondary);
}
</style>
