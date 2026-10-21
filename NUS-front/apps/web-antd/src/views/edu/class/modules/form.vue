<script lang="ts" setup>
import type { EduClassApi } from '#/api/edu/class';

import { computed, h, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { getDictLabel } from '@vben/hooks';

import { Button, message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createClass,
  getClass,
  getClassPage,
  updateClass,
} from '#/api/edu/class';
import { $t } from '#/locales';

import { getDeptNameMap, useFormSchema } from '../data';

const emit = defineEmits(['success']);
const EDU_EDUCATION_LEVEL_DICT = 'edu_education_level';
const AUTO_NAME_TRIGGER_FIELDS = new Set([
  'deptId',
  'educationLevel',
  'gradeYear',
]);

const formData = ref<EduClassApi.Clazz>();
const isEditMode = ref(false);
const isFormInitializing = ref(false);
const manualClassNameMode = ref(false);
const deptNameMap = ref<Map<number, string>>(new Map());
const autoNameRequestSeq = ref(0);

const getTitle = computed(() => {
  return formData.value?.id ? $t('ui.actionTitle.edit', ['班级']) : '新建班级';
});
const classFormHint = computed(() =>
  formData.value?.id
    ? '这里的班级信息会直接用于课程绑定与任务发布。'
    : '班级名称按年级、组织和教育层次自动生成。新建后，可以在实训课程详情里按学期绑定课程。',
);

async function loadDeptNameMap() {
  deptNameMap.value = await getDeptNameMap();
}

function getClassNamePrefix(values: Partial<EduClassApi.Clazz>) {
  const gradeYear = Number(values.gradeYear);
  const deptId = Number(values.deptId);
  const educationLevel = Number(values.educationLevel);
  const gradeYearPart = gradeYear ? `${gradeYear}级` : '';
  const deptPart = deptNameMap.value.get(deptId) ?? '';
  const educationLevelPart =
    getDictLabel(EDU_EDUCATION_LEVEL_DICT, educationLevel) || '';
  return `${gradeYearPart}${deptPart}${educationLevelPart}`;
}

async function refreshClassName(values: Partial<EduClassApi.Clazz>) {
  const prefix = getClassNamePrefix(values);
  const gradeYear = Number(values.gradeYear);
  const deptId = Number(values.deptId);
  const educationLevel = Number(values.educationLevel);
  const hasAnyDimension =
    !!gradeYear || !!deptId || !!educationLevel || prefix.length > 0;
  if (!hasAnyDimension) {
    await formApi.setFieldValue('className', undefined);
    return;
  }
  if (!gradeYear || !deptId || !educationLevel) {
    await formApi.setFieldValue('className', prefix);
    return;
  }

  const requestId = ++autoNameRequestSeq.value;
  const pageResult = await getClassPage({
    pageNo: 1,
    pageSize: 1,
    gradeYear,
    deptId,
    educationLevel,
  });

  if (requestId !== autoNameRequestSeq.value) {
    return;
  }

  const total = Number(pageResult?.total ?? 0);
  const className = `${prefix}${total + 1}班`;
  await formApi.setFieldValue('className', className);
}

async function toggleManualClassNameMode() {
  manualClassNameMode.value = !manualClassNameMode.value;
  if (!manualClassNameMode.value && !isEditMode.value) {
    const values = (await formApi.getValues()) as Partial<EduClassApi.Clazz>;
    await refreshClassName(values);
  }
}

function buildFormSchema() {
  return useFormSchema().map((item) => {
    if (item.fieldName !== 'className') {
      return item;
    }
    return {
      ...item,
      componentProps: () => ({
        ...item.componentProps,
        readonly: !manualClassNameMode.value,
      }),
      renderComponentContent: () => ({
        addonAfter: () =>
          h(
            Button,
            {
              type: 'link',
              size: 'small',
              onClick: () => {
                void toggleManualClassNameMode();
              },
            },
            () => (manualClassNameMode.value ? '自动' : '编辑'),
          ),
      }),
    };
  });
}

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 90,
  },
  async handleValuesChange(values, fieldsChanged) {
    if (
      isFormInitializing.value ||
      isEditMode.value ||
      manualClassNameMode.value
    ) {
      return;
    }
    const shouldRefresh = fieldsChanged.some((field) =>
      AUTO_NAME_TRIGGER_FIELDS.has(field),
    );
    if (!shouldRefresh) {
      return;
    }
    try {
      await refreshClassName(values as Partial<EduClassApi.Clazz>);
    } catch (error) {
      console.error(error);
    }
  },
  layout: 'horizontal',
  schema: buildFormSchema(),
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    const data = (await formApi.getValues()) as EduClassApi.Clazz;
    try {
      await (formData.value?.id ? updateClass(data) : createClass(data));
      await modalApi.close();
      emit('success');
      message.success($t('ui.actionMessage.operationSuccess'));
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formData.value = undefined;
      isEditMode.value = false;
      isFormInitializing.value = false;
      manualClassNameMode.value = false;
      autoNameRequestSeq.value = 0;
      return;
    }
    const data = modalApi.getData<Partial<EduClassApi.Clazz>>();
    isEditMode.value = !!data?.id;
    modalApi.lock();
    try {
      await loadDeptNameMap();
      isFormInitializing.value = true;
      manualClassNameMode.value = false;
      if (!data?.id) {
        await formApi.setValues({});
        return;
      }
      formData.value = await getClass(data.id);
      await formApi.setValues(formData.value);
    } finally {
      isFormInitializing.value = false;
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-1/2">
    <p class="class-form-hint">{{ classFormHint }}</p>
    <Form class="mx-4" />
  </Modal>
</template>

<style scoped>
.class-form-hint {
  margin: 0 16px 16px;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}
</style>
