<script lang="ts" setup>
import { Fallback } from '@vben/common-ui';

defineOptions({ name: 'Fallback404Demo' });
</script>

<template>
  <Fallback
    description="地址可能有误，或者页面已经下线。返回首页后从导航进入。"
    status="404"
    title="没有找到这个页面"
  />
</template>
