<script setup lang="ts">
import type { PropType } from 'vue';

import type { AiChatConversationApi } from '#/api/ai/chat/conversation';
import type { AiChatMessageApi } from '#/api/ai/chat/message';

import { computed, nextTick, onMounted, ref, toRefs } from 'vue';

import { IconifyIcon, SvgGptIcon } from '@vben/icons';
import { preferences } from '@vben/preferences';
import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import { useClipboard } from '@vueuse/core';
import { Avatar, Button, message, Tooltip } from 'ant-design-vue';

import { deleteChatMessage } from '#/api/ai/chat/message';
import { MarkdownView } from '#/components/markdown-view';

import MessageFiles from './files.vue';
import MessageReasoning from './reasoning.vue';

const props = defineProps({
  conversation: {
    type: Object as PropType<AiChatConversationApi.ChatConversation>,
    required: true,
  },
  list: {
    type: Array as PropType<AiChatMessageApi.ChatMessage[]>,
    required: true,
  },
});

const emits = defineEmits(['onDeleteSuccess', 'onRefresh', 'onEdit']);
const { copy } = useClipboard(); // 初始化 copy 到粘贴板
const userStore = useUserStore();

// 判断“消息列表”滚动的位置(用于判断是否需要滚动到消息最下方)
const messageContainer: any = ref(null);
const isScrolling = ref(false); // 用于判断用户是否在滚动

const userAvatar = computed(
  () => userStore.userInfo?.avatar || preferences.app.defaultAvatar,
);

const { list } = toRefs(props); // 定义 emits

// ============ 处理对话滚动 ==============

/** 滚动到底部 */
async function scrollToBottom(isIgnore?: boolean) {
  // 注意要使用 nextTick 以免获取不到 dom
  await nextTick();
  if (isIgnore || !isScrolling.value) {
    messageContainer.value.scrollTop =
      messageContainer.value.scrollHeight - messageContainer.value.offsetHeight;
  }
}

function handleScroll() {
  const scrollContainer = messageContainer.value;
  const scrollTop = scrollContainer.scrollTop;
  const scrollHeight = scrollContainer.scrollHeight;
  const offsetHeight = scrollContainer.offsetHeight;
  isScrolling.value = scrollTop + offsetHeight < scrollHeight - 100;
}

/** 回到底部 */
async function handleGoBottom() {
  const scrollContainer = messageContainer.value;
  scrollContainer.scrollTop = scrollContainer.scrollHeight;
}

/** 回到顶部 */
async function handlerGoTop() {
  const scrollContainer = messageContainer.value;
  scrollContainer.scrollTop = 0;
}

defineExpose({ scrollToBottom, handlerGoTop }); // 提供方法给 parent 调用

// ============ 处理消息操作 ==============

/** 复制 */
async function copyContent(content: string) {
  await copy(content);
  message.success('已复制');
}

/** 删除 */
async function handleDelete(id: number) {
  // 删除 message
  await deleteChatMessage(id);
  message.success('已删除这条消息');
  // 回调
  emits('onDeleteSuccess');
}

/** 刷新 */
async function handleRefresh(message: AiChatMessageApi.ChatMessage) {
  emits('onRefresh', message);
}

/** 编辑 */
async function handleEdit(message: AiChatMessageApi.ChatMessage) {
  emits('onEdit', message);
}

/** 初始化 */
onMounted(async () => {
  messageContainer.value.addEventListener('scroll', handleScroll);
});
</script>
<template>
  <div ref="messageContainer" class="msg-list">
    <div v-for="(item, index) in list" :key="index" class="msg-list__item">
      <!-- 左侧消息：system、assistant -->
      <div v-if="item.type !== 'user'" class="msg msg--assistant">
        <div class="msg__avatar">
          <Avatar
            v-if="conversation.roleAvatar"
            :src="conversation.roleAvatar"
            :size="28"
          />
          <SvgGptIcon v-else class="size-7" />
        </div>
        <div class="msg__main">
          <div class="msg__meta">
            {{ formatDateTime(item.createTime) }}
          </div>
          <div class="msg__bubble msg__bubble--assistant">
            <MessageReasoning
              :reasoning-content="item.reasoningContent || ''"
              :content="item.content || ''"
            />
            <MarkdownView :content="item.content" />
            <MessageFiles :attachment-urls="item.attachmentUrls" />
          </div>
          <div class="msg__actions">
            <Tooltip title="复制">
              <Button
                aria-label="复制"
                size="small"
                type="text"
                @click="copyContent(item.content)"
              >
                <IconifyIcon icon="lucide:copy" class="size-4" />
              </Button>
            </Tooltip>
            <Tooltip v-if="item.id > 0" title="删除">
              <Button
                aria-label="删除"
                size="small"
                type="text"
                @click="handleDelete(item.id)"
              >
                <IconifyIcon icon="lucide:trash-2" class="size-4" />
              </Button>
            </Tooltip>
          </div>
        </div>
      </div>

      <!-- 右侧消息：user -->
      <div v-else class="msg msg--user">
        <div class="msg__avatar">
          <Avatar :src="userAvatar" :size="28" />
        </div>
        <div class="msg__main msg__main--user">
          <div class="msg__meta">
            {{ formatDateTime(item.createTime) }}
          </div>
          <div
            v-if="item.attachmentUrls && item.attachmentUrls.length > 0"
            class="msg__files"
          >
            <MessageFiles :attachment-urls="item.attachmentUrls" />
          </div>
          <div
            v-if="item.content && item.content.trim()"
            class="msg__bubble msg__bubble--user"
          >
            {{ item.content }}
          </div>
          <div class="msg__actions">
            <Tooltip title="复制">
              <Button
                aria-label="复制"
                size="small"
                type="text"
                @click="copyContent(item.content)"
              >
                <IconifyIcon icon="lucide:copy" class="size-4" />
              </Button>
            </Tooltip>
            <Tooltip title="删除">
              <Button
                aria-label="删除"
                size="small"
                type="text"
                @click="handleDelete(item.id)"
              >
                <IconifyIcon icon="lucide:trash-2" class="size-4" />
              </Button>
            </Tooltip>
            <Tooltip title="重新发送">
              <Button
                aria-label="重新发送"
                size="small"
                type="text"
                @click="handleRefresh(item)"
              >
                <IconifyIcon icon="lucide:refresh-cw" class="size-4" />
              </Button>
            </Tooltip>
            <Tooltip title="编辑后重发">
              <Button
                aria-label="编辑后重发"
                size="small"
                type="text"
                @click="handleEdit(item)"
              >
                <IconifyIcon icon="lucide:pencil" class="size-4" />
              </Button>
            </Tooltip>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 回到底部按钮 -->
  <div v-if="isScrolling" class="msg-list__to-bottom">
    <Button size="small" @click="handleGoBottom">
      <IconifyIcon icon="lucide:chevron-down" class="size-4" />
      回到底部
    </Button>
  </div>
</template>

<style scoped>
/* 消息区（V1.0.1.6 界面规范）：AI 的回复用凹陷底，自己的消息用选中底，不用饱和色；正文 14，时间 12 辅助色 */
.msg-list {
  position: relative;
  height: 100%;
  padding: var(--nus-space-6) var(--nus-space-4);
  overflow-y: auto;
}

.msg-list__item {
  max-width: 880px;
  margin: 0 auto;
}

.msg-list__item + .msg-list__item {
  margin-top: var(--nus-space-6);
}

.msg {
  display: flex;
  gap: var(--nus-space-3);
  align-items: flex-start;
}

.msg--user {
  flex-direction: row-reverse;
}

.msg__avatar {
  flex: none;
  padding-top: 22px;
}

.msg__main {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  max-width: calc(100% - 40px);
}

.msg__main--user {
  align-items: flex-end;
}

.msg__meta {
  margin-bottom: var(--nus-space-1);
  font-size: var(--nus-fs-caption);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.msg__bubble {
  max-width: 100%;
  padding: var(--nus-space-3) var(--nus-space-4);
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  overflow-wrap: anywhere;
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius-lg);
}

.msg__bubble--assistant {
  min-width: 0;
  background: var(--nus-bg-sunken);
}

.msg__bubble--user {
  white-space: pre-wrap;
  background: var(--nus-bg-selected);
  border-color: var(--nus-bg-selected);
}

.msg__files {
  margin-bottom: var(--nus-space-2);
}

.msg__actions {
  display: flex;
  gap: 2px;
  margin-top: var(--nus-space-1);
}

.msg__actions :deep(.ant-btn) {
  width: 24px;
  height: 24px;
  padding: 0;
  color: var(--nus-text-tertiary);
}

.msg__actions :deep(.ant-btn:hover) {
  color: var(--nus-text);
}

.msg-list__to-bottom {
  position: absolute;
  bottom: var(--nus-space-3);
  left: 50%;
  z-index: 10;
  transform: translateX(-50%);
}

.msg-list__to-bottom :deep(.ant-btn) {
  display: inline-flex;
  gap: var(--nus-space-1);
  align-items: center;
}
</style>
