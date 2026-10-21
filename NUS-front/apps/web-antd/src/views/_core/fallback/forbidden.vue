<script lang="ts" setup>
import { Fallback } from '@vben/common-ui';

defineOptions({ name: 'Fallback403Demo' });
</script>

<template>
  <Fallback
    description="你的账号没有这个页面的权限。需要开通时，请联系管理员。"
    status="403"
    title="没有访问权限"
  />
</template>
