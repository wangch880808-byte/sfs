<script lang="ts" setup>
import type { UploadFile } from 'ant-design-vue';

import type { CapacityCourseApi } from '#/api/capacity/course';

import { computed, nextTick, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import { Modal as AntModal, Button, message, Upload } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createCourse, getCourse, updateCourse } from '#/api/capacity/course';
import { snapshotForm, useDirtyGuard } from '#/composables/use-dirty-guard';
import { $t } from '#/locales';
import { discardChangesConfirm } from '#/utils/confirm-copy';
import { defaultCourseCoverUrls } from '#/utils/course-cover';

import {
  parseAssistantIds,
  stringifyAssistantIds,
  useFormSchema,
} from '../data';

defineOptions({ name: 'CapacityCourseForm' });

const emit = defineEmits(['success']);
const formData = ref<CapacityCourseApi.Course>();
const userStore = useUserStore();
const courseImageFile = ref<File>();
const courseImageList = ref<UploadFile[]>([]);
const courseImageRemoved = ref(false);
const selectedDefaultCoverUrl = ref('');
const defaultCoverPickerVisible = ref(false);

function getCurrentLoginUserId() {
  const userInfo = userStore.userInfo as
    | undefined
    | { id?: number | string; userId?: number | string };
  return Number(userInfo?.userId ?? userInfo?.id ?? 0) || undefined;
}

const getTitle = computed(() => {
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['实训课程'])
    : '新建实训课程';
});
const confirmText = computed(() => (formData.value?.id ? '保存' : '新建'));
const courseFormHint = computed(() =>
  formData.value?.id
    ? '可以修改课程名称、图片、简介和助教团队；申请公共课的课程，提交后会重新进入管理员审批。'
    : '新建后进入课程详情，可以绑定班级、发布实训任务、维护课程资源；公共课经管理员审批后才对全体教师开放。',
);

function buildDefaultValues() {
  return {
    id: undefined,
    indicatorId: undefined,
    isPublicCourse: 1,
    courseName: '',
    courseImageUrl: undefined,
    courseIntroduction: '',
    teacherId: getCurrentLoginUserId(),
    teachAssistantIds: [],
    status: 0,
  };
}

function buildSubmitData(values: Record<string, any>) {
  let courseImageUrl = values.courseImageUrl;
  const currentUserId = getCurrentLoginUserId();
  if (courseImageRemoved.value && !courseImageFile.value) {
    courseImageUrl = null;
  }
  return {
    ...values,
    courseImageUrl,
    teacherId: Number(values.teacherId || currentUserId) || undefined,
    teachAssistantIds: stringifyAssistantIds(
      parseAssistantIds(values.teachAssistantIds).filter(
        (id) => id !== currentUserId,
      ),
    ),
  } as CapacityCourseApi.Course;
}

function resetCourseImageState() {
  courseImageFile.value = undefined;
  courseImageList.value = [];
  courseImageRemoved.value = false;
  selectedDefaultCoverUrl.value = '';
  defaultCoverPickerVisible.value = false;
}

function buildRemoteImageFile(url?: string) {
  if (!url) {
    return [];
  }
  return [
    {
      uid: '-1',
      name: url.slice(Math.max(0, url.lastIndexOf('/') + 1)) || 'course-image',
      status: 'done',
      url,
    } as UploadFile,
  ];
}

function handleCourseImageBeforeUpload(file: File) {
  if (!file.type.startsWith('image/')) {
    message.error('只能上传图片文件，请重新选择。');
    return Upload.LIST_IGNORE;
  }
  if (file.size / 1024 / 1024 > 5) {
    message.error('课程图片不能超过 5 MB，请压缩后再上传。');
    return Upload.LIST_IGNORE;
  }
  courseImageFile.value = file;
  courseImageRemoved.value = false;
  selectedDefaultCoverUrl.value = '';
  courseImageList.value = [
    {
      uid: `${Date.now()}`,
      name: file.name,
      status: 'done',
      url: URL.createObjectURL(file),
    } as UploadFile,
  ];
  return false;
}

function handleCourseImageRemove() {
  courseImageFile.value = undefined;
  courseImageRemoved.value = true;
  selectedDefaultCoverUrl.value = '';
  courseImageList.value = [];
  formApi.setValues({ courseImageUrl: null });
}

async function handleSelectDefaultCover(url: string) {
  courseImageFile.value = undefined;
  courseImageRemoved.value = false;
  selectedDefaultCoverUrl.value = url;
  defaultCoverPickerVisible.value = false;
  courseImageList.value = buildRemoteImageFile(url);
  await formApi.setValues({ courseImageUrl: url });
}

function handleOpenDefaultCoverPicker() {
  defaultCoverPickerVisible.value = true;
}

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 100,
  },
  layout: 'horizontal',
  schema: useFormSchema(getCurrentLoginUserId()),
  showDefaultActions: false,
});

/** 打开并回填完成后的快照（表单值 + 课程图片）；关闭时清空 */
const formBaseline = ref<null | string>(null);
let modalOpen = false;
function currentFormSnapshot() {
  return snapshotForm({
    image: courseImageFile.value
      ? `${courseImageFile.value.name}:${courseImageFile.value.size}`
      : null,
    imageRemoved: courseImageRemoved.value,
    values: formApi.form?.values,
  });
}
const dirtyGuard = useDirtyGuard(
  () =>
    formBaseline.value !== null && currentFormSnapshot() !== formBaseline.value,
  { copy: discardChangesConfirm('课程信息还没有保存，关闭后不会保留。') },
);

const [Modal, modalApi] = useVbenModal({
  onBeforeClose: dirtyGuard.onBeforeClose,
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    try {
      const values = await formApi.getValues();
      const submitData = buildSubmitData(values);
      await (submitData.id
        ? updateCourse(submitData, courseImageFile.value)
        : createCourse(submitData, courseImageFile.value));
      await dirtyGuard.skipGuard(() => modalApi.close());
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    modalOpen = isOpen;
    if (!isOpen) {
      formBaseline.value = null;
      formData.value = undefined;
      resetCourseImageState();
      return;
    }

    const data = modalApi.getData<Partial<CapacityCourseApi.Course>>();
    modalApi.lock();
    try {
      resetCourseImageState();
      if (!data?.id) {
        await formApi.setValues(buildDefaultValues());
        return;
      }
      formData.value = await getCourse(data.id);
      selectedDefaultCoverUrl.value = formData.value.courseImageUrl || '';
      courseImageList.value = buildRemoteImageFile(
        formData.value.courseImageUrl,
      );
      await formApi.setValues({
        ...formData.value,
        teachAssistantIds: parseAssistantIds(
          formData.value.teachAssistantIds,
        ).filter((id) => id !== getCurrentLoginUserId()),
      });
    } finally {
      modalApi.unlock();
      if (modalOpen) {
        // 回填触发的赋值完成后再记快照
        await nextTick();
        formBaseline.value = currentFormSnapshot();
      }
    }
  },
});
dirtyGuard.bindPopup(modalApi);
</script>

<template>
  <Modal
    :confirm-text="confirmText"
    :title="getTitle"
    class="w-[720px] max-w-[92vw]"
  >
    <p class="course-form__hint">{{ courseFormHint }}</p>
    <Form class="mx-4">
      <template #courseImageUrl>
        <div class="course-cover-field">
          <Upload
            :before-upload="handleCourseImageBeforeUpload"
            :file-list="courseImageList"
            :max-count="1"
            accept="image/*"
            list-type="picture-card"
            @remove="handleCourseImageRemove"
          >
            <div
              v-if="courseImageList.length === 0"
              class="course-cover-field__trigger"
            >
              <IconifyIcon class="size-5" icon="lucide:image-plus" />
              <span>选择图片</span>
            </div>
          </Upload>
          <div class="course-cover-field__aside">
            <Button size="small" @click="handleOpenDefaultCoverPicker">
              使用默认图片
            </Button>
            <p class="course-cover-field__help">
              图片不超过 5 MB；不选时，系统按课程自动配一张默认图片。
            </p>
          </div>
        </div>
      </template>
    </Form>
    <AntModal
      v-model:open="defaultCoverPickerVisible"
      :footer="null"
      title="选择默认课程封面"
      width="760px"
    >
      <p class="course-cover-picker__hint">点一张图片即可选用，可以随时换。</p>
      <div class="grid grid-cols-3 gap-3 sm:grid-cols-4">
        <button
          v-for="(coverUrl, index) in defaultCourseCoverUrls"
          :key="coverUrl"
          :aria-label="`默认图片 ${index + 1}`"
          :aria-pressed="selectedDefaultCoverUrl === coverUrl"
          :class="{
            'is-selected': selectedDefaultCoverUrl === coverUrl,
          }"
          class="course-cover-option"
          type="button"
          @click="handleSelectDefaultCover(coverUrl)"
        >
          <img :src="coverUrl" alt="" />
          <span
            v-if="selectedDefaultCoverUrl === coverUrl"
            class="course-cover-option__check"
          >
            <IconifyIcon icon="lucide:check" />
          </span>
        </button>
      </div>
    </AntModal>
  </Modal>
</template>

<style scoped>
.course-form__hint {
  margin: var(--nus-space-1) var(--nus-space-4) var(--nus-space-5);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

/* ---- 课程图片：上传框在左，默认图片入口与说明在右 ---- */
.course-cover-field {
  display: flex;
  gap: var(--nus-space-4);
  align-items: flex-start;
}

/* 图片卡片的外层默认铺满一行，这里收窄到卡片本身，说明才能紧挨着它 */
.course-cover-field :deep(.ant-upload-wrapper) {
  flex: none;
  width: auto;
}

.course-cover-field :deep(.ant-upload-list-picture-card) {
  flex-wrap: nowrap;
}

.course-cover-field__trigger {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-1);
  align-items: center;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.course-cover-field__aside {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-2);
  align-items: flex-start;
  min-width: 0;
  padding-top: var(--nus-space-1);
}

.course-cover-field__help {
  margin: 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

/* ---- 默认图片选择：悬停换边框，选中用主色边框加勾 ---- */
.course-cover-picker__hint {
  margin: 0 0 var(--nus-space-3);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.course-cover-option {
  position: relative;
  aspect-ratio: 16 / 9;
  padding: 0;
  overflow: hidden;
  cursor: pointer;
  background: var(--nus-bg-sunken);
  border: 2px solid transparent;
  border-radius: var(--nus-radius-lg);
  transition: var(--nus-transition-colors);
}

.course-cover-option:hover {
  border-color: var(--nus-border-strong);
}

.course-cover-option.is-selected {
  border-color: var(--nus-primary);
}

.course-cover-option:focus-visible {
  outline: 2px solid var(--nus-focus-ring);
  outline-offset: 2px;
}

.course-cover-option img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.course-cover-option__check {
  position: absolute;
  top: var(--nus-space-1);
  right: var(--nus-space-1);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  font-size: var(--nus-fs-caption);
  color: var(--nus-on-fill);
  background: var(--nus-primary-fill);
  border-radius: 50%;
}
</style>
