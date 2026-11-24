<script lang="ts" setup>
import type { TablePaginationConfig } from 'ant-design-vue';

import type { CapacityCourseApi } from '#/api/capacity/course';
import type { EduClassCourseApi } from '#/api/edu/class-course';
import type { EduSemesterApi } from '#/api/edu/semester';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import {
  Alert,
  Button,
  Card,
  Input,
  message,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import { getCoursePage } from '#/api/capacity/course';
import { getSimpleClassList } from '#/api/edu/class';
import {
  bindClassCourse,
  claimClassCourse,
  getClassCourseListByClass,
  getClassCourseListByCourse,
  releaseClassCourse,
  restoreClassCourse,
  unbindClassCourse,
} from '#/api/edu/class-course';
import { getSemesterOptions } from '#/api/edu/semester';
import { getSimpleUserList } from '#/api/system/user';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import { ARCHIVE_COPY, isArchived, restoredMessage } from '#/utils/archive';

defineOptions({ name: 'EduClassCourse' });

interface ClassCourseRow extends EduClassCourseApi.ClassCourse {
  id: number;
}

const userStore = useUserStore();
/** 当前用户编号；读不到时为 0 */
const currentUserId = computed(() => {
  const userInfo = userStore.userInfo as
    | undefined
    | { id?: number | string; userId?: number | string };
  return Number(userInfo?.userId ?? userInfo?.id ?? 0) || 0;
});

// ---- 筛选条件 ----
const filterClassId = ref<number | undefined>(undefined);
const filterCourseId = ref<number | undefined>(undefined);
const filterSemester = ref('');
const filterStatus = ref<'all' | 0 | 1>('all');

const statusOptions = [
  { label: '全部', value: 'all' },
  { label: '在用', value: 0 },
  { label: '已封存', value: 1 },
];

/** 是否带着筛选条件：决定表格空状态是“筛选没有结果”还是“还没有数据” */
const hasFilter = computed(
  () =>
    filterClassId.value !== undefined ||
    filterCourseId.value !== undefined ||
    Boolean(filterSemester.value.trim()) ||
    filterStatus.value !== 'all',
);
/** 未选择班级或课程时会汇总全部课程的班课关联，请求较多 */
const needFullScan = computed(
  () => filterClassId.value === undefined && filterCourseId.value === undefined,
);

// ---- 下拉选项 ----
const classOptions = ref<{ label: string; value: number }[]>([]);
const courseOptions = ref<{ label: string; value: number }[]>([]);
const semesterOptions = ref<EduSemesterApi.SemesterOption[]>([]);
const teacherOptions = ref<{ label: string; value: number }[]>([]);
/** 课程是否公共课（可认领）；查不到时按专属课处理 */
const publicCourseMap = ref<Map<number, boolean>>(new Map());

// ---- 表格数据 ----
const loading = ref(false);
const list = ref<ClassCourseRow[]>([]);
const pageNo = ref(1);
const pageSize = ref(10);

const pagination = computed<TablePaginationConfig>(() => ({
  current: pageNo.value,
  pageSize: pageSize.value,
  total: list.value.length,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
}));

function handleTableChange(pag: TablePaginationConfig) {
  pageNo.value = pag.current ?? 1;
  pageSize.value = pag.pageSize ?? 10;
}

const columns = [
  { dataIndex: 'courseName', ellipsis: true, title: '课程名', width: 180 },
  { dataIndex: 'className', ellipsis: true, title: '班级名', width: 160 },
  {
    dataIndex: 'semesterName',
    ellipsis: true,
    title: '学期',
    width: 150,
    customRender: ({ text }: { text?: string }) => text || '-',
  },
  {
    dataIndex: 'claimTeacherName',
    ellipsis: true,
    title: '认领教师',
    width: 110,
    customRender: ({ text }: { text?: string }) => text || '-',
  },
  { dataIndex: 'status', title: '状态', width: 90 },
  {
    dataIndex: 'createTime',
    title: '创建时间',
    width: 150,
    customRender: ({ text }: { text?: Date | string }) =>
      text ? dayjs(text).format('YYYY-MM-DD HH:mm') : '-',
  },
  { dataIndex: 'action', fixed: 'right' as const, title: '操作', width: 190 },
];

/** 班课状态：在用 / 已封存 */
function getStatusMeta(status?: number) {
  return isArchived(status)
    ? { label: '已封存', tone: 'neutral' as const }
    : { label: '在用', tone: 'done' as const };
}

function rowLabel(row: Record<string, any>) {
  const data = row as EduClassCourseApi.ClassCourse;
  return `${data.courseName || '课程'}·${data.className || '班级'}`;
}

function isPublicCourse(courseId?: number) {
  return publicCourseMap.value.get(Number(courseId)) === true;
}

/** 行上显示哪些操作：认领给未认领的公共课，放弃/清除认领给已认领的行 */
function rowActions(row: Record<string, any>) {
  const data = row as ClassCourseRow;
  const archived = isArchived(data.status);
  const operable = data.canOperate !== false && !archived;
  const claimed = Number(data.claimTeacherId ?? 0) > 0;
  return {
    unbind: operable,
    restore: archived && data.canOperate !== false,
    claim: operable && !claimed && isPublicCourse(data.courseId),
    release: operable && claimed && data.canRelease !== false,
    releaseIsMine: Number(data.claimTeacherId ?? 0) === currentUserId.value,
  };
}

// ---- 数据加载 ----

/** 逐页读取全部课程，供筛选、绑定弹窗与公共课判断使用 */
async function loadCourseOptions() {
  const all: CapacityCourseApi.Course[] = [];
  const pageSizeOnce = 200;
  for (let pageNoOnce = 1; ; pageNoOnce += 1) {
    const { list: pageList, total } = await getCoursePage({
      pageNo: pageNoOnce,
      pageSize: pageSizeOnce,
      includeArchived: true,
    });
    all.push(...(pageList ?? []));
    if (!pageList?.length || all.length >= Number(total || 0)) {
      break;
    }
  }
  courseOptions.value = all.map((item) => ({
    label: item.courseName,
    value: Number(item.id),
  }));
  publicCourseMap.value = new Map(
    all.map((item) => [
      Number(item.id),
      item.publicCourse ?? item.isPublicCourse === 0,
    ]),
  );
  return all;
}

/** 没有班级、课程筛选时的兜底：遍历课程汇总全部班课关联 */
async function loadAllClassCourses() {
  if (courseOptions.value.length === 0) {
    await loadCourseOptions();
  }
  const courses = courseOptions.value;
  const rows: ClassCourseRow[] = [];
  const seen = new Set<number>();
  const concurrency = 5;
  for (let i = 0; i < courses.length; i += concurrency) {
    const chunk = courses.slice(i, i + concurrency);
    const results = await Promise.all(
      chunk.map((course) =>
        getClassCourseListByCourse(course.value, true).catch(() => []),
      ),
    );
    for (const result of results) {
      for (const row of result) {
        const id = Number(row.id);
        if (id && !seen.has(id)) {
          seen.add(id);
          rows.push({ ...row, id });
        }
      }
    }
  }
  return rows;
}

async function loadList() {
  loading.value = true;
  try {
    let rows: EduClassCourseApi.ClassCourse[];
    if (filterClassId.value !== undefined) {
      // 优先单条件查询：选了班级按班级查，再在前端按课程收窄
      rows = await getClassCourseListByClass(filterClassId.value, true);
      if (filterCourseId.value !== undefined) {
        rows = rows.filter(
          (row) => Number(row.courseId) === Number(filterCourseId.value),
        );
      }
    } else if (filterCourseId.value !== undefined) {
      rows = await getClassCourseListByCourse(filterCourseId.value, true);
    } else {
      rows = await loadAllClassCourses();
    }
    const semesterKeyword = filterSemester.value.trim();
    list.value = rows
      .filter((row) => {
        if (
          semesterKeyword &&
          !(row.semesterName ?? '').includes(semesterKeyword)
        ) {
          return false;
        }
        if (filterStatus.value === 'all') {
          return true;
        }
        return filterStatus.value === 1
          ? isArchived(row.status)
          : !isArchived(row.status);
      })
      .map((row) => ({ ...row, id: Number(row.id) }));
    pageNo.value = 1;
  } finally {
    loading.value = false;
  }
}

async function loadBaseOptions() {
  const [classList, semesterList, teacherList] = await Promise.all([
    getSimpleClassList(),
    getSemesterOptions().catch(() => []),
    getSimpleUserList({ userType: 1 }),
  ]);
  classOptions.value = (classList ?? []).map((item) => ({
    label: item.className,
    value: Number(item.id),
  }));
  semesterOptions.value = semesterList ?? [];
  teacherOptions.value = (teacherList ?? []).map((item) => ({
    label: item.nickname || item.username,
    value: Number(item.id),
  }));
}

function handleSearch() {
  loadList();
}

function handleReset() {
  filterClassId.value = undefined;
  filterCourseId.value = undefined;
  filterSemester.value = '';
  filterStatus.value = 'all';
  handleSearch();
}

// ---- 行内操作 ----

async function handleUnbind(record: Record<string, any>) {
  const row = record as ClassCourseRow;
  await unbindClassCourse({ id: row.id });
  message.success(`已解绑「${rowLabel(row)}」，可随时恢复`);
  loadList();
}

async function handleRestore(record: Record<string, any>) {
  const row = record as ClassCourseRow;
  await restoreClassCourse(row.id);
  message.success(restoredMessage(rowLabel(row)));
  loadList();
}

async function handleClaim(record: Record<string, any>) {
  const row = record as ClassCourseRow;
  await claimClassCourse({
    courseId: Number(row.courseId),
    classIds: [Number(row.classId)],
    semesterName: row.semesterName || undefined,
  });
  message.success(`已认领「${rowLabel(row)}」`);
  loadList();
}

async function handleRelease(record: Record<string, any>, mine: boolean) {
  const row = record as ClassCourseRow;
  await releaseClassCourse(row.id);
  message.success(
    mine
      ? `已放弃认领「${rowLabel(row)}」`
      : `已清除「${rowLabel(row)}」的认领`,
  );
  loadList();
}

// ---- 绑定弹窗 ----
const bindOpen = ref(false);
const bindSubmitting = ref(false);
const bindForm = ref<{
  assistantTeacherIds: number[];
  classIds: number[];
  courseId?: number;
  semesterName?: string;
}>({
  assistantTeacherIds: [],
  classIds: [],
});

const semesterSelectOptions = computed(() =>
  semesterOptions.value.map((item) => ({
    label: item.current
      ? `${item.semesterName}（当前）`
      : item.semesterName,
    value: item.semesterName,
  })),
);

/** 下拉浮层挂在下拉框的父元素里，随弹窗一起销毁 */
function getModalPopupContainer(trigger: HTMLElement) {
  return trigger.parentElement ?? document.body;
}

async function handleOpenBind() {
  // 课程与班级选项缺失时补读一次（其他入口可能还没读过）
  if (courseOptions.value.length === 0) {
    await loadCourseOptions();
  }
  if (classOptions.value.length === 0 || teacherOptions.value.length === 0) {
    await loadBaseOptions();
  }
  const current = semesterOptions.value.find((item) => item.current);
  bindForm.value = {
    assistantTeacherIds: [],
    classIds: [],
    courseId: filterCourseId.value,
    semesterName:
      (current ?? semesterOptions.value[0])?.semesterName ?? undefined,
  };
  bindOpen.value = true;
  if (semesterOptions.value.length === 0) {
    message.warning(
      '还没有可选的学期：先在「学期管理」里设置当前学期，再绑定课程与班级。',
    );
  }
}

async function handleBindSubmit() {
  const form = bindForm.value;
  if (!form.courseId) {
    message.warning('请选择课程');
    return;
  }
  if (form.classIds.length === 0) {
    message.warning('请选择班级');
    return;
  }
  if (!form.semesterName) {
    message.warning('请选择学期');
    return;
  }
  const semester = semesterOptions.value.find(
    (item) => item.semesterName === form.semesterName,
  );
  bindSubmitting.value = true;
  try {
    await bindClassCourse({
      courseId: Number(form.courseId),
      classIds: [...new Set(form.classIds.map(Number))],
      semesterName: form.semesterName,
      semesterStartDate: semester?.semesterStartDate,
      semesterEndDate: semester?.semesterEndDate,
      assistantTeacherIds:
        form.assistantTeacherIds.length > 0
          ? form.assistantTeacherIds.map(Number).join(',')
          : undefined,
    });
    bindOpen.value = false;
    message.success('班级与课程已绑定');
    loadList();
  } finally {
    bindSubmitting.value = false;
  }
}

onMounted(async () => {
  loading.value = true;
  try {
    await Promise.all([loadCourseOptions(), loadBaseOptions()]);
  } finally {
    loading.value = false;
  }
  loadList();
});
</script>

<template>
  <Page
    auto-content-height
    description="统一管理课程与班级的关联：绑定、解绑、恢复与公共课认领。"
    title="班课关联管理"
  >
    <Card>
      <!-- 筛选区域 -->
      <div class="cc-toolbar">
        <Select
          v-model:value="filterClassId"
          :options="classOptions"
          allow-clear
          class="w-44"
          option-filter-prop="label"
          placeholder="全部班级"
          show-search
        />
        <Select
          v-model:value="filterCourseId"
          :options="courseOptions"
          allow-clear
          class="w-44"
          option-filter-prop="label"
          placeholder="全部课程"
          show-search
        />
        <Input
          v-model:value="filterSemester"
          allow-clear
          class="w-44"
          placeholder="学期名称"
          @press-enter="handleSearch"
        />
        <Select
          v-model:value="filterStatus"
          :options="statusOptions"
          class="w-32"
          placeholder="状态"
        />
        <Button type="primary" @click="handleSearch">查询</Button>
        <Button @click="handleReset">重置</Button>
        <Button
          v-access:code="['edu:class-course:bind']"
          class="cc-toolbar__bind"
          type="primary"
          @click="handleOpenBind"
        >
          绑定课程班级
        </Button>
      </div>
      <p v-if="needFullScan" class="cc-hint">
        未选择班级或课程时会汇总全部课程的班课关联，数据较多时建议先选择筛选条件。
      </p>

      <!-- 表格 -->
      <Table
        :columns="columns"
        :data-source="list"
        :loading="loading"
        :pagination="pagination"
        :scroll="{ x: 1020 }"
        row-key="id"
        size="middle"
        @change="handleTableChange"
      >
        <template #emptyText>
          <EmptyState
            v-if="hasFilter"
            compact
            description="换个班级、课程或学期再试，也可以重置筛选查看全部关联。"
            kind="filtered"
            title="没有符合条件的班课关联"
          >
            <template #action>
              <Button size="small" @click="handleReset">重置筛选</Button>
            </template>
          </EmptyState>
          <EmptyState
            v-else
            compact
            description="点右上角的「绑定课程班级」，把课程与班级建立关联。"
            title="还没有班课关联"
          />
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'status'">
            <StatusTag v-bind="getStatusMeta(record.status)" />
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <Space :size="0">
              <Popconfirm
                v-if="rowActions(record).claim"
                v-access:code="['edu:class-course:bind']"
                cancel-text="返回"
                description="认领后你成为这个班的任课教师，可以给班级布置任务。"
                ok-text="认领"
                :title="`认领班课「${rowLabel(record)}」？`"
                @confirm="handleClaim(record)"
              >
                <Button size="small" type="link">认领</Button>
              </Popconfirm>
              <Popconfirm
                v-if="rowActions(record).release"
                v-access:code="['edu:class-course:bind']"
                cancel-text="返回"
                :description="
                  rowActions(record).releaseIsMine
                    ? '放弃后这个班回到未认领状态，已布置的任务不受影响。'
                    : '清除后这个班回到未认领状态，已布置的任务不受影响。'
                "
                :ok-text="
                  rowActions(record).releaseIsMine ? '放弃认领' : '清除认领'
                "
                :title="
                  rowActions(record).releaseIsMine
                    ? `放弃认领班课「${rowLabel(record)}」？`
                    : `清除班课「${rowLabel(record)}」的认领？`
                "
                @confirm="
                  handleRelease(record, rowActions(record).releaseIsMine)
                "
              >
                <Button size="small" type="link">
                  {{
                    rowActions(record).releaseIsMine ? '放弃认领' : '清除认领'
                  }}
                </Button>
              </Popconfirm>
              <Popconfirm
                v-if="rowActions(record).unbind"
                v-access:code="['edu:class-course:bind']"
                cancel-text="返回"
                :description="`${ARCHIVE_COPY.archiveHint}。`"
                ok-text="解绑"
                :title="`解绑班课「${rowLabel(record)}」？`"
                @confirm="handleUnbind(record)"
              >
                <Button danger size="small" type="link">解绑</Button>
              </Popconfirm>
              <Popconfirm
                v-if="rowActions(record).restore"
                v-access:code="['edu:class-course:bind']"
                cancel-text="返回"
                description="恢复后这门课与班级的关联重新生效，可以继续布置任务。"
                ok-text="恢复"
                :title="`恢复班课「${rowLabel(record)}」？`"
                @confirm="handleRestore(record)"
              >
                <Button size="small" type="link">恢复</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
      </Table>
    </Card>

    <!-- 绑定弹窗 -->
    <Modal
      v-model:open="bindOpen"
      :confirm-loading="bindSubmitting"
      cancel-text="取消"
      destroy-on-close
      ok-text="绑定"
      title="绑定课程班级"
      @ok="handleBindSubmit"
    >
      <div class="cc-bind">
        <Alert
          v-if="semesterOptions.length === 0"
          message="还没有可选的学期：先在「学期管理」里设置当前学期，再绑定课程与班级。"
          show-icon
          type="warning"
        />
        <div class="cc-bind__field">
          <span class="cc-bind__label">课程</span>
          <Select
            v-model:value="bindForm.courseId"
            :get-popup-container="getModalPopupContainer"
            :options="courseOptions"
            class="w-full"
            option-filter-prop="label"
            placeholder="请选择课程（支持搜索）"
            show-search
          />
        </div>
        <div class="cc-bind__field">
          <span class="cc-bind__label">班级（可多选）</span>
          <Select
            v-model:value="bindForm.classIds"
            :get-popup-container="getModalPopupContainer"
            :options="classOptions"
            allow-clear
            class="w-full"
            max-tag-count="responsive"
            mode="multiple"
            option-filter-prop="label"
            placeholder="请选择班级（支持搜索）"
            show-search
          />
        </div>
        <div class="cc-bind__field">
          <span class="cc-bind__label">学期</span>
          <Select
            v-model:value="bindForm.semesterName"
            :get-popup-container="getModalPopupContainer"
            :options="semesterSelectOptions"
            class="w-full"
            placeholder="请选择学期"
          />
        </div>
        <div class="cc-bind__field">
          <span class="cc-bind__label">助教（可多选）</span>
          <Select
            v-model:value="bindForm.assistantTeacherIds"
            :get-popup-container="getModalPopupContainer"
            :options="teacherOptions"
            allow-clear
            class="w-full"
            max-tag-count="responsive"
            mode="multiple"
            option-filter-prop="label"
            placeholder="不选则不设置助教"
            show-search
          />
        </div>
      </div>
    </Modal>
  </Page>
</template>

<style scoped>
.cc-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.cc-toolbar__bind {
  margin-left: auto;
}

.cc-hint {
  margin-bottom: 12px;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.cc-bind {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.cc-bind__field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.cc-bind__label {
  font-size: var(--nus-fs-dense);
  color: var(--nus-text-secondary);
}
</style>
