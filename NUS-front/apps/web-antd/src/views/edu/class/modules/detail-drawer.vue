<script lang="ts" setup>
import type { EduClassApi } from '#/api/edu/class';
import type { EduClassCourseApi } from '#/api/edu/class-course';

import { computed, ref, watch } from 'vue';

import { getDictLabel } from '@vben/hooks';

import {
  Descriptions,
  DescriptionsItem,
  Drawer,
  Spin,
  Table,
  Tag,
} from 'ant-design-vue';

import { getAssignedClassStudentList } from '#/api/edu/class';
import { getClassCourseListByClass } from '#/api/edu/class-course';
import { getSimpleUserList } from '#/api/system/user';
import { StatusTag } from '#/components/status-tag';
import { isArchived } from '#/utils/archive';

defineOptions({ name: 'EduClassDetailDrawer' });

interface ClassRow extends EduClassApi.Clazz {
  deptName?: string;
}

const props = defineProps<{
  open: boolean;
  row: ClassRow | null;
}>();
const emit = defineEmits<{ (e: 'update:open', value: boolean): void }>();

const EDU_EDUCATION_LEVEL_DICT = 'edu_education_level';

const loading = ref(false);
const classCourses = ref<EduClassCourseApi.ClassCourse[]>([]);
const studentCount = ref(0);
const teacherNameMap = ref<Map<number, string>>(new Map());

const courseColumns = [
  { dataIndex: 'courseName', ellipsis: true, title: '课程', width: 180 },
  {
    dataIndex: 'semesterName',
    ellipsis: true,
    title: '学期',
    width: 140,
    customRender: ({ text }: { text?: string }) => text || '-',
  },
  { dataIndex: 'status', title: '状态', width: 90 },
];

/** 班课状态：在用 / 已封存 */
function getStatusMeta(status?: number) {
  return isArchived(status)
    ? { label: '已封存', tone: 'neutral' as const }
    : { label: '在用', tone: 'done' as const };
}

function parseIdList(value?: string) {
  return (value ?? '')
    .split(',')
    .map((item) => Number(item.trim()))
    .filter((id) => Number.isFinite(id) && id > 0);
}

/** 任课教师：认领教师 + 助教（从班课数据的 assistantTeacherIds 映射姓名） */
const teacherLines = computed(() =>
  classCourses.value.map((row) => {
    const parts: string[] = [];
    const claimName = row.claimTeacherName?.trim();
    if (claimName) {
      parts.push(`${claimName}（认领）`);
    }
    const assistantNames = parseIdList(row.assistantTeacherIds)
      .map((id) => teacherNameMap.value.get(id))
      .filter((name): name is string => !!name);
    if (assistantNames.length > 0) {
      parts.push(`${assistantNames.join('、')}（助教）`);
    }
    return {
      classCourseId: Number(row.id),
      courseName: row.courseName || '-',
      teacherText: parts.length > 0 ? parts.join('、') : '未设置',
    };
  }),
);

async function loadDetail() {
  const classId = Number(props.row?.id || 0);
  if (!classId) {
    return;
  }
  loading.value = true;
  try {
    const [courses, students, teachers] = await Promise.all([
      getClassCourseListByClass(classId, true),
      getAssignedClassStudentList({ classId }).catch(() => []),
      getSimpleUserList({ userType: 1 }).catch(() => []),
    ]);
    classCourses.value = courses ?? [];
    studentCount.value = (students ?? []).length;
    teacherNameMap.value = new Map(
      (teachers ?? []).map((item) => [Number(item.id), item.nickname]),
    );
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      loadDetail();
    }
  },
);

function handleClose() {
  emit('update:open', false);
}
</script>

<template>
  <Drawer
    :open="props.open"
    :title="`班级详情：${props.row?.className || ''}`"
    :width="560"
    @update:open="handleClose"
  >
    <Spin :spinning="loading" tip="正在读取班级详情…">
      <section class="detail-section">
        <h3 class="detail-section__title">基本信息</h3>
        <Descriptions :column="2" size="small">
          <DescriptionsItem label="班级名称">
            {{ props.row?.className || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="所属年级">
            {{ props.row?.gradeYear ? `${props.row.gradeYear} 级` : '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="归属组织">
            {{ props.row?.deptName || '-' }}
          </DescriptionsItem>
          <DescriptionsItem label="教育层次">
            {{
              getDictLabel(EDU_EDUCATION_LEVEL_DICT, props.row?.educationLevel) ||
              '-'
            }}
          </DescriptionsItem>
          <DescriptionsItem label="学制">
            {{
              props.row?.schoolingYear ? `${props.row.schoolingYear} 年` : '-'
            }}
          </DescriptionsItem>
        </Descriptions>
      </section>

      <section class="detail-section">
        <h3 class="detail-section__title">班课关联</h3>
        <Table
          :columns="courseColumns"
          :data-source="classCourses"
          :pagination="false"
          row-key="id"
          size="small"
        >
          <template #emptyText>
            <span class="detail-empty">还没有绑定任何课程</span>
          </template>
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'status'">
              <StatusTag v-bind="getStatusMeta(record.status)" />
            </template>
          </template>
        </Table>
      </section>

      <section class="detail-section">
        <h3 class="detail-section__title">学生数</h3>
        <p class="detail-text">共 {{ studentCount }} 名学生</p>
      </section>

      <section class="detail-section">
        <h3 class="detail-section__title">任课教师</h3>
        <ul v-if="teacherLines.length > 0" class="detail-teachers">
          <li
            v-for="line in teacherLines"
            :key="line.classCourseId"
            class="detail-teachers__item"
          >
            <Tag class="detail-teachers__course">{{ line.courseName }}</Tag>
            <span class="detail-teachers__names">{{ line.teacherText }}</span>
          </li>
        </ul>
        <p v-else class="detail-text">还没有绑定课程，暂无任课教师</p>
      </section>
    </Spin>
  </Drawer>
</template>

<style scoped>
.detail-section {
  margin-bottom: 20px;
}

.detail-section__title {
  margin-bottom: 8px;
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.detail-text {
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.detail-empty {
  font-size: var(--nus-fs-dense);
  color: var(--nus-text-tertiary);
}

.detail-teachers {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.detail-teachers__item {
  display: flex;
  gap: 8px;
  align-items: baseline;
}

.detail-teachers__course {
  flex-shrink: 0;
  margin: 0;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-teachers__names {
  font-size: var(--nus-fs-dense);
  color: var(--nus-text-secondary);
}
</style>
