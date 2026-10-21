<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Button,
  Card,
  Form,
  FormItem,
  InputNumber,
  message,
  Spin,
} from 'ant-design-vue';

import {
  getCertificateThreshold,
  setCertificateThreshold,
} from '#/api/edu/certificate';

const loading = ref(false);
const saving = ref(false);
const threshold = ref<number>(80);

async function loadThreshold() {
  loading.value = true;
  try {
    const data = await getCertificateThreshold();
    threshold.value = data?.threshold ?? 80;
  } finally {
    loading.value = false;
  }
}

async function handleSave() {
  if (threshold.value === undefined || threshold.value === null) {
    message.warning('请输入达标率');
    return;
  }
  saving.value = true;
  try {
    await setCertificateThreshold(threshold.value);
    message.success('获证阈值已保存');
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  loadThreshold();
});
</script>

<template>
  <Page
    auto-content-height
    description="设置学生获得证书所需的能力达标率阈值。"
    title="证书配置"
  >
    <Card class="cert-card" title="获证阈值">
      <Spin :spinning="loading" tip="正在读取当前阈值…">
        <Form layout="vertical">
          <FormItem
            extra="学生的能力达标率达到这个值，即可获得证书。"
            label="达标率"
          >
            <InputNumber
              v-model:value="threshold"
              :max="100"
              :min="0"
              :precision="0"
              addon-after="%"
              class="cert-input"
            />
          </FormItem>
          <FormItem class="cert-actions">
            <Button :loading="saving" type="primary" @click="handleSave">
              保存
            </Button>
          </FormItem>
        </Form>
      </Spin>
    </Card>
  </Page>
</template>

<style scoped>
.cert-card {
  max-width: 640px;
}

.cert-input {
  width: 180px;
}

.cert-actions {
  margin-bottom: 0;
}
</style>
