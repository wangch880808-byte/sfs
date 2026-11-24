<script lang="ts" setup>
import type { EduClassRequestApi } from '#/api/edu/class-request';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Button,
  Card,
  Form,
  FormItem,
  Input,
  message,
  Modal,
  Pagination,
  Popconfirm,
  Select,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  approveClassRequest,
  getClassRequestPage,
  rejectClassRequest,
} from '#/api/edu/class-request';
import { EmptyState } from '#/components/empty-state';

defineOptions({ name: 'EduClassRequest' });

const loading = ref(false);
const dataList = ref<EduClassRequestApi.ClassRequest[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(10);

// 筛选条件
const filterStatus = ref<number | undefined>();

const statusOptions = [
  { label: '待审核', value: 0 },
  { label: '已通过', value: 1 },
  { label: '已拒绝', value: 2 },
];

const statusTextMap: Record<number, string> = {
  0: '待审核',
  1: '已通过',
  2: '已拒绝',
};

const columns = [
  { dataIndex: 'courseName', title: '课程', width: 180 },
  { dataIndex: 'className', title: '班级', width: 150 },
  { dataIndex: 'applicantName', title: '申请人', width: 120 },
  { dataIndex: 'createTime', title: '申请时间', width: 170 },
  { dataIndex: 'status', title: '状态', width: 100 },
  { dataIndex: 'action', title: '操作', width: 150, fixed: 'right' as const },
];

async function loadData() {
  loading.value = true;
  try {
    const { list, total: t } = await getClassRequestPage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      status: filterStatus.value,
    });
    dataList.value = list ?? [];
    total.value = Number(t || 0);
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pageNo.value = 1;
  loadData();
}

function handleReset() {
  filterStatus.value = undefined;
  handleSearch();
}

async function handleApprove(id: number) {
  await approveClassRequest(id);
  message.success('已通过，班课关联已创建');
  loadData();
}

// 拒绝弹窗
const rejectModalOpen = ref(false);
const rejecting = ref(false);
const rejectReason = ref('');
const rejectingId = ref<number>();

function openRejectModal(record: EduClassRequestApi.ClassRequest) {
  rejectingId.value = record.id;
  rejectReason.value = '';
  rejectModalOpen.value = true;
}

async function handleRejectOk() {
  const reason = rejectReason.value.trim();
  if (!reason) {
    message.warning('请填写拒绝原因');
    return;
  }
  if (!rejectingId.value) {
    return;
  }
  rejecting.value = true;
  try {
    await rejectClassRequest(rejectingId.value, reason);
    message.success('已拒绝该建班申请');
    rejectModalOpen.value = false;
    loadData();
  } finally {
    rejecting.value = false;
  }
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <Page
    auto-content-height
    description="审核教师提交的建班申请，通过后自动创建班课关联。"
    title="建班审核"
  >
    <Card>
      <!-- 筛选区域 -->
      <div class="cr-toolbar">
        <Select
          v-model:value="filterStatus"
          :options="statusOptions"
          allow-clear
          class="w-40"
          placeholder="审核状态"
        />
        <Button type="primary" @click="handleSearch">查询</Button>
        <Button @click="handleReset">重置</Button>
      </div>

      <!-- 表格 -->
      <Table
        :columns="columns"
        :data-source="dataList"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 900 }"
        row-key="id"
        size="middle"
      >
        <template #emptyText>
          <EmptyState
            compact
            description="教师提交建班申请后，会显示在这里等待审核。"
            title="暂无建班申请"
          />
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'status'">
            <Tag
              :color="record.status === 1 ? 'green' : record.status === 2 ? 'red' : 'orange'"
            >
              {{ statusTextMap[record.status] ?? '未知' }}
            </Tag>
          </template>
          <template v-if="column.dataIndex === 'action'">
            <template v-if="record.status === 0">
              <Popconfirm
                :title="`通过「${record.className || '该班级'}」的建班申请？`"
                cancel-text="返回"
                description="通过后会自动创建班课关联。"
                ok-text="通过"
                @confirm="handleApprove(record.id)"
              >
                <Button size="small" type="link">通过</Button>
              </Popconfirm>
              <Button danger size="small" type="link" @click="openRejectModal(record as EduClassRequestApi.ClassRequest)">
                拒绝
              </Button>
            </template>
            <span v-else class="cr-action-done">
              {{ record.status === 1 ? '已通过' : '已拒绝' }}
            </span>
          </template>
        </template>
      </Table>
      <div class="mt-4 flex justify-end">
        <Pagination
          v-model:current="pageNo"
          v-model:page-size="pageSize"
          :show-total="(t: number) => `共 ${t} 条`"
          :total="total"
          show-size-changer
          @change="loadData"
        />
      </div>
    </Card>

    <!-- 拒绝弹窗 -->
    <Modal
      v-model:open="rejectModalOpen"
      :confirm-loading="rejecting"
      cancel-text="返回"
      ok-text="确认拒绝"
      title="拒绝建班申请"
      @ok="handleRejectOk"
    >
      <Form layout="vertical">
        <FormItem label="拒绝原因" required>
          <Input.TextArea
            v-model:value="rejectReason"
            :maxlength="500"
            :rows="4"
            placeholder="请填写拒绝原因，申请人可以看到"
          />
        </FormItem>
      </Form>
    </Modal>
  </Page>
</template>

<style scoped>
.cr-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 16px;
}

.cr-action-done {
  font-size: 12px;
  color: var(--nus-text-tertiary, #999);
}
</style>
