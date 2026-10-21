<script lang="ts" setup>
import type { EduMessageApi } from '#/api/edu/message';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { confirm, Page } from '@vben/common-ui';
import { LockKeyhole } from '@vben/icons';

import { Button, Input, message, Select, Spin } from 'ant-design-vue';

import { getSimpleClassList } from '#/api/edu/class';
import { getClassCourseListByClass } from '#/api/edu/class-course';
import {
  closeConversation,
  getMessageDetail,
  getMessageStudentList,
  reopenConversation,
  replyMessage,
} from '#/api/edu/message';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import { emitNotifyUnreadChanged } from '#/utils/notify-unread';
import {
  CONVERSATION_STATUS,
  getConversationStatusMeta,
} from '#/utils/status-message';

import { formatListTime, formatMessageTime, nameInitial } from './format';
import { messageLabels } from './labels';

const route = useRoute();

// 课程/班级选择
const courseId = ref<number | undefined>();
const classId = ref<number | undefined>();
const classList = ref<{ className: string; id: number }[]>([]);
const courseList = ref<{ courseId: number; courseName: string }[]>([]);

// 学生列表
const searchName = ref('');
const studentList = ref<EduMessageApi.MessageStudent[]>([]);
const studentLoading = ref(false);
const selectedStudentId = ref<number | undefined>();

// 消息
const messageList = ref<EduMessageApi.MessageDetail[]>([]);
const messageLoading = ref(false);
const inputContent = ref('');
const sending = ref(false);
const chatBodyRef = ref<HTMLElement>();
const conversationClosed = ref(false);
const closingOrReopening = ref(false);

const selectedStudent = computed(() =>
  studentList.value.find((s) => s.studentId === selectedStudentId.value),
);

const classOptions = computed(() =>
  classList.value.map((c) => ({ label: c.className, value: c.id })),
);
const courseOptions = computed(() =>
  courseList.value.map((c) => ({ label: c.courseName, value: c.courseId })),
);

/** 对话区页头的范围说明：课程 · 班级 */
const conversationScope = computed(() =>
  [
    courseList.value.find((c) => c.courseId === courseId.value)?.courseName,
    classList.value.find((c) => c.id === classId.value)?.className,
  ]
    .filter(Boolean)
    .join(' · '),
);

const statusMeta = computed(() =>
  getConversationStatusMeta(
    conversationClosed.value
      ? CONVERSATION_STATUS.CLOSED
      : CONVERSATION_STATUS.OPEN,
  ),
);

const hasScope = computed(() => Boolean(classId.value && courseId.value));

function scrollToBottom() {
  nextTick(() => {
    if (chatBodyRef.value) {
      chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight;
    }
  });
}

// 加载班级列表
async function loadClassList() {
  const list = await getSimpleClassList();
  classList.value = list ?? [];
  const requestedClassId = Number(route.query.classId);
  if (
    requestedClassId &&
    classList.value.some((item) => item.id === requestedClassId)
  ) {
    classId.value = requestedClassId;
  }
}

// 班级变化 → 加载课程
watch(classId, async (val) => {
  courseId.value = undefined;
  courseList.value = [];
  selectedStudentId.value = undefined;
  studentList.value = [];
  messageList.value = [];
  if (!val) return;
  const list = await getClassCourseListByClass(val);
  courseList.value = (list ?? []).map((c) => ({
    courseId: c.courseId,
    courseName: c.courseName ?? `课程 ${c.courseId}`,
  }));
  const requestedCourseId = Number(route.query.courseId);
  if (
    requestedCourseId &&
    courseList.value.some((item) => item.courseId === requestedCourseId)
  ) {
    courseId.value = requestedCourseId;
  }
});

// 课程变化 → 加载学生列表
watch(courseId, () => {
  selectedStudentId.value = undefined;
  messageList.value = [];
  loadStudents();
});

async function loadStudents() {
  if (!classId.value || !courseId.value) {
    studentList.value = [];
    return;
  }
  studentLoading.value = true;
  try {
    const list = await getMessageStudentList({
      classId: classId.value,
      courseId: courseId.value,
      name: searchName.value || undefined,
    });
    studentList.value = list ?? [];
    const requestedStudentId = Number(route.query.studentId);
    if (
      requestedStudentId &&
      studentList.value.some((item) => item.studentId === requestedStudentId)
    ) {
      await handleSelectStudent(requestedStudentId);
    }
  } finally {
    studentLoading.value = false;
  }
}

function handleSearchStudent() {
  loadStudents();
}

function handleClearSearch() {
  searchName.value = '';
  handleSearchStudent();
}

async function handleSelectStudent(studentId: number) {
  selectedStudentId.value = studentId;
  conversationClosed.value = false;
  await loadMessages();
}

async function loadMessages() {
  if (!classId.value || !courseId.value || !selectedStudentId.value) {
    messageList.value = [];
    return;
  }
  messageLoading.value = true;
  try {
    const detail = await getMessageDetail({
      classId: classId.value,
      courseId: courseId.value,
      studentId: selectedStudentId.value,
    });
    messageList.value = detail?.messages ?? [];
    conversationClosed.value = detail?.status === 1;
    // 打开留言时后端已把这位学生的留言通知标为已读，让顶栏铃铛同步
    emitNotifyUnreadChanged();
    await loadStudentsAfterRead();
    scrollToBottom();
  } finally {
    messageLoading.value = false;
  }
}

async function loadStudentsAfterRead() {
  if (!classId.value || !courseId.value) return;
  studentList.value = await getMessageStudentList({
    classId: classId.value,
    courseId: courseId.value,
    name: searchName.value || undefined,
  });
}

async function handleSend() {
  const text = inputContent.value.trim();
  if (
    !text ||
    text.length > 1000 ||
    conversationClosed.value ||
    !classId.value ||
    !courseId.value ||
    !selectedStudentId.value
  )
    return;
  sending.value = true;
  try {
    await replyMessage({
      classId: classId.value,
      courseId: courseId.value,
      studentId: selectedStudentId.value,
      content: text,
    });
    inputContent.value = '';
    await loadMessages();
  } catch {
    // 失败原因已由全局请求提示给出；输入框里的内容保留，可以直接重试
  } finally {
    sending.value = false;
  }
}

async function handleCloseConversation() {
  if (!classId.value || !courseId.value || !selectedStudentId.value) return;
  closingOrReopening.value = true;
  try {
    await closeConversation({
      classId: classId.value,
      courseId: courseId.value,
      studentId: selectedStudentId.value,
    });
    conversationClosed.value = true;
    message.success(messageLabels.closed);
  } catch {
    // 失败原因已由全局请求提示给出，这里不重复提示
  } finally {
    closingOrReopening.value = false;
  }
}

/** 关闭留言前确认：写明对象、后果与怎么恢复 */
async function confirmCloseConversation() {
  try {
    await confirm({
      cancelText: messageLabels.confirmCancel,
      confirmText: messageLabels.close,
      content: messageLabels.closeConfirmContent,
      icon: 'warning',
      title: messageLabels.closeConfirmTitle(
        selectedStudent.value?.studentName || '这位学生',
      ),
    });
  } catch {
    return;
  }
  await handleCloseConversation();
}

async function handleReopenConversation() {
  if (!classId.value || !courseId.value || !selectedStudentId.value) return;
  closingOrReopening.value = true;
  try {
    await reopenConversation({
      classId: classId.value,
      courseId: courseId.value,
      studentId: selectedStudentId.value,
    });
    conversationClosed.value = false;
    message.success(messageLabels.reopened);
  } catch {
    // 失败原因已由全局请求提示给出，这里不重复提示
  } finally {
    closingOrReopening.value = false;
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
}

onMounted(() => {
  loadClassList();
});

watch(
  () => route.fullPath,
  async () => {
    const requestedClassId = Number(route.query.classId);
    const requestedCourseId = Number(route.query.courseId);
    if (!requestedClassId || !requestedCourseId) return;
    if (classId.value !== requestedClassId) {
      classId.value = requestedClassId;
    } else if (courseId.value === requestedCourseId) {
      await loadStudents();
    } else {
      courseId.value = requestedCourseId;
    }
  },
);
</script>

<template>
  <Page
    :description="messageLabels.teacherDescription"
    :title="messageLabels.teacherTitle"
    auto-content-height
  >
    <template #extra>
      <Select
        v-model:value="classId"
        :options="classOptions"
        :placeholder="messageLabels.selectClass"
        allow-clear
        class="msg-filter"
      />
      <Select
        v-model:value="courseId"
        :options="courseOptions"
        :placeholder="messageLabels.selectCourse"
        allow-clear
        class="msg-filter"
      />
    </template>

    <div class="msg-shell surface-panel">
      <!-- 左栏：留言学生 -->
      <aside aria-label="留言学生" class="msg-list">
        <div class="msg-list__head">
          <Input.Search
            v-model:value="searchName"
            :placeholder="messageLabels.searchStudent"
            allow-clear
            @search="handleSearchStudent"
          />
        </div>
        <div class="msg-list__body">
          <Spin :spinning="studentLoading" :tip="messageLabels.loadingStudents">
            <div class="msg-list__content">
              <EmptyState
                v-if="!hasScope"
                :description="messageLabels.needClassCourseHint"
                :title="messageLabels.needClassCourse"
                compact
                kind="blocked"
              />
              <template v-else-if="studentList.length === 0">
                <EmptyState
                  v-if="searchName && !studentLoading"
                  :description="messageLabels.noMatchedStudentsHint"
                  :title="messageLabels.noMatchedStudents"
                  compact
                  kind="filtered"
                >
                  <template #action>
                    <Button size="small" @click="handleClearSearch">
                      {{ messageLabels.clearSearch }}
                    </Button>
                  </template>
                </EmptyState>
                <EmptyState
                  v-else-if="!studentLoading"
                  :description="messageLabels.noStudentsHint"
                  :title="messageLabels.noStudents"
                  compact
                />
              </template>
              <ul v-else class="msg-conv-list">
                <li v-for="stu in studentList" :key="stu.studentId">
                  <button
                    :aria-current="
                      selectedStudentId === stu.studentId ? 'true' : undefined
                    "
                    :class="{
                      'is-selected': selectedStudentId === stu.studentId,
                      'is-unread': stu.unreadCount > 0,
                    }"
                    class="msg-conv"
                    type="button"
                    @click="handleSelectStudent(stu.studentId)"
                  >
                    <span aria-hidden="true" class="msg-conv__avatar">
                      {{ nameInitial(stu.studentName) }}
                    </span>
                    <span class="msg-conv__main">
                      <span class="msg-conv__line">
                        <span class="msg-conv__name">{{
                          stu.studentName
                        }}</span>
                        <span class="msg-conv__time">
                          {{ formatListTime(stu.lastMessageTime) }}
                        </span>
                      </span>
                      <span class="msg-conv__line">
                        <span class="msg-conv__preview">
                          {{ stu.lastMessageContent || messageLabels.empty }}
                        </span>
                        <span
                          v-if="stu.unreadCount > 0"
                          :aria-label="messageLabels.unread(stu.unreadCount)"
                          class="msg-unread"
                        >
                          <span
                            aria-hidden="true"
                            class="msg-unread__dot"
                          ></span>
                          {{ stu.unreadCount }}
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
        <template v-if="selectedStudentId">
          <header class="msg-chat__head">
            <div class="msg-chat__heading">
              <div class="msg-chat__title">
                <span class="msg-chat__name">
                  {{ selectedStudent?.studentName || '学生' }}
                </span>
                <StatusTag :label="statusMeta.label" :tone="statusMeta.tone" />
              </div>
              <div v-if="conversationScope" class="msg-chat__meta">
                {{ conversationScope }}
              </div>
            </div>
            <div class="msg-chat__actions">
              <Button
                v-if="conversationClosed"
                v-access:code="['edu:message:manage']"
                :loading="closingOrReopening"
                size="small"
                type="primary"
                @click="handleReopenConversation"
              >
                {{ messageLabels.reopen }}
              </Button>
              <Button
                v-else
                v-access:code="['edu:message:manage']"
                :loading="closingOrReopening"
                size="small"
                @click="confirmCloseConversation"
              >
                {{ messageLabels.close }}
              </Button>
            </div>
          </header>

          <div ref="chatBodyRef" class="msg-chat__body">
            <Spin
              :spinning="messageLoading"
              :tip="messageLabels.loadingMessages"
            >
              <div class="msg-chat__content">
                <EmptyState
                  v-if="messageList.length === 0 && !messageLoading"
                  :description="messageLabels.emptyTeacherHint"
                  :title="messageLabels.empty"
                  compact
                />
                <div v-else class="msg-thread">
                  <div
                    v-for="msg in messageList"
                    :key="msg.id"
                    :class="{ 'is-own': msg.type !== 0 }"
                    class="msg-item"
                  >
                    <div class="msg-item__meta">
                      {{ msg.senderName || (msg.type === 0 ? '学生' : '教师') }}
                      · {{ formatMessageTime(msg.createTime) }}
                    </div>
                    <div class="msg-item__bubble">{{ msg.content }}</div>
                  </div>
                </div>
              </div>
            </Spin>
          </div>

          <footer class="msg-composer">
            <div v-if="conversationClosed" class="msg-composer__closed">
              <LockKeyhole aria-hidden="true" class="msg-composer__icon" />
              <span>{{ messageLabels.closedTeacherHint }}</span>
            </div>
            <div v-else class="msg-composer__row">
              <Input.TextArea
                v-model:value="inputContent"
                :auto-size="{ minRows: 2, maxRows: 6 }"
                :disabled="sending"
                :maxlength="1000"
                :placeholder="messageLabels.placeholder"
                @keydown="handleKeydown"
              />
              <Button
                v-access:code="['edu:message:manage']"
                :disabled="!inputContent.trim()"
                :loading="sending"
                type="primary"
                @click="handleSend"
              >
                {{ messageLabels.send }}
              </Button>
            </div>
          </footer>
        </template>
        <EmptyState
          v-else
          :description="
            hasScope
              ? messageLabels.noSelectionTeacherHint
              : messageLabels.noScopeTeacherHint
          "
          :title="messageLabels.noSelection"
          class="msg-chat__placeholder"
        />
      </section>
    </div>
  </Page>
</template>

<style scoped src="./message-shell.css"></style>
