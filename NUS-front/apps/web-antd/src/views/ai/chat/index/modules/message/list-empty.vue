<!-- 新会话欢迎页与教学话题 -->
<script setup lang="ts">
import { IconifyIcon } from '@vben/icons';

const emits = defineEmits<{ onPrompt: [prompt: string] }>();

const promptList = [
  {
    label: '设计课堂教学活动',
    prompt:
      '请帮助我设计一份适合师范生试讲的课堂教学活动，包含教学目标、活动步骤和评价方式。请先询问我任教学科、年级和课题。',
  },
  {
    label: '制定教学能力提升计划',
    prompt:
      '请帮助我制定一份师范生教学能力提升计划，涵盖教学设计、课堂表达和教学反思。请先了解我目前的学习阶段、薄弱环节和可投入时间。',
  },
];
</script>

<template>
  <div class="chat-welcome">
    <div class="chat-welcome__inner">
      <h2 class="chat-welcome__title">师砺 AI 助手</h2>
      <p class="chat-welcome__desc">
        围绕教学设计、课堂表达与教学反思提问，也可以从下面的话题开始。
      </p>
      <div class="chat-welcome__prompts">
        <button
          v-for="prompt in promptList"
          :key="prompt.label"
          type="button"
          class="chat-welcome__prompt"
          @click="emits('onPrompt', prompt.prompt)"
        >
          <span>{{ prompt.label }}</span>
          <IconifyIcon
            icon="lucide:arrow-up-right"
            class="chat-welcome__arrow"
          />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 新对话的欢迎区（V1.0.1.6 界面规范）：标题 20/600，话题按钮用淡边框，悬停换悬停底 */
.chat-welcome {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: var(--nus-space-6) var(--nus-space-4);
  overflow-y: auto;
}

.chat-welcome__inner {
  width: 100%;
  max-width: 560px;
  text-align: center;
}

.chat-welcome__title {
  margin: 0;
  font-size: var(--nus-fs-title);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-title);
  color: var(--nus-text);
}

.chat-welcome__desc {
  margin: var(--nus-space-2) 0 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.chat-welcome__prompts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--nus-space-3);
  margin-top: var(--nus-space-6);
}

.chat-welcome__prompt {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  justify-content: space-between;
  padding: var(--nus-space-3) var(--nus-space-4);
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  text-align: left;
  cursor: pointer;
  background: var(--nus-bg-surface);
  border: 1px solid var(--nus-border);
  border-radius: var(--nus-radius-lg);
  transition: var(--nus-transition-colors);
}

.chat-welcome__prompt:hover {
  background: var(--nus-bg-hover);
  border-color: var(--nus-border-strong);
}

.chat-welcome__prompt:focus-visible {
  outline: 2px solid var(--nus-primary);
  outline-offset: 1px;
}

.chat-welcome__arrow {
  flex: none;
  width: 16px;
  height: 16px;
  color: var(--nus-text-tertiary);
}
</style>
