<script lang="ts" setup>
import type { AiChatConversationApi } from '#/api/ai/chat/conversation';
import type { AiChatMessageApi } from '#/api/ai/chat/message';

import { computed, nextTick, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { confirm, Page, useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Button, message, Switch, Tooltip } from 'ant-design-vue';

import { getChatConversationMy } from '#/api/ai/chat/conversation';
import {
  deleteByConversationId,
  getChatMessageListByConversationId,
  sendChatMessageStream,
} from '#/api/ai/chat/message';

import ConversationList from './modules/conversation/list.vue';
import ConversationUpdateForm from './modules/conversation/update-form.vue';
import MessageFileUpload from './modules/message/file-upload.vue';
import MessageListEmpty from './modules/message/list-empty.vue';
import MessageList from './modules/message/list.vue';
import MessageLoading from './modules/message/loading.vue';
import MessageNewConversation from './modules/message/new-conversation.vue';

/** AI 聊天对话 列表 */
defineOptions({ name: 'AiChat' });

const route = useRoute();
const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: ConversationUpdateForm,
  destroyOnClose: true,
});

// 聊天对话
const conversationListRef = ref();
const activeConversationId = ref<null | number>(null); // 选中的对话编号
const activeConversation = ref<AiChatConversationApi.ChatConversation | null>(
  null,
); // 选中的 Conversation
const conversationInProgress = ref(false); // 对话是否正在进行中。目前只有【发送】消息时，会更新为 true，避免切换对话、删除对话等操作

// 消息列表
const messageRef = ref();
const activeMessageList = ref<AiChatMessageApi.ChatMessage[]>([]); // 选中对话的消息列表
const activeMessageListLoading = ref<boolean>(false); // activeMessageList 是否正在加载中
const activeMessageListLoadingTimer = ref<any>(); // activeMessageListLoading Timer 定时器。如果加载速度很快，就不进入加载中
// 消息滚动
const textSpeed = ref<number>(50); // Typing speed in milliseconds
const textRoleRunning = ref<boolean>(false); // Typing speed in milliseconds

// 发送消息输入框
const isComposing = ref(false); // 判断用户是否在输入
const conversationInAbortController = ref<any>(); // 对话进行中 abort 控制器(控制 stream 对话)
const inputTimeout = ref<any>(); // 处理输入中回车的定时器
const prompt = ref<string>(); // prompt
const enableContext = ref<boolean>(true); // 是否开启上下文
const uploadFiles = ref<string[]>([]); // 上传的文件 URL 列表
// 接收 Stream 消息
const receiveMessageFullText = ref('');
const receiveMessageDisplayedText = ref('');

/**
 * 发送失败（V1.0.1.6 界面规范：出错按“什么没成功—原因—下一步”写，保留用户的内容）：
 * 记下这次发送的内容与原因，在输入框上方提示，可以原样重试。
 */
interface SendFailure {
  attachmentUrls: string[];
  content: string;
  conversationId: number;
  reason: string;
}
const sendFailure = ref<null | SendFailure>(null);

/** 记录发送失败；还没收到任何回复时，去掉页面上“正在思考”的占位消息 */
function markSendFailed(
  userMessage: AiChatMessageApi.ChatMessage,
  reason: string,
) {
  if (receiveMessageFullText.value === '') {
    const lastMessage =
      activeMessageList.value[activeMessageList.value.length - 1];
    if (lastMessage && lastMessage.id === -2) {
      activeMessageList.value.pop();
    }
  }
  sendFailure.value = {
    attachmentUrls: userMessage.attachmentUrls || [],
    content: userMessage.content,
    conversationId: userMessage.conversationId,
    reason,
  };
}

/** 重试：去掉上次留在页面上的本地占位消息，原样再发一次（输入框里新写的内容不受影响） */
async function handleRetrySend() {
  const failure = sendFailure.value;
  if (!failure || conversationInProgress.value) {
    return;
  }
  if (failure.conversationId !== activeConversationId.value) {
    sendFailure.value = null;
    return;
  }
  sendFailure.value = null;
  activeMessageList.value = activeMessageList.value.filter(
    (item) => item.id > 0,
  );
  await doSendMessageStream({
    attachmentUrls: failure.attachmentUrls,
    content: failure.content,
    conversationId: failure.conversationId,
  } as AiChatMessageApi.ChatMessage);
}

// =========== 【聊天对话】相关 ===========

/** 获取对话信息 */
async function getConversation(id: null | number) {
  if (!id) {
    return;
  }
  const conversation: AiChatConversationApi.ChatConversation =
    await getChatConversationMy(id);
  if (!conversation) {
    return;
  }
  activeConversation.value = conversation;
  activeConversationId.value = conversation.id;
}

/**
 * 点击某个对话
 *
 * @param conversation 选中的对话
 * @return 是否切换成功
 */
async function handleConversationClick(
  conversation: AiChatConversationApi.ChatConversation,
) {
  // 对话进行中，不允许切换
  if (conversationInProgress.value) {
    message.warning('AI 正在回复，回复结束或停止后再切换对话');
    return false;
  }

  // 切换对话后，上一个对话的发送失败提示不再显示
  sendFailure.value = null;
  // 更新选中的对话 id
  activeConversationId.value = conversation.id;
  activeConversation.value = conversation;
  // 刷新 message 列表
  await getMessageList();
  // 滚动底部
  await scrollToBottom(true);
  prompt.value = '';
  // 清空输入框
  prompt.value = '';
  // 清空文件列表
  uploadFiles.value = [];
  return true;
}

/** 删除某个对话*/
async function handlerConversationDelete(
  delConversation: AiChatConversationApi.ChatConversation,
) {
  // 删除的对话如果是当前选中的，那么就重置
  if (activeConversationId.value === delConversation.id) {
    await handleConversationClear();
  }
}

/** 清空选中的对话 */
async function handleConversationClear() {
  // 对话进行中，不允许切换
  if (conversationInProgress.value) {
    message.warning('AI 正在回复，回复结束或停止后再切换对话');
    return false;
  }
  sendFailure.value = null;
  activeConversationId.value = null;
  activeConversation.value = null;
  activeMessageList.value = [];
  // 清空输入框和文件列表
  prompt.value = '';
  uploadFiles.value = [];
}

async function openChatConversationUpdateForm() {
  formModalApi.setData({ id: activeConversationId.value }).open();
}

/** 对话更新成功，刷新最新信息 */
async function handleConversationUpdateSuccess() {
  await getConversation(activeConversationId.value);
}

/** 处理聊天对话的创建成功 */
async function handleConversationCreate() {
  // 创建对话
  await conversationListRef.value.createConversation();
}

/** 处理聊天对话的创建成功 */
async function handleConversationCreateSuccess() {
  // 创建新的对话，清空输入框
  prompt.value = '';
  // 清空文件列表
  uploadFiles.value = [];
}

// =========== 【消息列表】相关 ===========

/** 获取消息 message 列表 */
async function getMessageList() {
  try {
    if (activeConversationId.value === null) {
      return;
    }
    // Timer 定时器，如果加载速度很快，就不进入加载中
    activeMessageListLoadingTimer.value = setTimeout(() => {
      activeMessageListLoading.value = true;
    }, 60);

    // 获取消息列表
    activeMessageList.value = await getChatMessageListByConversationId(
      activeConversationId.value,
    );

    // 滚动到最下面
    await nextTick();
    await scrollToBottom();
  } finally {
    // time 定时器，如果加载速度很快，就不进入加载中
    if (activeMessageListLoadingTimer.value) {
      clearTimeout(activeMessageListLoadingTimer.value);
    }
    // 加载结束
    activeMessageListLoading.value = false;
  }
}

/**
 * 消息列表
 *
 * 和 {@link #getMessageList()} 的差异是，把 systemMessage 考虑进去
 */
const messageList = computed(() => {
  if (activeMessageList.value.length > 0) {
    return activeMessageList.value;
  }
  // 没有消息时，如果有 systemMessage 则展示它
  if (activeConversation.value?.systemMessage) {
    return [
      {
        id: 0,
        type: 'system',
        content: activeConversation.value.systemMessage,
      },
    ];
  }
  return [];
});

/** 处理删除 message 消息 */
function handleMessageDelete() {
  if (conversationInProgress.value) {
    message.warning('AI 正在回复，回复结束后再删除消息');
    return;
  }
  // 刷新 message 列表
  getMessageList();
}

/** 处理 message 清空 */
async function handlerMessageClear() {
  if (!activeConversationId.value) {
    return;
  }
  try {
    // 确认提示
    await confirm({
      cancelText: '返回',
      confirmText: '清空',
      content:
        '这个对话里的消息会全部删除，删除后不能恢复；对话本身和设定会保留。',
      icon: 'warning',
      title: `清空「${activeConversation.value?.title || '当前对话'}」的消息？`,
    });
    // 清空对话
    await deleteByConversationId(activeConversationId.value);
    // 刷新 message 列表
    activeMessageList.value = [];
    sendFailure.value = null;
  } catch {}
}

/** 回到 message 列表的顶部 */
function handleGoTopMessage() {
  messageRef.value.handlerGoTop();
}

// =========== 【发送消息】相关 ===========

/** 处理来自 keydown 的发送消息 */
async function handleSendByKeydown(event: any) {
  // 判断用户是否在输入
  if (isComposing.value) {
    return;
  }
  // 进行中不允许发送
  if (conversationInProgress.value) {
    return;
  }
  const content = prompt.value?.trim() as string;
  if (event.key === 'Enter') {
    if (event.shiftKey) {
      // 插入换行
      prompt.value += '\r\n';
      event.preventDefault(); // 防止默认的换行行为
    } else {
      // 发送消息
      await doSendMessage(content);
      event.preventDefault(); // 防止默认的提交行为
    }
  }
}

/** 处理来自【发送】按钮的发送消息 */
function handleSendByButton() {
  doSendMessage(prompt.value?.trim() as string);
}

/** 处理 prompt 输入变化 */
function handlePromptInput(event: any) {
  // 非输入法 输入设置为 true
  if (!isComposing.value) {
    // 回车 event data 是 null
    if (event.data === null || event.data === 'null') {
      return;
    }
    isComposing.value = true;
  }
  // 清理定时器
  if (inputTimeout.value) {
    clearTimeout(inputTimeout.value);
  }
  // 重置定时器
  inputTimeout.value = setTimeout(() => {
    isComposing.value = false;
  }, 400);
}

function onCompositionstart() {
  isComposing.value = true;
}

function onCompositionend() {
  setTimeout(() => {
    isComposing.value = false;
  }, 200);
}

/** 真正执行【发送】消息操作 */
async function doSendMessage(content: string) {
  // 校验
  if (content.length === 0) {
    message.warning('先输入要发送的内容');
    return;
  }
  if (activeConversationId.value === null) {
    message.warning('先新建一个对话，再发送消息');
    return;
  }

  // 准备附件 URL 数组
  const attachmentUrls = [...uploadFiles.value];

  // 清空输入框和文件列表
  prompt.value = '';
  uploadFiles.value = [];

  // 执行发送
  await doSendMessageStream({
    conversationId: activeConversationId.value,
    content,
    attachmentUrls,
  } as AiChatMessageApi.ChatMessage);
}

/** 真正执行【发送】消息操作 */
async function doSendMessageStream(userMessage: AiChatMessageApi.ChatMessage) {
  // 创建 AbortController 实例，以便中止请求
  conversationInAbortController.value = new AbortController();
  // 新的一次发送开始，清掉上一次的失败提示
  sendFailure.value = null;
  // 标记对话进行中
  conversationInProgress.value = true;
  // 设置为空
  receiveMessageFullText.value = '';

  try {
    // 1.1 先添加两个假数据，等 stream 返回再替换
    activeMessageList.value.push(
      {
        id: -1,
        conversationId: activeConversationId.value,
        type: 'user',
        content: userMessage.content,
        attachmentUrls: userMessage.attachmentUrls || [],
        createTime: new Date(),
      } as AiChatMessageApi.ChatMessage,
      {
        id: -2,
        conversationId: activeConversationId.value,
        type: 'assistant',
        content: '正在思考…',
        reasoningContent: '',
        createTime: new Date(),
      } as AiChatMessageApi.ChatMessage,
    );
    // 1.2 滚动到最下面
    await nextTick();
    await scrollToBottom(); // 底部
    // 1.3 开始滚动
    textRoll().then();

    // 2. 发送 event stream
    let isFirstChunk = true; // 是否是第一个 chunk 消息段
    await sendChatMessageStream(
      userMessage.conversationId,
      userMessage.content,
      conversationInAbortController.value,
      enableContext.value,
      async (res: any) => {
        const { code, data, msg } = JSON.parse(res.data);
        if (code !== 0) {
          // 如果未接收到消息，则去掉占位消息；失败原因显示在输入框上方，可以重试
          markSendFailed(userMessage, msg || 'AI 服务返回了错误');
          return;
        }

        // 如果内容和推理内容都为空，就不处理
        if (data.receive.content === '' && !data.receive.reasoningContent) {
          return;
        }

        // 首次返回需要添加一个 message 到页面，后面的都是更新
        if (isFirstChunk) {
          isFirstChunk = false;
          // 弹出两个假数据
          activeMessageList.value.pop();
          activeMessageList.value.pop();
          // 更新返回的数据
          activeMessageList.value.push(data.send, data.receive);
          data.send.attachmentUrls = userMessage.attachmentUrls;
        }

        // 处理 reasoningContent
        if (data.receive.reasoningContent) {
          const lastMessage =
            activeMessageList.value[activeMessageList.value.length - 1];
          // 累加推理内容
          lastMessage!.reasoningContent =
            (lastMessage!.reasoningContent || '') +
            data.receive.reasoningContent;
        }

        // 处理正常内容
        if (data.receive.content !== '') {
          receiveMessageFullText.value =
            receiveMessageFullText.value + data.receive.content;
        }

        // 滚动到最下面
        await scrollToBottom();
      },
      (error: any) => {
        // 异常提示，并停止流
        markSendFailed(
          userMessage,
          '没有连上 AI 服务，可能是网络中断或模型还没有配置好',
        );
        stopStream();
        // 需要抛出异常，禁止重试
        throw error;
      },
      () => {
        stopStream();
      },
      userMessage.attachmentUrls,
    );
  } catch {}
}

/** 停止 stream 流式调用 */
async function stopStream() {
  // tip：如果 stream 进行中的 message，就需要调用 controller 结束
  if (conversationInAbortController.value) {
    conversationInAbortController.value.abort();
  }
  // 设置为 false
  conversationInProgress.value = false;
}

/** 编辑 message：设置为 prompt，可以再次编辑 */
function handleMessageEdit(message: AiChatMessageApi.ChatMessage) {
  prompt.value = message.content;
}

/** 刷新 message：基于指定消息，再次发起对话 */
function handleMessageRefresh(message: AiChatMessageApi.ChatMessage) {
  doSendMessage(message.content);
}

// ============== 【消息滚动】相关 =============

/** 滚动到 message 底部 */
async function scrollToBottom(isIgnore?: boolean) {
  await nextTick();
  if (messageRef.value) {
    messageRef.value.scrollToBottom(isIgnore);
  }
}

/** 自提滚动效果 */
async function textRoll() {
  let index = 0;
  try {
    // 只能执行一次
    if (textRoleRunning.value) {
      return;
    }
    // 设置状态
    textRoleRunning.value = true;
    receiveMessageDisplayedText.value = '';
    async function task() {
      // 调整速度
      const diff =
        (receiveMessageFullText.value.length -
          receiveMessageDisplayedText.value.length) /
        10;
      if (diff > 5) {
        textSpeed.value = 10;
      } else if (diff > 2) {
        textSpeed.value = 30;
      } else if (diff > 1.5) {
        textSpeed.value = 50;
      } else {
        textSpeed.value = 100;
      }
      // 对话结束，就按 30 的速度
      if (!conversationInProgress.value) {
        textSpeed.value = 10;
      }

      if (index < receiveMessageFullText.value.length) {
        receiveMessageDisplayedText.value +=
          receiveMessageFullText.value[index];
        index++;

        // 更新 message
        const lastMessage =
          activeMessageList.value[activeMessageList.value.length - 1];
        if (lastMessage)
          lastMessage.content = receiveMessageDisplayedText.value;
        // 滚动到住下面
        await scrollToBottom();
        // 重新设置任务
        timer = setTimeout(task, textSpeed.value);
      } else {
        // 不是对话中可以结束
        if (conversationInProgress.value) {
          // 重新设置任务
          timer = setTimeout(task, textSpeed.value);
        } else {
          textRoleRunning.value = false;
          clearTimeout(timer);
        }
      }
    }
    let timer = setTimeout(task, textSpeed.value);
  } catch {}
}

/** 初始化 */
onMounted(async () => {
  // 如果有 conversationId 参数，则默认选中
  if (route.query.conversationId) {
    const id = route.query.conversationId as unknown as number;
    activeConversationId.value = id;
    await getConversation(id);
  }

  // 获取列表数据
  activeMessageListLoading.value = true;
  await getMessageList();
});
</script>

<template>
  <Page
    auto-content-height
    description="围绕教学设计、课堂表达与教学反思提问，对话会保存在左侧列表里。"
    title="AI 对话"
  >
    <div class="ai-chat">
      <!-- 左侧：对话列表 -->
      <ConversationList
        ref="conversationListRef"
        class="ai-chat__sidebar"
        :active-id="activeConversationId"
        @on-conversation-create="handleConversationCreateSuccess"
        @on-conversation-click="handleConversationClick"
        @on-conversation-clear="handleConversationClear"
        @on-conversation-delete="handlerConversationDelete"
      />

      <!-- 右侧：消息与输入 -->
      <section class="ai-chat__main">
        <header class="ai-chat__header">
          <div class="ai-chat__title">
            <h2 class="ai-chat__title-text">
              {{ activeConversation?.title || '对话' }}
            </h2>
            <span v-if="activeMessageList.length > 0" class="ai-chat__count">
              {{ activeMessageList.length }} 条消息
            </span>
          </div>

          <div v-if="activeConversation" class="ai-chat__tools">
            <Tooltip title="对话设定：模型、角色设定与回复参数">
              <Button size="small" @click="openChatConversationUpdateForm">
                <IconifyIcon icon="lucide:settings-2" class="size-4" />
                <span class="ai-chat__model">
                  {{ activeConversation?.modelName || '选择模型' }}
                </span>
              </Button>
            </Tooltip>
            <Tooltip title="清空消息">
              <Button
                aria-label="清空消息"
                size="small"
                type="text"
                @click="handlerMessageClear"
              >
                <IconifyIcon icon="lucide:trash-2" class="size-4" />
              </Button>
            </Tooltip>
            <Tooltip title="回到顶部">
              <Button
                aria-label="回到顶部"
                size="small"
                type="text"
                @click="handleGoTopMessage"
              >
                <IconifyIcon icon="lucide:arrow-up-to-line" class="size-4" />
              </Button>
            </Tooltip>
          </div>
        </header>

        <div class="ai-chat__body">
          <MessageLoading v-if="activeMessageListLoading" />
          <MessageNewConversation
            v-if="!activeConversation"
            @on-new-conversation="handleConversationCreate"
          />
          <MessageListEmpty
            v-if="
              !activeMessageListLoading &&
              messageList.length === 0 &&
              activeConversation
            "
            @on-prompt="doSendMessage"
          />
          <MessageList
            v-if="!activeMessageListLoading && messageList.length > 0"
            ref="messageRef"
            :conversation="activeConversation as any"
            :list="messageList as any"
            @on-delete-success="handleMessageDelete"
            @on-edit="handleMessageEdit"
            @on-refresh="handleMessageRefresh"
          />
        </div>

        <footer class="ai-chat__footer">
          <!-- 发送失败：什么没成功、原因、下一步 -->
          <div v-if="sendFailure" class="ai-chat__failure" role="alert">
            <IconifyIcon
              icon="lucide:circle-alert"
              class="ai-chat__failure-icon"
            />
            <div class="ai-chat__failure-text">
              <p class="ai-chat__failure-title">回复没有生成</p>
              <p class="ai-chat__failure-desc">
                {{
                  sendFailure.reason
                }}。可以点「重试」再发一次；仍然失败请联系管理员检查模型配置。
              </p>
            </div>
            <div class="ai-chat__failure-actions">
              <Button
                size="small"
                :disabled="conversationInProgress"
                @click="handleRetrySend"
              >
                重试
              </Button>
              <Button
                aria-label="关闭提示"
                size="small"
                type="text"
                @click="sendFailure = null"
              >
                <IconifyIcon icon="lucide:x" class="size-4" />
              </Button>
            </div>
          </div>

          <form class="ai-chat__composer" @submit.prevent>
            <textarea
              v-model="prompt"
              aria-label="输入问题"
              class="ai-chat__input"
              placeholder="输入问题，按 Enter 发送，Shift + Enter 换行"
              @keydown="handleSendByKeydown"
              @input="handlePromptInput"
              @compositionstart="onCompositionstart"
              @compositionend="onCompositionend"
            ></textarea>
            <div class="ai-chat__composer-bar">
              <div class="ai-chat__composer-tools">
                <MessageFileUpload
                  v-model="uploadFiles"
                  :disabled="conversationInProgress"
                />
                <Tooltip title="开启后，AI 会参考这个对话里前面的消息">
                  <label class="ai-chat__context">
                    <Switch v-model:checked="enableContext" size="small" />
                    <span>上下文</span>
                  </label>
                </Tooltip>
              </div>
              <Button
                v-if="conversationInProgress === false"
                type="primary"
                :loading="conversationInProgress"
                @click="handleSendByButton"
              >
                <IconifyIcon icon="lucide:send-horizontal" class="size-4" />
                发送
              </Button>
              <Button
                v-if="conversationInProgress === true"
                @click="stopStream()"
              >
                <IconifyIcon icon="lucide:circle-stop" class="size-4" />
                停止
              </Button>
            </div>
          </form>
        </footer>
      </section>
    </div>
    <FormModal @success="handleConversationUpdateSuccess" />
  </Page>
</template>

<style scoped>
/* AI 对话（V1.0.1.6 界面规范）：左侧对话列表与右侧消息区放在同一块面板里，用分隔线分开 */
.ai-chat {
  display: flex;
  height: 100%;
  min-height: 480px;
  overflow: hidden;
  background: var(--nus-bg-surface);
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius-lg);
}

.ai-chat__sidebar {
  flex: none;
  border-right: 1px solid var(--nus-border-subtle);
}

.ai-chat__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.ai-chat__header {
  display: flex;
  flex: none;
  gap: var(--nus-space-3);
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 var(--nus-space-4);
  border-bottom: 1px solid var(--nus-border-subtle);
}

.ai-chat__title {
  display: flex;
  gap: var(--nus-space-2);
  align-items: baseline;
  min-width: 0;
}

.ai-chat__title-text {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
  white-space: nowrap;
}

.ai-chat__count {
  flex: none;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.ai-chat__tools {
  display: flex;
  flex: none;
  gap: var(--nus-space-1);
  align-items: center;
}

.ai-chat__tools :deep(.ant-btn) {
  display: inline-flex;
  gap: var(--nus-space-1);
  align-items: center;
}

.ai-chat__tools :deep(.ant-btn-text) {
  color: var(--nus-text-secondary);
}

.ai-chat__model {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-chat__body {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.ai-chat__footer {
  flex: none;
  padding: var(--nus-space-3) var(--nus-space-4) var(--nus-space-4);
}

.ai-chat__failure {
  display: flex;
  gap: var(--nus-space-3);
  align-items: flex-start;
  padding: var(--nus-space-3);
  margin-bottom: var(--nus-space-3);
  background: var(--nus-danger-tint);
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius);
}

.ai-chat__failure-icon {
  flex: none;
  width: 16px;
  height: 16px;
  margin-top: 3px;
  color: var(--nus-danger);
}

.ai-chat__failure-text {
  flex: 1;
  min-width: 0;
}

.ai-chat__failure-title {
  margin: 0;
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.ai-chat__failure-desc {
  margin: 2px 0 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.ai-chat__failure-actions {
  display: flex;
  flex: none;
  gap: var(--nus-space-1);
  align-items: center;
}

.ai-chat__composer {
  display: flex;
  flex-direction: column;
  padding: var(--nus-space-2) var(--nus-space-3);
  background: var(--nus-bg-surface);
  border: 1px solid var(--nus-border);
  border-radius: var(--nus-radius-lg);
  transition: var(--nus-transition-colors);
}

.ai-chat__composer:focus-within {
  border-color: var(--nus-primary);
}

.ai-chat__input {
  box-sizing: border-box;
  height: 72px;
  padding: var(--nus-space-1) 0;
  overflow: auto;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  resize: none;
  outline: none;
  background: transparent;
  border: 0;
}

.ai-chat__input::placeholder {
  color: var(--nus-text-tertiary);
}

.ai-chat__composer-bar {
  display: flex;
  gap: var(--nus-space-3);
  align-items: center;
  justify-content: space-between;
  padding-top: var(--nus-space-2);
}

.ai-chat__composer-bar :deep(.ant-btn) {
  display: inline-flex;
  gap: var(--nus-space-1);
  align-items: center;
}

.ai-chat__composer-tools {
  display: flex;
  gap: var(--nus-space-3);
  align-items: center;
}

.ai-chat__context {
  display: inline-flex;
  gap: var(--nus-space-2);
  align-items: center;
  font-size: var(--nus-fs-dense);
  color: var(--nus-text-secondary);
  cursor: pointer;
}
</style>
