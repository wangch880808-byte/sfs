<script lang="ts" setup>
import type { TablePaginationConfig } from 'ant-design-vue';

import type { EduClassApi } from '#/api/edu/class';
import type { SystemDeptApi } from '#/api/system/dept';
import type { SystemUserProfileExtApi } from '#/api/system/user-profile';

import { computed, ref } from 'vue';

import { useAccess } from '@vben/access';
import { confirm, useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { downloadFileFromBlobPart } from '@vben/utils';

import {
  Alert,
  Modal as AntModal,
  Button,
  Input,
  message,
  Select,
  Table,
  TreeSelect,
} from 'ant-design-vue';

import {
  addClassStudents,
  checkAddClassStudentRemark,
  exportClassRoster,
  getAssignedClassStudentList,
  getAvailableClassStudentPage,
  removeClassStudents,
} from '#/api/edu/class';
import { getSimpleUserProfileList } from '#/api/system/user-profile';
import ImportWizard from '#/components/data-exchange/import-wizard.vue';
import { EmptyState } from '#/components/empty-state';
import { batchConfirm } from '#/utils/confirm-copy';
import { getPageNoAfterEmptyPage } from '#/utils/pagination';

import { getDeptNameMap, getDeptTree, gradeYearOptions } from '../data';

defineOptions({ name: 'EduClassStudentManageModal' });
const emit = defineEmits(['success']);
const { hasAccessByCodes } = useAccess();
const canImportRoster = computed(() =>
  hasAccessByCodes(['edu:data-exchange:import']),
);

interface StudentRow extends EduClassApi.ClassStudent {
  deptName?: string;
}

const classInfo = ref<null | Partial<EduClassApi.Clazz>>(null);
const deptNameMap = ref<Map<number, string>>(new Map());

const availableLoading = ref(false);
const assignedLoading = ref(false);

const availableKeyword = ref('');
const assignedKeyword = ref('');
const availableDeptId = ref<number | undefined>(undefined);
const availableGradeYear = ref<number | undefined>(undefined);

const availableList = ref<StudentRow[]>([]);
const assignedList = ref<StudentRow[]>([]);
const deptTreeOptions = ref<SystemDeptApi.Dept[]>([]);

const availableSelectedIds = ref<number[]>([]);
const assignedSelectedIds = ref<number[]>([]);
const studentGradeYearMap = ref<
  Map<number, SystemUserProfileExtApi.UserProfileSimple['gradeYear']>
>(new Map());

const remarkModalOpen = ref(false);
const addRemark = ref('');
const pendingRemarkStudentIds = ref<number[]>([]);
const quickRemarks = ['留级', '转班', '借读', '插班', '跟班旁听'];

const availablePageNo = ref(1);
const availablePageSize = ref(10);
const availableTotal = ref(0);

const currentClassId = computed(() => Number(classInfo.value?.id || 0));
const currentClassDeptName = computed(
  () => deptNameMap.value.get(Number(classInfo.value?.deptId)) ?? '-',
);
const currentClassGradeYear = computed(() =>
  toNumberOrUndefined(classInfo.value?.gradeYear),
);
const pendingRemarkStudents = computed(() => {
  const selectedIds = new Set(pendingRemarkStudentIds.value.map(Number));
  return availableList.value.filter((item) => selectedIds.has(Number(item.id)));
});
const modalTitle = computed(() => {
  if (!classInfo.value?.id) {
    return '学生管理';
  }
  return `学生管理：${classInfo.value.className || ''}`;
});
/** 两侧人数：数字用中性色，已选人数跟在后面 */
function countText(total: number, selected: number) {
  return selected > 0
    ? `共 ${total} 人，已选 ${selected} 人`
    : `共 ${total} 人`;
}
const availableCountText = computed(() =>
  countText(availableTotal.value, availableSelectedIds.value.length),
);
const assignedCountText = computed(() =>
  countText(assignedList.value.length, assignedSelectedIds.value.length),
);
/** 左侧是否按关键词、组织或年级筛选：决定空状态的写法 */
const availableFiltered = computed(
  () =>
    !!availableKeyword.value ||
    availableDeptId.value !== undefined ||
    availableGradeYear.value !== undefined,
);

const tableColumns = [
  {
    dataIndex: 'username',
    ellipsis: true,
    key: 'username',
    title: '学生学号',
    width: 130,
  },
  {
    dataIndex: 'nickname',
    ellipsis: true,
    key: 'nickname',
    title: '姓名',
    width: 110,
  },
  {
    dataIndex: 'mobile',
    ellipsis: true,
    key: 'mobile',
    title: '手机号',
    width: 130,
  },
  { dataIndex: 'deptName', ellipsis: true, key: 'deptName', title: '归属组织' },
];

const availableRowSelection = computed(() => ({
  selectedRowKeys: availableSelectedIds.value,
  onChange: (keys: (number | string)[]) => {
    availableSelectedIds.value = keys.map(Number);
  },
}));

const assignedRowSelection = computed(() => ({
  selectedRowKeys: assignedSelectedIds.value,
  onChange: (keys: (number | string)[]) => {
    assignedSelectedIds.value = keys.map(Number);
  },
}));

const availablePagination = computed<TablePaginationConfig>(() => ({
  current: availablePageNo.value,
  pageSize: availablePageSize.value,
  total: availableTotal.value,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
}));

function mapRows(rows: EduClassApi.ClassStudent[]) {
  return (rows || []).map((row) => ({
    ...row,
    deptName: deptNameMap.value.get(Number(row.deptId)) ?? '-',
  }));
}

async function loadDeptMap() {
  deptNameMap.value = await getDeptNameMap();
  deptTreeOptions.value = await getDeptTree();
}

async function loadStudentGradeYearMap() {
  const map = new Map<
    number,
    SystemUserProfileExtApi.UserProfileSimple['gradeYear']
  >();
  const list = await getSimpleUserProfileList({ userType: 2 });
  (list || []).forEach((item) => {
    map.set(Number(item.userId), item.gradeYear);
  });
  studentGradeYearMap.value = map;
}

function toNumberOrUndefined(value: unknown) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : undefined;
}

function initDefaultFilters() {
  availableDeptId.value = toNumberOrUndefined(classInfo.value?.deptId);
  availableGradeYear.value = toNumberOrUndefined(classInfo.value?.gradeYear);
}

async function loadAvailableStudents(
  pageNo = 1,
  pageSize = availablePageSize.value,
) {
  if (!currentClassId.value) {
    return;
  }
  availableLoading.value = true;
  try {
    const { list, total } = await getAvailableClassStudentPage({
      classId: currentClassId.value,
      keyword: availableKeyword.value || undefined,
      deptId: availableDeptId.value,
      gradeYear: availableGradeYear.value,
      pageNo,
      pageSize,
    });
    availableList.value = mapRows(list || []);
    availablePageNo.value = pageNo;
    availablePageSize.value = pageSize;
    availableTotal.value = Number(total || 0);
    availableSelectedIds.value = [];
  } finally {
    availableLoading.value = false;
  }
  // 把这一页的学生都加入本班后，这一页就空了：回退到还有学生的页重新读取
  const fallbackPageNo = getPageNoAfterEmptyPage({
    listLength: availableList.value.length,
    pageNo: availablePageNo.value,
    pageSize: availablePageSize.value,
    total: availableTotal.value,
  });
  if (fallbackPageNo !== undefined) {
    await loadAvailableStudents(fallbackPageNo, availablePageSize.value);
  }
}

async function loadAssignedStudents() {
  if (!currentClassId.value) {
    return;
  }
  assignedLoading.value = true;
  try {
    const list = await getAssignedClassStudentList({
      classId: currentClassId.value,
      keyword: assignedKeyword.value || undefined,
    });
    assignedList.value = mapRows(list || []);
    assignedSelectedIds.value = [];
  } finally {
    assignedLoading.value = false;
  }
}

async function refreshData() {
  await Promise.all([
    loadAvailableStudents(availablePageNo.value),
    loadAssignedStudents(),
  ]);
}

function resetData() {
  classInfo.value = null;
  availableKeyword.value = '';
  assignedKeyword.value = '';
  availableDeptId.value = undefined;
  availableGradeYear.value = undefined;
  availableList.value = [];
  assignedList.value = [];
  deptTreeOptions.value = [];
  studentGradeYearMap.value = new Map();
  availableSelectedIds.value = [];
  assignedSelectedIds.value = [];
  remarkModalOpen.value = false;
  addRemark.value = '';
  pendingRemarkStudentIds.value = [];
  availablePageNo.value = 1;
  availablePageSize.value = 10;
  availableTotal.value = 0;
}

async function submitAddStudents(studentIds: number[], remark?: string) {
  if (!currentClassId.value || studentIds.length === 0) {
    return;
  }
  await addClassStudents({
    classId: currentClassId.value,
    studentIds,
    remark,
  });
  message.success('已添加到本班');
  remarkModalOpen.value = false;
  addRemark.value = '';
  pendingRemarkStudentIds.value = [];
  await refreshData();
  emit('success');
}

async function handleAddStudents() {
  if (!currentClassId.value) {
    return;
  }
  if (availableSelectedIds.value.length === 0) {
    message.warning('请先选择要添加的学生');
    return;
  }
  const selectedIds = [...availableSelectedIds.value];
  const needRemark = await checkAddClassStudentRemark({
    classId: currentClassId.value,
    studentIds: selectedIds,
  });
  if (needRemark) {
    pendingRemarkStudentIds.value = selectedIds;
    addRemark.value = '';
    remarkModalOpen.value = true;
    return;
  }
  await submitAddStudents(selectedIds);
}

async function handleRemoveStudents() {
  if (!currentClassId.value) {
    return;
  }
  if (assignedSelectedIds.value.length === 0) {
    message.warning('请先选择要移除的学生');
    return;
  }
  await confirm(
    batchConfirm(
      '移除',
      '学生',
      assignedSelectedIds.value.length,
      `移除后这些学生不再属于「${classInfo.value?.className || '本班'}」，可以随时从左侧重新添加。`,
      '名',
    ),
  );
  await removeClassStudents({
    classId: currentClassId.value,
    studentIds: assignedSelectedIds.value,
  });
  message.success('已从本班移除');
  await refreshData();
  emit('success');
}

async function handleAvailableSearch() {
  await loadAvailableStudents(1, availablePageSize.value);
}

async function handleAvailableReset() {
  initDefaultFilters();
  availableKeyword.value = '';
  await loadAvailableStudents(1, availablePageSize.value);
}

async function handleAssignedSearch() {
  await loadAssignedStudents();
}

async function handleAvailableTableChange(pagination: TablePaginationConfig) {
  const pageNo = Number(pagination.current || 1);
  const pageSize = Number(pagination.pageSize || availablePageSize.value);
  await loadAvailableStudents(pageNo, pageSize);
}

const importModalOpen = ref(false);

async function handleExportRoster() {
  if (!currentClassId.value) return;
  const source = await exportClassRoster(currentClassId.value);
  downloadFileFromBlobPart({
    fileName: `${classInfo.value?.className || '班级'}花名册.xlsx`,
    source,
  });
}

async function handleImportSuccess() {
  await refreshData();
  emit('success');
}

async function handleClose() {
  await modalApi.close();
}

function handleQuickRemarkClick(remark: string) {
  if (addRemark.value.includes(remark)) {
    return;
  }
  addRemark.value = addRemark.value ? `${addRemark.value}；${remark}` : remark;
}

async function handleConfirmRemark() {
  if (pendingRemarkStudentIds.value.length === 0) {
    remarkModalOpen.value = false;
    return;
  }
  if (!addRemark.value.trim()) {
    message.warning('请填写备注说明');
    return;
  }
  await submitAddStudents(
    pendingRemarkStudentIds.value,
    addRemark.value.trim(),
  );
}

function handleCancelRemark() {
  remarkModalOpen.value = false;
  addRemark.value = '';
  pendingRemarkStudentIds.value = [];
}

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      resetData();
      return;
    }
    const data = modalApi.getData<Partial<EduClassApi.Clazz>>();
    if (!data?.id) {
      return;
    }
    classInfo.value = data;
    initDefaultFilters();
    modalApi.lock();
    try {
      await loadDeptMap();
      await loadStudentGradeYearMap();
      await refreshData();
    } finally {
      modalApi.unlock();
    }
  },
  destroyOnClose: true,
});
</script>

<template>
  <Modal :title="modalTitle" class="w-[90%]">
    <div class="roster">
      <section class="roster-pane" aria-labelledby="roster-available-title">
        <header class="roster-pane__header">
          <h3 id="roster-available-title" class="roster-pane__title">
            可添加的学生
          </h3>
          <span class="roster-pane__count">{{ availableCountText }}</span>
        </header>
        <div class="roster-pane__filters">
          <Input
            v-model:value="availableKeyword"
            placeholder="学号、姓名或手机号"
            allow-clear
            @press-enter="handleAvailableSearch"
          >
            <template #prefix>
              <IconifyIcon icon="lucide:search" class="roster-icon" />
            </template>
          </Input>
          <TreeSelect
            v-model:value="availableDeptId"
            :tree-data="deptTreeOptions"
            :field-names="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="全部组织"
            allow-clear
            tree-default-expand-all
          />
          <Select
            v-model:value="availableGradeYear"
            :options="gradeYearOptions"
            placeholder="全部年级"
            allow-clear
          />
          <div class="roster-pane__filter-actions">
            <Button @click="handleAvailableReset">重置</Button>
            <Button @click="handleAvailableSearch">查询</Button>
          </div>
        </div>
        <Table
          row-key="id"
          size="small"
          :loading="{
            spinning: availableLoading,
            tip: '正在读取可添加的学生…',
          }"
          :columns="tableColumns"
          :data-source="availableList"
          :row-selection="availableRowSelection"
          :pagination="availablePagination"
          :scroll="{ y: 420 }"
          @change="handleAvailableTableChange"
        >
          <template #emptyText>
            <EmptyState
              v-if="!availableLoading"
              compact
              :description="
                availableFiltered
                  ? '换个关键词，或清空组织和年级后再查询。'
                  : '新建或导入学生账号后，再回来添加到本班。'
              "
              :kind="availableFiltered ? 'filtered' : 'empty'"
              :title="
                availableFiltered ? '没有符合条件的学生' : '没有可添加的学生'
              "
            />
          </template>
        </Table>
      </section>

      <div class="roster-transfer">
        <Button
          v-access:code="['edu:class:update']"
          type="primary"
          :disabled="availableSelectedIds.length === 0"
          @click="handleAddStudents"
        >
          添加
          <IconifyIcon icon="lucide:arrow-right" class="roster-icon" />
        </Button>
        <Button
          v-access:code="['edu:class:update']"
          :disabled="assignedSelectedIds.length === 0"
          @click="handleRemoveStudents"
        >
          <IconifyIcon icon="lucide:arrow-left" class="roster-icon" />
          移除
        </Button>
      </div>

      <section class="roster-pane" aria-labelledby="roster-assigned-title">
        <header class="roster-pane__header">
          <h3 id="roster-assigned-title" class="roster-pane__title">
            已在本班的学生
          </h3>
          <span class="roster-pane__count">{{ assignedCountText }}</span>
        </header>
        <div class="roster-pane__filters roster-pane__filters--single">
          <Input.Search
            v-model:value="assignedKeyword"
            placeholder="学号、姓名或手机号"
            allow-clear
            @search="handleAssignedSearch"
          />
        </div>
        <Table
          row-key="id"
          size="small"
          :loading="{ spinning: assignedLoading, tip: '正在读取本班学生…' }"
          :columns="tableColumns"
          :data-source="assignedList"
          :row-selection="assignedRowSelection"
          :pagination="false"
          :scroll="{ y: 420 }"
        >
          <template #emptyText>
            <EmptyState
              v-if="!assignedLoading"
              compact
              :description="
                assignedKeyword
                  ? '换个关键词再查询。'
                  : '在左侧勾选学生后点「添加」，或导入花名册。'
              "
              :kind="assignedKeyword ? 'filtered' : 'empty'"
              :title="
                assignedKeyword ? '没有符合条件的学生' : '班级里还没有学生'
              "
            />
          </template>
        </Table>
      </section>
    </div>

    <AntModal
      v-model:open="remarkModalOpen"
      title="填写备注后添加"
      ok-text="添加"
      cancel-text="返回"
      @ok="handleConfirmRemark"
      @cancel="handleCancelRemark"
    >
      <Alert
        class="remark-alert"
        message="所选学生与本班的年级或专业不一致，填写备注后才能添加。"
        show-icon
        type="warning"
      />
      <p class="remark-meta">
        本班：{{ currentClassGradeYear || '-' }} 级 ·
        {{ currentClassDeptName }}
      </p>
      <ul class="remark-students">
        <li
          v-for="student in pendingRemarkStudents"
          :key="student.id"
          class="remark-students__item"
        >
          <span>{{ student.nickname }}（{{ student.username }}）</span>
          <span class="remark-students__meta">
            {{ studentGradeYearMap.get(Number(student.id)) || '-' }} 级 ·
            {{ student.deptName || '-' }}
          </span>
        </li>
      </ul>
      <div class="remark-quick">
        <span class="remark-quick__label">常用备注</span>
        <Button
          v-for="item in quickRemarks"
          :key="item"
          size="small"
          @click="handleQuickRemarkClick(item)"
        >
          {{ item }}
        </Button>
      </div>
      <Input.TextArea
        v-model:value="addRemark"
        placeholder="请输入备注说明"
        :maxlength="500"
        :rows="4"
        show-count
      />
    </AntModal>

    <ImportWizard
      v-if="canImportRoster"
      v-model:open="importModalOpen"
      biz-type="CLASS_ROSTER"
      :options="{ classId: currentClassId }"
      template-file-name="班级花名册导入模板.xlsx"
      :title="`导入花名册：${classInfo?.className || ''}`"
      @success="handleImportSuccess"
    />

    <template #footer>
      <div class="roster-footer">
        <div class="roster-footer__secondary">
          <Button @click="handleExportRoster">
            <IconifyIcon icon="lucide:download" class="roster-icon" />
            导出花名册
          </Button>
          <Button v-if="canImportRoster" @click="importModalOpen = true">
            <IconifyIcon icon="lucide:upload" class="roster-icon" />
            导入花名册
          </Button>
        </div>
        <Button @click="handleClose">关闭</Button>
      </div>
    </template>
  </Modal>
</template>

<style scoped>
.roster {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: var(--nus-space-4);
  align-items: stretch;
}

.roster-pane {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-3);
  min-width: 0;
  padding: var(--nus-space-4);
  background: var(--nus-bg-surface);
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius-lg);
}

.roster-pane__header {
  display: flex;
  gap: var(--nus-space-2);
  align-items: baseline;
  justify-content: space-between;
}

.roster-pane__title {
  margin: 0;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
}

.roster-pane__count {
  font-size: var(--nus-fs-dense);
  font-variant-numeric: tabular-nums;
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
  white-space: nowrap;
}

.roster-pane__filters {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--nus-space-2);
}

.roster-pane__filters--single {
  grid-template-columns: minmax(0, 1fr);
}

.roster-pane__filter-actions {
  display: flex;
  gap: var(--nus-space-2);
  justify-content: flex-end;
}

.roster-transfer {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-3);
  justify-content: center;
}

.roster-icon {
  width: 14px;
  height: 14px;
  vertical-align: -2px;
}

.roster-footer {
  display: flex;
  flex: 1;
  gap: var(--nus-space-2);
  align-items: center;
  justify-content: space-between;
}

.roster-footer__secondary {
  display: flex;
  gap: var(--nus-space-2);
}

.remark-alert {
  margin-bottom: var(--nus-space-3);
}

.remark-meta {
  margin: 0 0 var(--nus-space-2);
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.remark-students {
  max-height: 112px;
  padding: var(--nus-space-2) var(--nus-space-3);
  margin: 0 0 var(--nus-space-3);
  overflow: auto;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  list-style: none;
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius);
}

.remark-students__item {
  display: flex;
  gap: var(--nus-space-2);
  justify-content: space-between;
  padding: 2px 0;
  color: var(--nus-text);
}

.remark-students__meta {
  color: var(--nus-text-tertiary);
  white-space: nowrap;
}

.remark-quick {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2);
  align-items: center;
  margin-bottom: var(--nus-space-2);
}

.remark-quick__label {
  font-size: var(--nus-fs-caption);
  color: var(--nus-text-tertiary);
}
</style>
