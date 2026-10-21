<script setup lang="ts">
import type { PropType } from 'vue';

import type { AiModelChatRoleApi } from '#/api/ai/model/chatRole';

import { ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Avatar, Button, Dropdown, Menu } from 'ant-design-vue';

import { EmptyState } from '#/components/empty-state';

const props = defineProps({
  loading: {
    type: Boolean,
    required: true,
  },
  roleList: {
    type: Array as PropType<AiModelChatRoleApi.ChatRole[]>,
    required: true,
  },
  showMore: {
    type: Boolean,
    required: false,
    default: false,
  },
});

const emits = defineEmits(['onDelete', 'onEdit', 'onUse', 'onPage']);

const tabsRef = ref<any>();

/** 操作：编辑、删除 */
async function handleMoreClick(data: any) {
  const type = data[0];
  const role = data[1];
  if (type === 'delete') {
    emits('onDelete', role);
  } else {
    emits('onEdit', role);
  }
}

/** 选中 */
function handleUseClick(role: any) {
  emits('onUse', role);
}

/** 滚动 */
async function handleTabsScroll() {
  if (tabsRef.value) {
    const { scrollTop, scrollHeight, clientHeight } = tabsRef.value;
    if (scrollTop + clientHeight >= scrollHeight - 20 && !props.loading) {
      emits('onPage');
    }
  }
}
</script>

<template>
  <div ref="tabsRef" class="role-list" @scroll="handleTabsScroll">
    <EmptyState
      v-if="roleList.length === 0"
      compact
      description="换个类别或关键词，或者新建自己的角色。"
      title="这里还没有角色"
    />
    <div v-for="role in roleList" :key="role.id" class="role-card">
      <!-- 头部：头像、名称 -->
      <div class="role-card__head">
        <Avatar :src="role.avatar" :size="32" class="flex-none" />
        <div class="role-card__name">{{ role.name }}</div>
      </div>
      <!-- 描述信息 -->
      <div class="role-card__desc">
        {{ role.description || '没有填写角色描述' }}
      </div>
      <!-- 底部操作按钮 -->
      <div class="role-card__actions">
        <Dropdown v-if="showMore">
          <Button aria-label="更多操作" size="small" type="text">
            <IconifyIcon icon="lucide:ellipsis" class="size-4" />
          </Button>
          <template #overlay>
            <Menu>
              <Menu.Item @click="handleMoreClick(['edit', role])">
                <div class="flex items-center gap-2">
                  <IconifyIcon icon="lucide:pencil" />
                  <span>编辑</span>
                </div>
              </Menu.Item>
              <Menu.Item danger @click="handleMoreClick(['delete', role])">
                <div class="flex items-center gap-2">
                  <IconifyIcon icon="lucide:trash-2" />
                  <span>删除</span>
                </div>
              </Menu.Item>
            </Menu>
          </template>
        </Dropdown>
        <Button size="small" @click="handleUseClick(role)">使用</Button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 角色卡片（V1.0.1.6 界面规范）：淡边框面板，不加阴影；“使用”用默认按钮，避免一屏多个主按钮 */
.role-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--nus-space-3);
  align-content: start;
  height: 100%;
  padding-bottom: var(--nus-space-10);
  overflow: auto;
}

.role-list > :deep(.nus-empty) {
  grid-column: 1 / -1;
}

.role-card {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-2);
  padding: var(--nus-space-4);
  background: transparent;
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius-lg);
  transition: var(--nus-transition-colors);
}

.role-card:hover {
  border-color: var(--nus-border);
}

.role-card__head {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  min-width: 0;
}

.role-card__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  white-space: nowrap;
}

.role-card__desc {
  display: -webkit-box;
  min-height: 40px;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
  -webkit-box-orient: vertical;
}

.role-card__actions {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  justify-content: flex-end;
}
</style>
