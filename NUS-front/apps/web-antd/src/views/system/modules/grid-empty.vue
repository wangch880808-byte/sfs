<script lang="ts" setup>
import { computed } from 'vue';

import { EmptyState } from '#/components/empty-state';

/**
 * 列表的空状态（V1.0.1.6 界面规范）：没有数据时写下一步，筛选没有结果时提示换条件或重置。
 * 放在 Grid 的 empty 插槽里，系统管理与教务管理的列表共用。
 */
interface Props {
  /** 列表里的对象，例如「角色」「通知模板」 */
  subject: string;
  /** 还没有数据时的下一步 */
  hint?: string;
  /** 筛选条件是否生效 */
  filtered?: boolean;
  /** 还没有数据时的标题，默认“还没有{subject}” */
  emptyTitle?: string;
}

const props = withDefaults(defineProps<Props>(), {
  emptyTitle: '',
  filtered: false,
  hint: '',
});

const title = computed(() =>
  props.filtered
    ? `没有符合条件的${props.subject}`
    : props.emptyTitle || `还没有${props.subject}`,
);

const description = computed(() =>
  props.filtered ? '换个筛选条件，或点「重置」查看全部。' : props.hint,
);
</script>

<template>
  <EmptyState
    :description="description"
    :kind="filtered ? 'filtered' : 'empty'"
    :title="title"
  />
</template>
