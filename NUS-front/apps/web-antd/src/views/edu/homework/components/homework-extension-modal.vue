<script lang="ts" setup>
import type { Dayjs } from 'dayjs';

import type { EduHomeworkDeadlineApi } from '#/api/edu/homework/deadline';
import type { DeadlineValue } from '#/utils/homework-deadline';

import { computed, ref, watch } from 'vue';

import {
  Alert,
  Button,
  DatePicker,
  Input,
  message,
  Modal,
  Popconfirm,
  Select,
  Table,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import {
  deleteHomeworkExtension,
  getHomeworkExtensionList,
  getHomeworkExtensionStudents,
  setHomeworkExtensions,
} from '#/api/edu/homework/deadline';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import { rowConfirm } from '#/utils/confirm-copy';
import { formatDeadlineTime } from '#/utils/homework-deadline';
import {
  getHomeworkRecordStatusMeta,
  HOMEWORK_RECORD_STATUS,
} from '#/utils/status';

import {
  EXTENSION_DEADLINE_FORMAT,
  EXTENSION_REASON_MAX,
  formatStudentName,
  getDefaultExtensionDeadline,
  getExtensionCandidates,
  validateExtensionDeadline,
} from './extension-display';

defineOptions({ name: 'HomeworkExtensionModal' });

const props = defineProps<{
  homeworkDeadline?: DeadlineValue;
  homeworkId?: number;
  homeworkName?: string;
}>();

const emit = defineEmits<{ changed: [count: number] }>();

const open = defineModel<boolean>('open', { default: false });

const loading = ref(false);
const loadError = ref(false);
const saving = ref(false);
const removingId = ref<number>();
const students = ref<EduHomeworkDeadlineApi.Student[]>([]);
const extensions = ref<EduHomeworkDeadlineApi.Extension[]>([]);

const selectedStudentIds = ref<number[]>([]);
const deadline = ref<string>();
const reason = ref('');

const candidateOptions = computed(() => getExtensionCandidates(students.value));

const modalTitle = computed(() =>
  props.homeworkName ? `个别延期 · ${props.homeworkName}` : '个别延期',
);

const taskDeadlineText = computed(() =>
  formatDeadlineTime(props.homeworkDeadline),
);

const extensionColumns = [
  { title: '学生', key: 'student' },
  { title: '个人截止时间', key: 'deadline', width: 150 },
  { title: '原因', dataIndex: 'reason', key: 'reason', ellipsis: true },
  { title: '状态', key: 'status', width: 100 },
  { title: '操作', key: 'action', width: 72 },
];

function resetForm() {
  selectedStudentIds.value = [];
  deadline.value = getDefaultExtensionDeadline(props.homeworkDeadline);
  reason.value = '';
}

async function loadData() {
  if (!props.homeworkId) {
    return;
  }
  loading.value = true;
  loadError.value = false;
  try {
    const [studentList, extensionList] = await Promise.all([
      getHomeworkExtensionStudents(props.homeworkId, true),
      getHomeworkExtensionList(props.homeworkId, true),
    ]);
    students.value = studentList ?? [];
    extensions.value = extensionList ?? [];
  } catch {
    loadError.value = true;
  } finally {
    loading.value = false;
  }
}

watch(open, (value) => {
  if (value) {
    resetForm();
    void loadData();
  }
});

function selectAllCandidates() {
  selectedStudentIds.value = candidateOptions.value.map((item) => item.value);
}

function disabledDate(current: Dayjs) {
  return current.isBefore(dayjs(), 'day');
}

async function handleSave() {
  if (!props.homeworkId) {
    return;
  }
  if (selectedStudentIds.value.length === 0) {
    message.warning('请先选择要延期的学生');
    return;
  }
  const problem = validateExtensionDeadline(
    deadline.value,
    props.homeworkDeadline,
  );
  if (problem) {
    message.warning(problem);
    return;
  }
  saving.value = true;
  try {
    const count = await setHomeworkExtensions({
      deadline: deadline.value as string,
      homeworkId: props.homeworkId,
      reason: reason.value.trim() || undefined,
      studentIds: selectedStudentIds.value,
    });
    message.success(
      `已为 ${count} 名学生延期到 ${formatDeadlineTime(deadline.value)}，并通知了学生`,
    );
    resetForm();
    await loadData();
    emit('changed', extensions.value.length);
  } finally {
    saving.value = false;
  }
}

function removeConfirm(record: EduHomeworkDeadlineApi.Extension) {
  return rowConfirm(
    '移除',
    '个别延期',
    formatStudentName(record),
    `移除后这名学生按任务截止时间 ${taskDeadlineText.value} 提交，已过截止时间的将不能再提交。需要时可以重新设置。`,
  );
}

async function handleRemove(record: EduHomeworkDeadlineApi.Extension) {
  removingId.value = record.id;
  try {
    await deleteHomeworkExtension(record.id);
    message.success(`已移除${formatStudentName(record)}的个别延期`);
    await loadData();
    emit('changed', extensions.value.length);
  } finally {
    removingId.value = undefined;
  }
}
</script>

<template>
  <Modal
    v-model:open="open"
    :title="modalTitle"
    :mask-closable="false"
    width="min(760px, calc(100vw - 32px))"
  >
    <div class="extension">
      <p class="extension__hint">
        学生的截止时间取任务截止时间 {{ taskDeadlineText }}
        和个人截止时间中较晚的一个。只能给还没提交的学生延期，设置后会通知学生。
      </p>

      <Alert
        v-if="loadError"
        message="没有读到学生名单和已有延期：网络或服务暂时不可用。"
        show-icon
        type="error"
      >
        <template #action>
          <Button size="small" @click="loadData">重新读取</Button>
        </template>
      </Alert>

      <section class="extension__form" aria-label="设置延期">
        <div class="extension__field">
          <div class="extension__label">
            <span>学生</span>
            <Button
              :disabled="candidateOptions.length === 0"
              size="small"
              type="link"
              @click="selectAllCandidates"
            >
              选中全部未提交（{{ candidateOptions.length }}）
            </Button>
          </div>
          <Select
            v-model:value="selectedStudentIds"
            :loading="loading"
            :options="candidateOptions"
            :not-found-content="
              loading ? '正在读取学生名单…' : '没有未提交的学生'
            "
            allow-clear
            class="w-full"
            mode="multiple"
            option-filter-prop="label"
            placeholder="选择还没提交的学生，可输入姓名或学号查找"
          />
        </div>
        <div class="extension__row">
          <div class="extension__field">
            <div class="extension__label">延期到</div>
            <DatePicker
              v-model:value="deadline"
              :allow-clear="false"
              :disabled-date="disabledDate"
              :show-time="{ format: 'HH:mm' }"
              :value-format="EXTENSION_DEADLINE_FORMAT"
              class="w-full"
              format="YYYY-MM-DD HH:mm"
              placeholder="选择延期后的截止时间"
            />
          </div>
          <div class="extension__field">
            <div class="extension__label">原因（选填，学生能看到）</div>
            <Input
              v-model:value="reason"
              :maxlength="EXTENSION_REASON_MAX"
              allow-clear
              placeholder="例如：病假，补交"
              show-count
            />
          </div>
        </div>
      </section>

      <section class="extension__list" aria-label="已设置的延期">
        <h3 class="extension__title">
          已设置的延期
          <span class="extension__count">{{ extensions.length }} 人</span>
        </h3>
        <Table
          :columns="extensionColumns"
          :data-source="extensions"
          :loading="{ spinning: loading, tip: '正在读取个别延期…' }"
          :pagination="false"
          :scroll="{ y: 280 }"
          row-key="id"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'student'">
              {{ formatStudentName(record) }}
            </template>
            <template v-else-if="column.key === 'deadline'">
              {{ formatDeadlineTime(record.deadline) }}
            </template>
            <template v-else-if="column.key === 'reason'">
              {{ record.reason || '—' }}
            </template>
            <template v-else-if="column.key === 'status'">
              <StatusTag
                v-bind="
                  getHomeworkRecordStatusMeta(
                    record.recordStatus ?? HOMEWORK_RECORD_STATUS.NOT_SUBMITTED,
                  )
                "
              />
            </template>
            <template v-else-if="column.key === 'action'">
              <Popconfirm
                v-bind="
                  removeConfirm(record as EduHomeworkDeadlineApi.Extension)
                "
                @confirm="
                  handleRemove(record as EduHomeworkDeadlineApi.Extension)
                "
              >
                <Button
                  :loading="removingId === record.id"
                  danger
                  size="small"
                  type="link"
                >
                  移除
                </Button>
              </Popconfirm>
            </template>
          </template>
          <template #emptyText>
            <div v-if="loading" class="h-16"></div>
            <EmptyState
              v-else
              compact
              description="在上方选择学生和时间，保存后会显示在这里。"
              title="还没有个别延期"
            />
          </template>
        </Table>
      </section>
    </div>

    <template #footer>
      <Button @click="open = false">关闭</Button>
      <Button
        :disabled="selectedStudentIds.length === 0"
        :loading="saving"
        type="primary"
        @click="handleSave"
      >
        {{ saving ? '正在保存…' : '保存延期' }}
      </Button>
    </template>
  </Modal>
</template>

<style scoped>
.extension {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-4);
}

.extension__hint {
  margin: 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.extension__form {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-3);
  padding: var(--nus-space-4);
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius-lg);
}

.extension__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--nus-space-3);
}

@media (width >= 640px) {
  .extension__row {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }
}

.extension__field {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-1);
  min-width: 0;
}

.extension__label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 24px;
  font-size: var(--nus-fs-dense);
  color: var(--nus-text-secondary);
}

.extension__title {
  display: flex;
  gap: var(--nus-space-2);
  align-items: baseline;
  margin: 0 0 var(--nus-space-2);
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  color: var(--nus-text);
}

.extension__count {
  font-size: var(--nus-fs-caption);
  font-weight: var(--nus-fw-regular);
  color: var(--nus-text-tertiary);
}
</style>
