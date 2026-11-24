<script lang="ts" setup>
import type { EduCourseResourceApi } from '#/api/edu/course-resource';

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
  Table,
  Tag,
} from 'ant-design-vue';

import { getCoursePage } from '#/api/capacity/course';
import {
  getCourseResourceReviewPage,
  reviewCourseResource,
} from '#/api/edu/course-resource';
import { EmptyState } from '#/components/empty-state';

defineOptions({ name: 'EduCourseResourceReview' });

const loading = ref(false);
const dataList = ref<EduCourseResourceApi.CourseResource[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(10);

// 课程名称映射
const courseNames = ref<Map<number, string>>(new Map());

const columns = [
  { dataIndex: 'resourceName', title: '标题', width: 260 },
  { dataIndex: 'courseName', title: '课程', width: 200 },
  { dataIndex: 'reviewStatus', title: '状态', width: 100 },
  { dataIndex: 'action', title: '操作', width: 150, fixed: 'right' as const },
];

const courseNameOf = (courseId: number) =>
  courseNames.value.get(courseId) ?? '-';

async function loadCourses() {
  const result = await getCoursePage({ pageNo: 1, pageSize: 500 });
  const map = new Map<number, string>();
  for (const item of result.list ?? []) {
    map.set(Number(item.id), item.courseName);
  }
  courseNames.value = map;
}

async function loadData() {
  loading.value = true;
  try {
    const { list, total: t } = await getCourseResourceReviewPage({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    });
    dataList.value = list ?? [];
    total.value = Number(t || 0);
  } finally {
    loading.value = false;
  }
}

async function handleApprove(id: number) {
  await reviewCourseResource(id, { approved: true });
  message.success('已通过该资源');
  loadData();
}

// 驳回弹窗
const rejectModalOpen = ref(false);
const rejecting = ref(false);
const rejectReason = ref('');
const rejectingId = ref<number>();

function openRejectModal(record: EduCourseResourceApi.CourseResource) {
  rejectingId.value = record.id;
  rejectReason.value = '';
  rejectModalOpen.value = true;
}

async function handleRejectOk() {
  const reason = rejectReason.value.trim();
  if (!reason) {
    message.warning('请填写驳回原因');
    return;
  }
  if (!rejectingId.value) {
    return;
  }
  rejecting.value = true;
  try {
    await reviewCourseResource(rejectingId.value, {
      approved: false,
      reason,
    });
    message.success('已驳回该资源');
    rejectModalOpen.value = false;
    loadData();
  } finally {
    rejecting.value = false;
  }
}

onMounted(async () => {
  await loadCourses();
  loadData();
});
</script>

<template>
  <Page
    auto-content-height
    description="审核教师上传的课程资源，通过后学生才能查看。"
    title="课程资源审核"
  >
    <Card>
      <!-- 表格 -->
      <Table
        :columns="columns"
        :data-source="dataList"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 800 }"
        row-key="id"
        size="middle"
      >
        <template #emptyText>
          <EmptyState
            compact
            description="教师上传新资源后，会显示在这里等待审核。"
            title="暂无待审核资源"
          />
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'courseName'">
            {{ courseNameOf(record.courseId) }}
          </template>
          <template v-if="column.dataIndex === 'reviewStatus'">
            <Tag color="orange">待审核</Tag>
          </template>
          <template v-if="column.dataIndex === 'action'">
            <Popconfirm
              :title="`通过资源「${record.resourceName}」？`"
              cancel-text="返回"
              description="通过后学生可以在课程详情里查看该资源。"
              ok-text="通过"
              @confirm="handleApprove(record.id)"
            >
              <Button size="small" type="link">通过</Button>
            </Popconfirm>
            <Button
              danger
              size="small"
              type="link"
              @click="openRejectModal(record as EduCourseResourceApi.CourseResource)"
            >
              驳回
            </Button>
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

    <!-- 驳回弹窗 -->
    <Modal
      v-model:open="rejectModalOpen"
      :confirm-loading="rejecting"
      cancel-text="返回"
      ok-text="确认驳回"
      title="驳回课程资源"
      @ok="handleRejectOk"
    >
      <Form layout="vertical">
        <FormItem label="驳回原因" required>
          <Input.TextArea
            v-model:value="rejectReason"
            :maxlength="500"
            :rows="4"
            placeholder="请填写驳回原因，上传教师可以看到"
          />
        </FormItem>
      </Form>
    </Modal>
  </Page>
</template>
