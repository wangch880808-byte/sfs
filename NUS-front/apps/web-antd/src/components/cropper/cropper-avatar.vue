<script lang="ts" setup>
import type { CSSProperties } from 'vue';

import type { CropperAvatarProps } from './typing';

import { computed, ref, unref, watch, watchEffect } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { $t } from '@vben/locales';

import { Button, message } from 'ant-design-vue';

import cropperModal from './cropper-modal.vue';

defineOptions({ name: 'CropperAvatar' });

const props = withDefaults(defineProps<CropperAvatarProps>(), {
  width: 200,
  value: '',
  showBtn: true,
  btnProps: () => ({}),
  btnText: '',
  uploadApi: () => Promise.resolve(),
  size: 5,
});

const emit = defineEmits(['update:value', 'change']);

const sourceValue = ref(props.value || '');
const [CropperModal, modalApi] = useVbenModal({
  connectedComponent: cropperModal,
});

const getWidth = computed(() => `${`${props.width}`.replace(/px/, '')}px`);

const getIconWidth = computed(
  () => `${Number.parseInt(`${props.width}`.replace(/px/, '')) / 2}px`,
);

const getStyle = computed((): CSSProperties => ({ width: unref(getWidth) }));

const getImageWrapperStyle = computed(
  (): CSSProperties => ({ height: unref(getWidth), width: unref(getWidth) }),
);

watchEffect(() => {
  sourceValue.value = props.value || '';
});

watch(
  () => sourceValue.value,
  (v: string) => {
    emit('update:value', v);
  },
);

function handleUploadSuccess({ data, source }: any) {
  sourceValue.value = source;
  emit('change', { data, source });
  message.success($t('ui.cropper.uploadSuccess'));
}

const closeModal = () => modalApi.close();
const openModal = () => modalApi.open();

defineExpose({
  closeModal,
  openModal,
});
</script>

<template>
  <!-- 头像容器 -->
  <div class="inline-block text-center" :style="getStyle">
    <!-- 图片包装器 -->
    <div
      class="cropper-avatar__wrapper group relative cursor-pointer overflow-hidden rounded-full"
      :style="getImageWrapperStyle"
      @click="openModal"
    >
      <!-- 遮罩层 -->
      <div
        class="cropper-avatar__mask absolute inset-0 flex cursor-pointer items-center justify-center rounded-full opacity-0 group-hover:opacity-100"
        :style="getImageWrapperStyle"
      >
        <IconifyIcon
          icon="lucide:cloud-upload"
          class="cropper-avatar__icon m-auto"
          :style="{
            ...getImageWrapperStyle,
            width: getIconWidth,
            height: getIconWidth,
            lineHeight: getIconWidth,
          }"
        />
      </div>
      <!-- 头像图片 -->
      <img
        v-if="sourceValue"
        :src="sourceValue"
        alt="头像"
        class="h-full w-full object-cover"
      />
    </div>
    <!-- 上传按钮 -->
    <Button
      v-if="showBtn"
      class="mx-auto mt-2"
      @click="openModal"
      v-bind="btnProps"
    >
      {{ btnText ? btnText : $t('ui.cropper.selectImage') }}
    </Button>

    <CropperModal
      :size="size"
      :src="sourceValue"
      :upload-api="uploadApi"
      @upload-success="handleUploadSuccess"
    />
  </div>
</template>

<style scoped>
/* 头像上传（V1.0.1.6 界面规范）：淡边框，悬停时用遮罩色盖住并显示上传图标 */
.cropper-avatar__wrapper {
  background: var(--nus-bg-surface);
  border: 1px solid var(--nus-border);
}

.cropper-avatar__mask {
  background: var(--nus-overlay);
  transition: opacity var(--nus-dur) var(--nus-ease);
}

.cropper-avatar__icon {
  color: var(--nus-on-fill);
}
</style>
