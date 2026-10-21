<script lang="ts" setup>
import type { UploadFile, UploadProps } from 'ant-design-vue';
import type { UploadRequestOption } from 'ant-design-vue/lib/vc-upload/interface';

import type { FileUploadProps } from './typing';

import type { AxiosProgressEvent } from '#/api/infra/file';

import { computed, ref, toRefs, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';
import { $t } from '@vben/locales';
import { checkFileType, isFunction, isObject, isString } from '@vben/utils';

import { Button, message, Upload } from 'ant-design-vue';

import { UploadResultStatus } from './typing';
import { useUpload, useUploadType } from './use-upload';

defineOptions({ name: 'FileUpload', inheritAttrs: false });

const props = withDefaults(defineProps<FileUploadProps>(), {
  value: () => [],
  modelValue: undefined,
  directory: undefined,
  disabled: false,
  drag: false,
  helpText: '',
  maxSize: 2,
  maxNumber: 1,
  accept: () => [],
  multiple: false,
  api: undefined,
  resultField: '',
  showDescription: false,
});
const emit = defineEmits([
  'change',
  'update:value',
  'update:modelValue',
  'delete',
  'returnText',
  'preview',
]);
const { accept, helpText, maxNumber, maxSize } = toRefs(props);
const isInnerOperate = ref<boolean>(false);
const { getStringAccept } = useUploadType({
  acceptRef: accept,
  helpTextRef: helpText,
  maxNumberRef: maxNumber,
  maxSizeRef: maxSize,
});

/** 计算当前绑定的值，优先使用 modelValue */
const currentValue = computed(() => {
  return props.modelValue === undefined ? props.value : props.modelValue;
});

/** 判断是否使用 modelValue */
const isUsingModelValue = computed(() => {
  return props.modelValue !== undefined;
});

const fileList = ref<UploadProps['fileList']>([]);
const isLtMsg = ref<boolean>(true); // 文件大小错误提示
const isActMsg = ref<boolean>(true); // 文件类型错误提示
const isFirstRender = ref<boolean>(true); // 是否第一次渲染
const uploadNumber = ref<number>(0); // 上传文件计数器
const uploadList = ref<any[]>([]); // 临时上传列表

watch(
  currentValue,
  (v) => {
    if (isInnerOperate.value) {
      isInnerOperate.value = false;
      return;
    }
    let value: string[] = [];
    if (v) {
      if (Array.isArray(v)) {
        value = v;
      } else {
        value.push(v);
      }
      fileList.value = value
        .map((item, i) => {
          if (item && isString(item)) {
            return {
              uid: `${-i}`,
              name: item.slice(Math.max(0, item.lastIndexOf('/') + 1)),
              status: UploadResultStatus.DONE,
              url: item,
            };
          } else if (item && isObject(item)) {
            return item;
          }
          return null;
        })
        .filter(Boolean) as UploadProps['fileList'];
    }
    if (!isFirstRender.value) {
      emit('change', value);
      isFirstRender.value = false;
    }
  },
  {
    immediate: true,
    deep: true,
  },
);

/** 处理文件删除 */
async function handleRemove(file: UploadFile) {
  if (fileList.value) {
    const index = fileList.value.findIndex((item) => item.uid === file.uid);
    index !== -1 && fileList.value.splice(index, 1);
    const value = getValue();
    isInnerOperate.value = true;
    emit('update:value', value);
    emit('update:modelValue', value);
    emit('change', value);
    emit('delete', file);
  }
}

/** 处理文件预览 */
function handlePreview(file: UploadFile) {
  emit('preview', file);
}

/** 处理文件数量超限 */
function handleExceed() {
  message.error($t('ui.upload.maxNumber', [maxNumber.value]));
}

/** 处理上传错误 */
function handleUploadError(error: any) {
  console.error('上传错误:', error);
  // 失败原因已由全局请求提示，不再重复弹出「原因未知」的提示
  // 上传失败时减少计数器
  uploadNumber.value = Math.max(0, uploadNumber.value - 1);
}

/**
 * 上传前校验
 * @param file 待上传的文件
 * @returns 是否允许上传
 */
async function beforeUpload(file: File) {
  const fileContent = await file.text();
  emit('returnText', fileContent);

  // 检查文件数量限制
  if (fileList.value!.length >= props.maxNumber) {
    message.error($t('ui.upload.maxNumber', [props.maxNumber]));
    return Upload.LIST_IGNORE;
  }

  const { maxSize, accept } = props;
  const isAct = checkFileType(file, accept);
  if (!isAct) {
    message.error($t('ui.upload.acceptUpload', [accept]));
    isActMsg.value = false;
    // 防止弹出多个错误提示
    setTimeout(() => (isActMsg.value = true), 1000);
    return Upload.LIST_IGNORE;
  }
  const isLt = file.size / 1024 / 1024 > maxSize;
  if (isLt) {
    message.error($t('ui.upload.maxSizeMultiple', [maxSize]));
    isLtMsg.value = false;
    // 防止弹出多个错误提示
    setTimeout(() => (isLtMsg.value = true), 1000);
    return Upload.LIST_IGNORE;
  }

  // 只有在验证通过后才增加计数器
  uploadNumber.value++;
  return true;
}

/** 自定义上传请求 */
async function customRequest(info: UploadRequestOption) {
  let { api } = props;
  if (!api || !isFunction(api)) {
    api = useUpload(props.directory).httpRequest;
  }
  try {
    // 上传文件
    const progressEvent: AxiosProgressEvent = (e) => {
      const percent = Math.trunc((e.loaded / e.total!) * 100);
      info.onProgress!({ percent });
    };
    const res = await api?.(info.file as File, progressEvent);

    // 处理上传成功后的逻辑
    handleUploadSuccess(res, info.file as File);

    info.onSuccess!(res);
    message.success($t('ui.upload.uploadSuccess'));
  } catch (error: any) {
    console.error(error);
    info.onError!(error);
    handleUploadError(error);
  }
}

/**
 * 处理上传成功
 * @param res 上传响应结果
 * @param file 上传的文件
 */
function handleUploadSuccess(res: any, file: File) {
  // 删除临时文件
  const index = fileList.value?.findIndex((item) => item.name === file.name);
  if (index !== -1) {
    fileList.value?.splice(index!, 1);
  }

  // 添加到临时上传列表
  const fileUrl = res?.url || res?.data || res;
  uploadList.value.push({
    name: file.name,
    url: fileUrl,
    status: UploadResultStatus.DONE,
    uid: file.name + Date.now(),
  });

  // 检查是否所有文件都上传完成
  if (uploadList.value.length >= uploadNumber.value) {
    fileList.value?.push(...uploadList.value);
    uploadList.value = [];
    uploadNumber.value = 0;

    // 更新值
    const value = getValue();
    isInnerOperate.value = true;
    emit('update:value', value);
    emit('update:modelValue', value);
    emit('change', value);
  }
}

/**
 * 获取当前文件列表的值
 * @returns 文件 URL 列表或字符串
 */
function getValue() {
  const list = (fileList.value || [])
    .filter((item) => item?.status === UploadResultStatus.DONE)
    .map((item: any) => {
      if (item?.response && props?.resultField) {
        return item?.response;
      }
      return item?.url || item?.response?.url || item?.response;
    });

  // 单个文件的情况，根据输入参数类型决定返回格式
  if (props.maxNumber === 1) {
    const singleValue = list.length > 0 ? list[0] : '';
    // 如果原始值是字符串或 modelValue 是字符串，返回字符串
    if (
      isString(props.value) ||
      (isUsingModelValue.value && isString(props.modelValue))
    ) {
      return singleValue;
    }
    return singleValue;
  }

  // 多文件情况，根据输入参数类型决定返回格式
  if (isUsingModelValue.value) {
    return Array.isArray(props.modelValue) ? list : list.join(',');
  }

  return Array.isArray(props.value) ? list : list.join(',');
}
</script>

<template>
  <div>
    <Upload
      v-bind="$attrs"
      v-model:file-list="fileList"
      :accept="getStringAccept"
      :before-upload="beforeUpload"
      :custom-request="customRequest"
      :disabled="disabled"
      :max-count="maxNumber"
      :multiple="multiple"
      list-type="text"
      :progress="{ showInfo: true }"
      :show-upload-list="{
        showPreviewIcon: true,
        showRemoveIcon: true,
        showDownloadIcon: true,
      }"
      @remove="handleRemove"
      @preview="handlePreview"
      @reject="handleExceed"
    >
      <div v-if="drag" class="upload-drag-area">
        <p class="upload-drag-area__icon">
          <IconifyIcon icon="lucide:cloud-upload" />
        </p>
        <p class="upload-drag-area__text">点击或拖拽文件到此区域上传</p>
        <p class="upload-drag-area__hint">
          支持 {{ accept.join('/') }} 格式，不超过 {{ maxSize }} MB
        </p>
      </div>
      <div v-else-if="fileList && fileList.length < maxNumber">
        <Button>
          <IconifyIcon icon="lucide:cloud-upload" />
          {{ $t('ui.upload.upload') }}
        </Button>
      </div>
      <div v-if="showDescription && !drag" class="upload-description">
        请上传不超过
        <span class="upload-description__value">{{ maxSize }} MB</span>
        的
        <span class="upload-description__value">{{ accept.join('/') }}</span>
        格式文件
      </div>
    </Upload>
  </div>
</template>

<style scoped>
/* 上传区（V1.0.1.6 界面规范）：凹陷底、虚线边框，悬停时边框换主色；文字只用中性色 */
.upload-drag-area {
  padding: var(--nus-space-5);
  text-align: center;
  background-color: var(--nus-bg-sunken);
  border: 1px dashed var(--nus-border);
  border-radius: var(--nus-radius-lg);
  transition: var(--nus-transition-colors);
}

.upload-drag-area:hover {
  border-color: var(--nus-primary);
}

.upload-drag-area__icon {
  display: flex;
  justify-content: center;
  margin-bottom: var(--nus-space-3);
  font-size: var(--nus-fs-display);
  color: var(--nus-text-tertiary);
}

.upload-drag-area__text {
  margin-bottom: var(--nus-space-1);
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.upload-drag-area__hint {
  margin: 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.upload-description {
  margin-top: var(--nus-space-2);
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.upload-description__value {
  margin: 0 2px;
  font-weight: var(--nus-fw-medium);
  color: var(--nus-text-secondary);
}
</style>
