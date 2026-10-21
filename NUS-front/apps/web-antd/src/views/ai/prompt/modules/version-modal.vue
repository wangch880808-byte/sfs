<script lang="ts" setup>
import type { AiPromptTemplateApi } from '#/api/ai/prompt';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Button, Checkbox, Input, message, Textarea } from 'ant-design-vue';

import { createPromptTemplateVersion } from '#/api/ai/prompt';

import { PROMPT_EFFECT_HINT } from '../data';

const emit = defineEmits(['success']);

const summary = ref<AiPromptTemplateApi.Summary>();
const content = ref('');
const remark = ref('');
const activate = ref(true);

const title = computed(() =>
  summary.value ? `新建「${summary.value.name}」的版本` : '新建版本',
);
const sameAsDefault = computed(
  () => !!summary.value && content.value === summary.value.defaultContent,
);

/** 用内置默认内容替换编辑框；改动只在保存后生效 */
function fillDefault() {
  if (!summary.value || sameAsDefault.value) return;
  content.value = summary.value.defaultContent;
  message.info('已填入内置默认内容，保存后才会生效。');
}

const [Modal, modalApi] = useVbenModal({
  confirmText: '保存',
  async onConfirm() {
    const current = summary.value;
    if (!current) return;
    if (!content.value.trim()) {
      message.warning('提示词内容是空的，填好后再保存。');
      return;
    }
    modalApi.lock();
    try {
      await createPromptTemplateVersion({
        businessCode: current.businessCode,
        content: content.value,
        remark: remark.value.trim() || undefined,
        activate: activate.value,
      });
      await modalApi.close();
      emit('success');
      message.success(
        activate.value
          ? `「${current.name}」的新版本已保存并启用，${PROMPT_EFFECT_HINT}。`
          : `「${current.name}」的新版本已保存，还没有启用。`,
      );
    } finally {
      modalApi.unlock();
    }
  },
  onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      summary.value = undefined;
      return;
    }
    const data = modalApi.getData<AiPromptTemplateApi.Summary>();
    summary.value = data;
    content.value = data?.content ?? '';
    remark.value = '';
    activate.value = true;
  },
});
</script>

<template>
  <Modal :title="title" class="w-3/5">
    <div v-if="summary" class="prompt-version-form">
      <div class="prompt-version-form__bar">
        <div class="min-w-0">
          <p class="prompt-version-form__label">提示词内容</p>
          <p class="prompt-version-form__hint">
            已填入当前生效的内容。缺少必需内容（如 JSON 输出要求）时不能保存。
          </p>
        </div>
        <Button :disabled="sameAsDefault" @click="fillDefault">
          填入默认内容
        </Button>
      </div>
      <Textarea
        v-model:value="content"
        :auto-size="{ minRows: 12, maxRows: 22 }"
        aria-label="提示词内容"
        class="prompt-version-form__editor"
        placeholder="写下 AI 处理这项业务时要遵循的说明"
        show-count
      />
      <label class="prompt-version-form__field">
        <span class="prompt-version-form__label">备注</span>
        <Input
          v-model:value="remark"
          :maxlength="200"
          allow-clear
          placeholder="说明这一版改了什么（选填）"
        />
      </label>
      <div>
        <Checkbox v-model:checked="activate">保存后立即启用</Checkbox>
        <p class="prompt-version-form__hint">
          {{
            activate
              ? `保存后${PROMPT_EFFECT_HINT}。`
              : '保存为未启用的版本，之后可以在「查看历史」里启用。'
          }}
        </p>
      </div>
    </div>
  </Modal>
</template>

<style scoped>
.prompt-version-form {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-4);
  padding: 0 var(--nus-space-4);
}

.prompt-version-form__bar {
  display: flex;
  gap: var(--nus-space-3);
  align-items: flex-end;
  justify-content: space-between;
}

.prompt-version-form__field {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-2);
}

.prompt-version-form__label {
  margin: 0;
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.prompt-version-form__hint {
  margin: var(--nus-space-1) 0 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-secondary);
}

.prompt-version-form__editor {
  font-family: var(--nus-font-mono);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
}

.prompt-version-form__editor :deep(textarea) {
  font-family: var(--nus-font-mono);
}
</style>
