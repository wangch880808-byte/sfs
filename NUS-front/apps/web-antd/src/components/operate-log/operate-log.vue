<script setup lang="ts">
import type { OperateLogProps } from './typing';

import { DICT_TYPE } from '@vben/constants';
import { getDictLabel } from '@vben/hooks';
import { formatDateTime } from '@vben/utils';

import { Timeline } from 'ant-design-vue';

defineOptions({ name: 'OperateLogV2' });

withDefaults(defineProps<OperateLogProps>(), {
  logList: () => [],
});

/** 用户类型是分类信息，不是状态：圆点里只放类型的首字，统一用中性色 */
function getUserTypeInitial(userType: number) {
  return getDictLabel(DICT_TYPE.USER_TYPE, userType)?.[0] ?? '';
}
</script>

<template>
  <div class="operate-log">
    <Timeline>
      <Timeline.Item v-for="log in logList" :key="log.id">
        <template #dot>
          <span
            :title="getDictLabel(DICT_TYPE.USER_TYPE, log.userType)"
            class="operate-log__dot"
          >
            {{ getUserTypeInitial(log.userType) }}
          </span>
        </template>
        <div class="operate-log__row">
          <span class="operate-log__time">
            {{ formatDateTime(log.createTime) }}
          </span>
          <span class="operate-log__user">{{ log.userName }}</span>
          <span class="operate-log__action">{{ log.action }}</span>
        </div>
      </Timeline.Item>
    </Timeline>
  </div>
</template>

<style scoped>
.operate-log {
  padding-top: var(--nus-space-5);
}

.operate-log__dot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  font-size: var(--nus-fs-caption);
  line-height: 1;
  color: var(--nus-text-secondary);
  background: var(--nus-bg-sunken);
  border: 1px solid var(--nus-border);
  border-radius: 50%;
}

.operate-log__row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
  margin-left: 8px;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
}

.operate-log__time {
  flex: none;
  width: 140px;
  font-size: var(--nus-fs-dense);
  font-variant-numeric: tabular-nums;
  color: var(--nus-text-tertiary);
}

.operate-log__user {
  font-weight: var(--nus-fw-medium);
  color: var(--nus-text);
}

.operate-log__action {
  color: var(--nus-text-secondary);
}
</style>
