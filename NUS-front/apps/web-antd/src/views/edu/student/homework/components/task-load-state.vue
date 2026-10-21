<script lang="ts" setup>
import type { StudentLoadError } from '../load-error';

import { computed } from 'vue';
import { useRouter } from 'vue-router';

import { Button, Spin } from 'ant-design-vue';

import { EmptyState } from '#/components/empty-state';

/**
 * 学生任务页（提交、成绩反馈）还没拿到任务时的状态：读取中、等级未解锁、任务不存在或没有分配、读取失败。
 * 等级未解锁写明原因、怎么解锁和去哪里看进度；出错按“什么没成功—原因—下一步”写。
 */
const props = withDefaults(
  defineProps<{
    error?: null | StudentLoadError;
    loading?: boolean;
    /** 读取中时写明在读什么 */
    loadingText?: string;
    /** 等级未解锁时的具体进度（如“还差 2 个任务达标。”），读不到时为空 */
    lockedDetail?: string;
  }>(),
  {
    error: null,
    loading: false,
    loadingText: '正在读取任务…',
    lockedDetail: '',
  },
);

const emit = defineEmits<{ retry: [] }>();

const router = useRouter();

const state = computed(() => {
  switch (props.error?.kind) {
    case 'failed': {
      return {
        title: '任务没有读取成功',
        description: `${props.error.reason}可以重试，或返回任务列表。`,
      };
    }
    case 'locked': {
      return {
        title: '这个任务还没有解锁',
        description: `${props.error.reason}${props.lockedDetail}达标率达到要求后自动解锁，各等级进度可在课程详情的「等级闯关」中查看。`,
      };
    }
    case 'unavailable': {
      return {
        title: '没有找到这个任务',
        description:
          '任务可能已经删除，或没有分配给你。可以回到任务列表查看你的任务。',
      };
    }
    default: {
      return null;
    }
  }
});

function goTaskList() {
  router.push('/student/homework');
}

function goCourses() {
  router.push('/student/course');
}
</script>

<template>
  <div
    v-if="loading && !error"
    class="flex min-h-60 flex-col items-center justify-center gap-3 text-sm text-muted-foreground"
    role="status"
  >
    <Spin />
    <span>{{ loadingText }}</span>
  </div>
  <div v-else-if="state" class="surface-panel">
    <EmptyState
      :description="state.description"
      :title="state.title"
      kind="blocked"
    >
      <template #action>
        <template v-if="error?.kind === 'failed'">
          <Button type="primary" @click="emit('retry')">重试</Button>
          <Button @click="goTaskList">返回任务列表</Button>
        </template>
        <template v-else>
          <Button type="primary" @click="goTaskList">返回任务列表</Button>
          <Button v-if="error?.kind === 'locked'" @click="goCourses">
            查看我的课程
          </Button>
        </template>
      </template>
    </EmptyState>
  </div>
</template>
