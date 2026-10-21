<script lang="ts" setup>
import type { StudentMessageItem } from '#/views/dashboard/shared/teacher';

import { computed } from 'vue';
import { useRouter } from 'vue-router';

import { Button } from 'ant-design-vue';

import { EmptyState } from '#/components/empty-state';
import { DashboardPanel, DashboardRow } from '#/views/dashboard/components';
import {
  TEACHER_LINKS,
  useAvailableLink,
} from '#/views/dashboard/shared/links';
import { formatFullTime, formatPastTime } from '#/views/dashboard/shared/time';

/**
 * 教师工作台：最近的学生留言（来自“收到学生留言”的站内信），整行打开对应会话；
 * 标题旁的未读数是工作台统计的未读留言条数。
 */
const props = withDefaults(
  defineProps<{
    error?: boolean;
    items: StudentMessageItem[];
    loading?: boolean;
    now: number;
    unread?: number;
  }>(),
  { error: false, loading: false, unread: undefined },
);

defineEmits<{ retry: [] }>();

const router = useRouter();
const linkOf = useAvailableLink();
const messagesLink = computed(() => linkOf(TEACHER_LINKS.messages));
const countText = computed(() =>
  props.unread ? `${props.unread} 条未读` : '',
);
</script>

<template>
  <DashboardPanel
    :count="countText"
    :empty="items.length === 0"
    :error="error"
    :loading="loading"
    :more-to="messagesLink"
    error-title="学生留言没有读到"
    loading-text="正在读取学生留言…"
    more-text="全部留言"
    title="学生留言"
    @retry="$emit('retry')"
  >
    <template #empty>
      <EmptyState
        compact
        description="学生在课程里留言后会出现在这里；更早的留言在「留言互动」里查看。"
        title="最近没有新的学生留言"
      >
        <template v-if="messagesLink" #action>
          <Button size="small" @click="router.push(messagesLink)">
            进入留言互动
          </Button>
        </template>
      </EmptyState>
    </template>
    <ul>
      <li v-for="item in items" :key="item.id">
        <DashboardRow :to="linkOf(item.targetUrl || TEACHER_LINKS.messages)">
          {{ item.studentName }}
          <span v-if="item.courseName" class="course-name">
            {{ item.courseName }}
          </span>
          <template #meta>{{ item.content }}</template>
          <template #aside>
            <span :title="formatFullTime(item.createTime)" class="message-time">
              {{ formatPastTime(item.createTime, now) }}
            </span>
          </template>
        </DashboardRow>
      </li>
    </ul>
  </DashboardPanel>
</template>

<style scoped>
.course-name {
  margin-left: var(--nus-space-2);
  font-size: var(--nus-fs-caption);
  font-weight: var(--nus-fw-regular);
  color: var(--nus-text-tertiary);
}

.message-time {
  color: var(--nus-text-tertiary);
}
</style>
