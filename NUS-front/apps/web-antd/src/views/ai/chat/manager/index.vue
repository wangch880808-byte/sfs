<script lang="ts" setup>
import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Tabs } from 'ant-design-vue';

import ChatConversationList from './modules/conversation-list.vue';
import ChatMessageList from './modules/message-list.vue';

const activeTabName = ref('conversation');
</script>

<template>
  <Page
    auto-content-height
    description="查看全部用户的 AI 对话与消息记录，可以删除不当内容。"
    title="聊天管理"
  >
    <Tabs v-model:active-key="activeTabName" class="chat-manager-tabs">
      <Tabs.TabPane tab="对话列表" key="conversation">
        <ChatConversationList />
      </Tabs.TabPane>
      <Tabs.TabPane tab="消息列表" key="message">
        <ChatMessageList />
      </Tabs.TabPane>
    </Tabs>
  </Page>
</template>

<style scoped>
/* 页签占满页面内容高度，表格在页签内容里自适应高度 */
.chat-manager-tabs {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chat-manager-tabs :deep(.ant-tabs-content-holder) {
  flex: 1;
  min-height: 0;
}

.chat-manager-tabs :deep(.ant-tabs-content),
.chat-manager-tabs :deep(.ant-tabs-tabpane-active) {
  height: 100%;
}
</style>
