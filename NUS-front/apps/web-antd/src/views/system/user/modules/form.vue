<script lang="ts" setup>
import type { SystemUserApi } from '#/api/system/user';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createStudent,
  createTeacher,
  createUser,
  getUser,
  updateUser,
} from '#/api/system/user';
import { $t } from '#/locales';

import { useFormSchema } from '../data';
import { getUserFormTitle } from '../form-copy';

interface UserFormModalData extends SystemUserApi.User {
  accountLabel?: string;
  /** 弹窗标题里的对象名，如「学生」「教师」；不传时写「用户」 */
  entityName?: string;
  lockUserType?: boolean;
}

const emit = defineEmits(['success']);
const formData = ref<SystemUserApi.User>();
const entityName = ref<string>();
/** 打开时就按传入数据定下标题，不等详情读完，免得先闪一下「新建」 */
const isEditing = ref(false);
const isFormInitializing = ref(false);
const previousUserType = ref<number | undefined>(undefined);
const getTitle = computed(() =>
  getUserFormTitle(isEditing.value, entityName.value),
);

function createUserByEntry(data: SystemUserApi.User) {
  if (formData.value?.lockUserType && data.userType === 1) {
    return createTeacher(data);
  }
  if (formData.value?.lockUserType && data.userType === 2) {
    return createStudent(data);
  }
  return createUser(data);
}

function buildFormSchema({
  accountLabel,
  fixedUserType,
  lockUserType = false,
  isEditMode = false,
}: {
  accountLabel?: string;
  fixedUserType?: number;
  isEditMode?: boolean;
  lockUserType?: boolean;
} = {}) {
  const schema = useFormSchema({ accountLabel, fixedUserType });
  return schema.map((item) => {
    if (item.fieldName === 'password') {
      return {
        ...item,
        dependencies: {
          ...item.dependencies,
          show: (values: any) => {
            if (isEditMode) {
              return false;
            }
            const rawShow = (item.dependencies as any)?.show;
            if (typeof rawShow === 'function') {
              return rawShow(values);
            }
            return !values?.id;
          },
        },
      };
    }
    if (item.fieldName !== 'userType') {
      return item;
    }
    return {
      ...item,
      componentProps: {
        ...item.componentProps,
        disabled: lockUserType,
      },
    };
  }) as typeof schema;
}

async function setFormValuesSafely(values: Partial<SystemUserApi.User>) {
  isFormInitializing.value = true;
  try {
    await formApi.setValues(values);
    previousUserType.value = values.userType;
  } finally {
    isFormInitializing.value = false;
  }
}

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 80,
  },
  async handleValuesChange(values, fieldsChanged) {
    if (isFormInitializing.value) {
      return;
    }
    if (fieldsChanged.includes('userType')) {
      const currentUserType = values.userType as number | undefined;
      if (previousUserType.value === currentUserType) {
        return;
      }
      previousUserType.value = currentUserType;
      await formApi.setFieldValue('deptId', undefined);
      await formApi.setFieldValue('roleIds', []);
      await formApi.setFieldValue('profile.professionalTitle', undefined);
      await formApi.setFieldValue('profile.gradeYear', undefined);
      await formApi.setFieldValue('profile.classIds', []);
      return;
    }
    const currentUserType = values.userType as number | undefined;
    if (
      currentUserType === 2 &&
      (fieldsChanged.includes('deptId') ||
        fieldsChanged.includes('profile.gradeYear'))
    ) {
      await formApi.setFieldValue('profile.classIds', []);
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
    // 提交表单
    const data = (await formApi.getValues()) as SystemUserApi.User;
    try {
      await (formData.value?.id ? updateUser(data) : createUserByEntry(data));
      // 关闭并提示
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
      entityName.value = undefined;
      isEditing.value = false;
      previousUserType.value = undefined;
      isFormInitializing.value = false;
      return;
    }
    // 加载数据
    const data = modalApi.getData<UserFormModalData>();
    entityName.value = data?.entityName;
    const isEditMode = !!data?.id;
    isEditing.value = isEditMode;
    const accountLabel = data?.accountLabel;
    const fixedUserType = data?.lockUserType ? data.userType : undefined;
    formApi.setState({
      schema: buildFormSchema({
        accountLabel,
        fixedUserType,
        lockUserType: !!data?.lockUserType,
        isEditMode,
      }),
    });
    if (!data) {
      return;
    }
    if (!data.id) {
      formData.value = data;
      await setFormValuesSafely(data);
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getUser(data.id);
      // 设置到 values
      await setFormValuesSafely({
        ...formData.value,
        userType: data.lockUserType ? data.userType : formData.value.userType,
      });
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-1/3">
    <Form class="mx-4" />
  </Modal>
</template>
