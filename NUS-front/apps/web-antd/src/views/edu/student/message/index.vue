<script lang="ts" setup>
import type { EduMessageApi } from '#/api/edu/message';
import type { StudentConversation } from '#/api/edu/student/message';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { LockKeyhole } from '@vben/icons';

import { Button, Input, Spin } from 'ant-design-vue';

import {
  getStudentConversations,
  getStudentMessageDetail,
  sendStudentMessage,
} from '#/api/edu/student/message';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import { emitNotifyUnreadChanged } from '#/utils/notify-unread';
import {
  CONVERSATION_STATUS,
  getConversationStatusMeta,
  isConversationClosed,
} from '#/utils/status-message';
import { formatListTime, formatMessageTime } from '#/views/edu/message/format';
import { messageLabels } from '#/views/edu/message/labels';

import { findConversation } from './selection';

const route = useRoute();
const router = useRouter();
const conversations = ref<StudentConversation[]>([]);
const selected = ref<StudentConversation>();
const detail = ref<EduMessageApi.ConversationDetail>();
const content = ref('');
const loading = ref(false);
const detailLoading = ref(false);
const sending = ref(false);
const chatBody = ref<HTMLElement>();

const closed = computed(() => detail.value?.status === 1);

const statusMeta = computed(() =>
  getConversationStatusMeta(
    closed.value ? CONVERSATION_STATUS.CLOSED : CONVERSATION_STATUS.OPEN,
  ),
);

function isSelected(item: StudentConversation) {
  return (
    selected.value?.classId === item.classId &&
    selected.value?.courseId === item.courseId
  );
}

async function loadConversations() {
  loading.value = true;
  try {
    conversations.value = await getStudentConversations();
    const classId = Number(route.query.classId);
    const courseId = Number(route.query.courseId);
    const requested = findConversation(conversations.value, courseId, classId);
    const current = findConversation(
      conversations.value,
      selected.value?.courseId ?? 0,
      selected.value?.classId,
    );
    const target = requested ?? current;
    if (target) await selectConversation(target);
  } finally {
    loading.value = false;
  }
}

async function selectConversation(item: StudentConversation) {
  selected.value = item;
  detailLoading.value = true;
  try {
    detail.value = await getStudentMessageDetail({
      classId: item.classId,
      courseId: item.courseId,
    });
  } finally {
    detailLoading.value = false;
  }
  item.unreadCount = 0;
  // 打开留言时后端已把这门课的老师回复通知标为已读，让顶栏铃铛同步
  emitNotifyUnreadChanged();
  await nextTick();
  if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight;
}

async function send() {
  const text = content.value.trim();
  if (!selected.value || closed.value || !text || text.length > 1000) return;
  sending.value = true;
  try {
    await sendStudentMessage({
      classId: selected.value.classId,
      courseId: selected.value.courseId,
      content: text,
    });
    content.value = '';
    await selectConversation(selected.value);
    await loadConversations();
  } catch {
    // 失败原因已由全局请求提示给出；输入框里的内容保留，可以直接重试
  } finally {
    sending.value = false;
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    void send();
  }
}

onMounted(() => void loadConversations());
watch(
  () => route.fullPath,
  () => void loadConversations(),
);
</script>

<template>
  <Page
    :description="messageLabels.studentDescription"
    :title="messageLabels.myMessages"
    auto-content-height
  >
    <div class="msg-shell surface-panel">
      <!-- 左栏：课程会话 -->
      <aside :aria-label="messageLabels.conversationList" class="msg-list">
        <div class="msg-list__head">
          <span class="msg-list__title">{{
            messageLabels.conversationList
          }}</span>
          <span v-if="conversations.length > 0" class="msg-list__count">
            {{ conversations.length }}
          </span>
        </div>
        <div class="msg-list__body">
          <Spin :spinning="loading" :tip="messageLabels.loadingConversations">
            <div class="msg-list__content">
              <EmptyState
                v-if="conversations.length === 0 && !loading"
                :description="messageLabels.noConversationsHint"
                :title="messageLabels.noConversations"
                compact
              >
                <template #action>
                  <Button size="small" @click="router.push('/student/course')">
                    {{ messageLabels.viewCourses }}
                  </Button>
                </template>
              </EmptyState>
              <ul v-else class="msg-conv-list">
                <li
                  v-for="item in conversations"
                  :key="`${item.classId}-${item.courseId}`"
                >
                  <button
                    :aria-current="isSelected(item) ? 'true' : undefined"
                    :class="{
                      'is-selected': isSelected(item),
                      'is-unread': item.unreadCount > 0,
                    }"
                    class="msg-conv"
                    type="button"
                    @click="selectConversation(item)"
                  >
                    <span class="msg-conv__main">
                      <span class="msg-conv__line">
                        <span class="msg-conv__name">{{
                          item.courseName
                        }}</span>
                        <span class="msg-conv__time">
                          {{ formatListTime(item.lastMessageTime) }}
                        </span>
                      </span>
                      <span class="msg-conv__line">
                        <span class="msg-conv__sub">
                          {{ item.className }} ·
                          {{ item.teacherName || '教师' }}
                        </span>
                        <StatusTag
                          v-if="isConversationClosed(item.status)"
                          :label="getConversationStatusMeta(item.status).label"
                          :tone="getConversationStatusMeta(item.status).tone"
                        />
                      </span>
                      <span class="msg-conv__line">
                        <span class="msg-conv__preview">
                          {{ item.lastMessageContent || messageLabels.empty }}
                        </span>
                        <span
                          v-if="item.unreadCount > 0"
                          :aria-label="messageLabels.unread(item.unreadCount)"
                          class="msg-unread"
                        >
                          <span
                            aria-hidden="true"
                            class="msg-unread__dot"
                          ></span>
                          {{ item.unreadCount }}
                        </span>
                      </span>
                    </span>
                  </button>
                </li>
              </ul>
            </div>
          </Spin>
        </div>
      </aside>

      <!-- 右栏：对话 -->
      <section aria-label="对话" class="msg-chat">
        <template v-if="selected">
          <header class="msg-chat__head">
            <div class="msg-chat__heading">
              <div class="msg-chat__title">
                <span class="msg-chat__name">{{ selected.courseName }}</span>
                <StatusTag :label="statusMeta.label" :tone="statusMeta.tone" />
              </div>
              <div class="msg-chat__meta">
                {{ selected.className }} · {{ selected.teacherName || '教师' }}
              </div>
            </div>
          </header>

          <div ref="chatBody" class="msg-chat__body">
            <Spin
              :spinning="detailLoading"
              :tip="messageLabels.loadingMessages"
            >
              <div class="msg-chat__content">
                <EmptyState
                  v-if="!detail?.messages?.length && !detailLoading"
                  :description="messageLabels.emptyStudentHint"
                  :title="messageLabels.empty"
                  compact
                />
                <div v-else class="msg-thread">
                  <div
                    v-for="item in detail?.messages || []"
                    :key="item.id"
                    :class="{ 'is-own': item.type === 0 }"
                    class="msg-item"
                  >
                    <div class="msg-item__meta">
                      {{ item.senderName || (item.type === 0 ? '我' : '教师') }}
                      · {{ formatMessageTime(item.createTime) }}
                    </div>
                    <div class="msg-item__bubble">{{ item.content }}</div>
                  </div>
                </div>
              </div>
            </Spin>
          </div>

          <footer class="msg-composer">
            <div v-if="closed" class="msg-composer__closed">
              <LockKeyhole aria-hidden="true" class="msg-composer__icon" />
              <span>{{ messageLabels.closedStudentHint }}</span>
            </div>
            <div v-else class="msg-composer__row">
              <Input.TextArea
                v-model:value="content"
                :auto-size="{ minRows: 2, maxRows: 6 }"
                :disabled="sending"
                :maxlength="1000"
                :placeholder="messageLabels.placeholder"
                @keydown="handleKeydown"
              />
              <Button
                v-access:code="['edu:student-message:send']"
                :disabled="!content.trim()"
                :loading="sending"
                type="primary"
                @click="send"
              >
                {{ messageLabels.send }}
              </Button>
            </div>
          </footer>
        </template>
        <EmptyState
          v-else
          :description="messageLabels.noSelectionStudentHint"
          :title="messageLabels.noSelection"
          class="msg-chat__placeholder"
        />
      </section>
    </div>
  </Page>
</template>

<style scoped src="../../message/message-shell.css"></style>
