<script setup lang="ts">
import { Button } from 'ant-design-vue';

import { EmptyState } from '#/components/empty-state';

const emits = defineEmits(['onNewConversation']);

/** 新建 conversation 聊天对话 */
function handlerNewChat() {
  emits('onNewConversation');
}
</script>

<template>
  <div class="flex h-full w-full items-center justify-center">
    <EmptyState
      description="新建一个对话开始提问，或从左侧选择已有的对话。"
      title="还没有选中对话"
    >
      <template #action>
        <Button @click="handlerNewChat">新建对话</Button>
      </template>
    </EmptyState>
  </div>
</template>
