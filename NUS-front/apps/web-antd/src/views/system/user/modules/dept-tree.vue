<script lang="ts" setup>
import type { SystemDeptApi } from '#/api/system/dept';

import { onMounted, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';
import { handleTree } from '@vben/utils';

import { Input, Spin, Tree } from 'ant-design-vue';

import { getSimpleDeptList } from '#/api/system/dept';
import { EmptyState } from '#/components/empty-state';

const emit = defineEmits(['select']);
const deptList = ref<SystemDeptApi.Dept[]>([]); // 组织列表
const deptTree = ref<any[]>([]); // 组织树
const expandedKeys = ref<number[]>([]); // 展开的节点
const loading = ref(false); // 加载状态
const searchValue = ref(''); // 搜索值
const loadFailed = ref(false); // 组织列表没有读出来

/** 处理搜索逻辑 */
function handleSearch(e: any) {
  const value = e.target.value;
  searchValue.value = value;
  const filteredList = value
    ? deptList.value.filter((item) =>
        item.name.toLowerCase().includes(value.toLowerCase()),
      )
    : deptList.value;
  deptTree.value = handleTree(filteredList);
  // 展开所有节点
  expandedKeys.value = deptTree.value.map((node) => node.id!);
}

/** 选中组织 */
function handleSelect(_selectedKeys: any[], info: any) {
  emit('select', info.node.dataRef);
}

/** 初始化 */
onMounted(async () => {
  try {
    loading.value = true;
    loadFailed.value = false;
    const data = await getSimpleDeptList();
    deptList.value = data;
    deptTree.value = handleTree(data);
  } catch (error) {
    loadFailed.value = true;
    console.error('获取组织数据失败', error);
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="dept-tree">
    <Input
      placeholder="搜索组织"
      allow-clear
      v-model:value="searchValue"
      @change="handleSearch"
      class="w-full"
    >
      <template #prefix>
        <IconifyIcon icon="lucide:search" class="size-4" />
      </template>
    </Input>
    <Spin
      :spinning="loading"
      tip="正在读取组织…"
      wrapper-class-name="dept-tree__spin w-full"
    >
      <Tree
        @select="handleSelect"
        v-if="deptTree.length > 0"
        class="dept-tree__nodes pt-2"
        :tree-data="deptTree"
        :default-expand-all="true"
        :field-names="{ title: 'name', key: 'id', children: 'children' }"
      />
      <EmptyState
        v-else-if="loadFailed"
        compact
        description="网络或服务暂时不可用，刷新页面再试。"
        kind="blocked"
        title="组织列表没有读出来"
      />
      <EmptyState
        v-else-if="!loading"
        compact
        :description="
          searchValue
            ? '换个关键词再试。'
            : '在组织管理页面新建组织后，会显示在这里。'
        "
        :kind="searchValue ? 'filtered' : 'empty'"
        :title="searchValue ? '没有匹配的组织' : '还没有组织'"
      />
    </Spin>
  </div>
</template>

<style scoped>
.dept-tree {
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.dept-tree :deep(.dept-tree__spin) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.dept-tree :deep(.dept-tree__spin .ant-spin-container) {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.dept-tree :deep(.dept-tree__nodes) {
  height: 100%;
  min-height: 0;
  overflow: hidden auto;
}

.dept-tree :deep(.dept-tree__nodes .ant-tree-treenode) {
  display: flex;
  width: 100%;
  min-width: 0;
  white-space: nowrap;
}

.dept-tree :deep(.dept-tree__nodes .ant-tree-indent),
.dept-tree :deep(.dept-tree__nodes .ant-tree-switcher) {
  flex-shrink: 0;
}

.dept-tree :deep(.dept-tree__nodes .ant-tree-node-content-wrapper) {
  display: block;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
}

.dept-tree :deep(.dept-tree__nodes .ant-tree-title) {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
