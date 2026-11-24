<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { EduOfficialCourseApi } from '#/api/edu/official-course';

import { computed, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Input,
  InputNumber,
  message,
  Modal,
  Tag,
  Textarea,
} from 'ant-design-vue';

import {
  ACTION_ICON,
  TableAction,
  useVbenVxeGrid,
} from '#/adapter/vxe-table';
import {
  archiveOfficialCourse,
  createOfficialCourse,
  getOfficialCoursePage,
  updateOfficialCourse,
} from '#/api/edu/official-course';

defineOptions({ name: 'EduOfficialCourse' });

// ========== 搜索表单 Schema ==========
function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '课程名称',
      component: 'Input',
      componentProps: { placeholder: '请输入课程名称', allowClear: true },
    },
    {
      fieldName: 'code',
      label: '课程编码',
      component: 'Input',
      componentProps: { placeholder: '请输入课程编码', allowClear: true },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        options: [
          { label: '正常', value: 0 },
          { label: '已归档', value: 1 },
        ],
        placeholder: '请选择状态',
        allowClear: true,
      },
    },
  ];
}

// ========== 表格列 ==========
function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    { type: 'checkbox', width: 40 },
    { field: 'code', title: '课程编码', minWidth: 120 },
    { field: 'name', title: '课程名称', minWidth: 180 },
    { field: 'credit', title: '学分', minWidth: 80 },
    { field: 'deptId', title: '归属组织', minWidth: 100 },
    {
      field: 'status',
      title: '状态',
      minWidth: 80,
      slots: { default: 'status' },
    },
    { field: 'remark', title: '备注', minWidth: 150, showOverflow: true },
    {
      field: 'createTime',
      title: '创建时间',
      minWidth: 160,
      formatter: ({ cellValue }) =>
        cellValue ? new Date(cellValue).toLocaleString('zh-CN') : '-',
    },
    {
      field: 'actions',
      title: '操作',
      width: 160,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}

// ========== 弹窗表单 ==========
const modalVisible = ref(false);
const isEdit = ref(false);
const submitting = ref(false);
const formData = reactive<EduOfficialCourseApi.OfficialCourse>({
  code: '',
  credit: 0,
  deptId: 0,
  name: '',
  remark: '',
});

function openCreate() {
  isEdit.value = false;
  Object.assign(formData, {
    id: undefined,
    code: '',
    credit: undefined,
    deptId: undefined,
    name: '',
    remark: '',
  });
  modalVisible.value = true;
}

function openEdit(row: EduOfficialCourseApi.OfficialCourse) {
  isEdit.value = true;
  Object.assign(formData, { ...row });
  modalVisible.value = true;
}

async function handleSubmit() {
  if (!formData.code?.trim()) {
    message.warning('请输入课程编码');
    return;
  }
  if (!formData.name?.trim()) {
    message.warning('请输入课程名称');
    return;
  }
  if (!formData.deptId) {
    message.warning('请输入部门 ID');
    return;
  }
  submitting.value = true;
  try {
    if (isEdit.value) {
      await updateOfficialCourse({ ...formData });
      message.success('更新成功');
    } else {
      await createOfficialCourse({ ...formData });
      message.success('创建成功');
    }
    modalVisible.value = false;
    handleRefresh();
  } finally {
    submitting.value = false;
  }
}

// ========== 表格 ==========
const tableTitle = computed(() => '正式课程列表');

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    wrapperClass: 'grid-cols-1 md:grid-cols-2 xl:grid-cols-3',
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const pageResult = await getOfficialCoursePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
          return pageResult;
        },
      },
    },
    rowConfig: { keyField: 'id', isHover: true },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<EduOfficialCourseApi.OfficialCourse>,
});

function handleRefresh() {
  gridApi.query();
}

async function handleArchive(row: EduOfficialCourseApi.OfficialCourse) {
  Modal.confirm({
    title: '归档确认',
    content: `确定要归档课程「${row.name}」吗？归档后将不可编辑。`,
    onOk: async () => {
      await archiveOfficialCourse(row.id!);
      message.success('归档成功');
      handleRefresh();
    },
  });
}
</script>

<template>
  <Page auto-content-height>
    <Modal
      v-model:open="modalVisible"
      :confirm-loading="submitting"
      :title="isEdit ? '编辑课程' : '新建课程'"
      @ok="handleSubmit"
    >
      <div class="space-y-4 py-4">
        <div>
          <label class="mb-1 block text-sm font-medium">课程编码 *</label>
          <Input v-model:value="formData.code" placeholder="请输入课程编码" />
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium">课程名称 *</label>
          <Input v-model:value="formData.name" placeholder="请输入课程名称" />
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium">学分</label>
          <InputNumber
            v-model:value="formData.credit"
            :min="0"
            :precision="1"
            class="w-full"
            placeholder="请输入学分"
          />
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium">归属组织 *</label>
          <InputNumber
            v-model:value="formData.deptId"
            :min="1"
            class="w-full"
            placeholder="请输入部门 ID"
          />
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium">备注</label>
          <Textarea
            v-model:value="formData.remark"
            :rows="3"
            placeholder="请输入备注"
          />
        </div>
      </div>
    </Modal>

    <Grid :table-title="tableTitle">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新建课程',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['edu:official-course:create'],
              onClick: openCreate,
            },
          ]"
        />
      </template>
      <template #status="{ row }">
        <Tag :color="row.status === 0 ? 'green' : 'orange'">
          {{ row.status === 0 ? '正常' : '已归档' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '编辑',
              type: 'link',
              size: 'small',
              icon: ACTION_ICON.EDIT,
              auth: ['edu:official-course:update'],
              ifShow: row.status !== 1,
              onClick: openEdit.bind(null, row),
            },
            {
              label: '归档',
              type: 'link',
              size: 'small',
              icon: 'lucide:archive',
              auth: ['edu:official-course:archive'],
              ifShow: row.status !== 1,
              onClick: handleArchive.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
