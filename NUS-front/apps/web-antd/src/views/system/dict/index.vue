<script lang="ts" setup>
import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import DataGrid from './modules/data-grid.vue';
import TypeGrid from './modules/type-grid.vue';

const searchDictType = ref<string>(); // 搜索的字典类型

function handleDictTypeSelect(dictType: string) {
  searchDictType.value = dictType;
}
</script>

<template>
  <Page auto-content-height>
    <!-- 1280 以上左右两栏；更窄时上下排列，免得两栏都挤 -->
    <div class="flex h-full flex-col gap-4 xl:flex-row">
      <!-- 字典类型列表 -->
      <div class="min-h-0 min-w-0 flex-1">
        <TypeGrid @select="handleDictTypeSelect" />
      </div>
      <!-- 字典数据列表 -->
      <div class="min-h-0 min-w-0 flex-1">
        <DataGrid :dict-type="searchDictType" />
      </div>
    </div>
  </Page>
</template>
