<script lang="ts" setup>
import type { EduSemesterApi } from '#/api/edu/semester';

import { computed, ref, watch } from 'vue';

import {
  Alert,
  Button,
  Checkbox,
  CheckboxGroup,
  Modal,
  Select,
  Spin,
  Steps,
  Table,
} from 'ant-design-vue';

import {
  archiveSemesterWrapUp,
  getSemesterOptions,
  getSemesterWrapUpPreview,
} from '#/api/edu/semester';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';

import {
  defaultWrapUpSelection,
  defaultWrapUpSemester,
  hasOpenWork,
  WRAP_UP_ARCHIVE_EFFECT,
  WRAP_UP_OPEN_WORK_HINT,
  WRAP_UP_RESTORE_HINT,
  WRAP_UP_STEPS,
  wrapUpConfirmTitle,
  wrapUpResultText,
} from './semester-wrap-up';

const props = defineProps<{ open: boolean }>();

const emit = defineEmits<{
  done: [];
  'update:open': [value: boolean];
}>();

const step = ref(0);
const semesters = ref<EduSemesterApi.SemesterOption[]>([]);
const semesterName = ref<string>();
const preview = ref<EduSemesterApi.WrapUpPreview | null>(null);
const loading = ref(false);
const loadError = ref('');
const submitting = ref(false);
const selectedClassCourseIds = ref<number[]>([]);
const selectedClassIds = ref<number[]>([]);
const archiveResult = ref<EduSemesterApi.WrapUpArchiveResp | null>(null);

const stepItems = WRAP_UP_STEPS.map((title) => ({ title }));
const semesterOptions = computed(() =>
  semesters.value.map((item) => ({
    label: item.current ? `${item.semesterName}（当前）` : item.semesterName,
    value: item.semesterName,
  })),
);
const classCourses = computed(() => preview.value?.classCourses ?? []);
const classes = computed(() => preview.value?.classes ?? []);
const classOptions = computed(() =>
  classes.value.map((item) => ({
    label: `${item.className || '未命名班级'}（本学期 ${item.activeClassCourseCount ?? 0} 门课）`,
    value: item.classId,
  })),
);
const selectionHasOpenWork = computed(() =>
  hasOpenWork(classCourses.value, selectedClassCourseIds.value),
);
const nothingSelected = computed(
  () =>
    selectedClassCourseIds.value.length === 0 &&
    selectedClassIds.value.length === 0,
);
const columns = [
  { dataIndex: 'className', title: '班级' },
  { dataIndex: 'courseName', title: '课程' },
  { dataIndex: 'autoCreated', key: 'source', title: '来源', width: 120 },
  {
    align: 'right' as const,
    dataIndex: 'openTaskCount',
    title: '进行中任务',
    width: 96,
  },
  {
    align: 'right' as const,
    dataIndex: 'pendingReviewCount',
    title: '待批改',
    width: 80,
  },
];
const rowSelection = computed(() => ({
  onChange: (keys: (number | string)[]) => {
    selectedClassCourseIds.value = keys.map(Number);
  },
  selectedRowKeys: selectedClassCourseIds.value,
}));

watch(
  () => props.open,
  (open) => {
    if (open) {
      step.value = 0;
      archiveResult.value = null;
      loadSemesters();
    }
  },
);

async function loadSemesters() {
  loading.value = true;
  loadError.value = '';
  try {
    semesters.value = await getSemesterOptions();
    semesterName.value = defaultWrapUpSemester(semesters.value);
    await loadPreview();
  } catch {
    loadError.value =
      '没有读到学期列表：网络或服务暂时不可用。关闭后重新打开再试。';
  } finally {
    loading.value = false;
  }
}

async function loadPreview() {
  preview.value = null;
  selectedClassCourseIds.value = [];
  selectedClassIds.value = [];
  if (!semesterName.value) {
    return;
  }
  loading.value = true;
  loadError.value = '';
  try {
    preview.value = await getSemesterWrapUpPreview(semesterName.value);
    selectedClassCourseIds.value = defaultWrapUpSelection(
      preview.value.classCourses,
    );
  } catch {
    loadError.value =
      '没有读到这个学期的收尾预览：网络或服务暂时不可用。换个学期或稍后再试。';
  } finally {
    loading.value = false;
  }
}

function close() {
  emit('update:open', false);
}

function goBack() {
  if (step.value > 0 && step.value < 3) {
    step.value -= 1;
    return;
  }
  close();
}

async function goNext() {
  if (step.value < 2) {
    step.value += 1;
    return;
  }
  if (step.value === 2 && semesterName.value && !submitting.value) {
    submitting.value = true;
    try {
      archiveResult.value = await archiveSemesterWrapUp({
        classCourseIds: selectedClassCourseIds.value,
        classIds: selectedClassIds.value,
        semesterName: semesterName.value,
      });
      step.value = 3;
      emit('done');
    } finally {
      submitting.value = false;
    }
  }
}

/** 页面离开时据此决定能不能走 */
defineExpose({ submitting });

function toggleAllClassCourses(checked: boolean) {
  selectedClassCourseIds.value = checked
    ? classCourses.value.map((item) => item.classCourseId)
    : [];
}
</script>

<template>
  <Modal
    :closable="!submitting"
    :keyboard="!submitting"
    :mask-closable="!submitting"
    :open="open"
    :width="760"
    destroy-on-close
    title="学期收尾"
    @cancel="close"
  >
    <Steps
      :current="step"
      :items="stepItems"
      class="wrap-up__steps"
      size="small"
    />

    <Spin :spinning="loading" tip="正在读取学期收尾预览…">
      <!-- 1 预览 -->
      <template v-if="step === 0">
        <div class="wrap-up__field">
          <div class="wrap-up__label">收尾的学期</div>
          <Select
            v-model:value="semesterName"
            :options="semesterOptions"
            class="w-full"
            placeholder="选择学期"
            @change="loadPreview"
          />
        </div>
        <EmptyState
          v-if="loadError"
          :description="loadError"
          compact
          kind="blocked"
          title="预览没有读到"
        />
        <template v-else-if="preview">
          <div class="metric-grid wrap-up__metrics">
            <div class="metric-card">
              <div class="metric-card__label">在用的班级课程</div>
              <div class="metric-card__value">
                {{ preview.activeClassCourseCount }}
              </div>
            </div>
            <div class="metric-card">
              <div class="metric-card__label">进行中的任务</div>
              <div class="metric-card__value">{{ preview.openTaskCount }}</div>
              <div class="metric-card__meta">已发布，未截止或没有截止时间</div>
            </div>
            <div class="metric-card">
              <div class="metric-card__label">等待批改的提交</div>
              <div class="metric-card__value">
                {{ preview.pendingReviewCount }}
              </div>
            </div>
            <div class="metric-card">
              <div class="metric-card__label">公共课自动关联</div>
              <div class="metric-card__value">
                {{ preview.activePublicCourseLinkCount }}
              </div>
              <div class="metric-card__meta">
                在用 / 共 {{ preview.publicCourseLinkCount }} 个
              </div>
            </div>
          </div>
          <p class="wrap-up__hint">
            {{
              preview.current
                ? '这是当前学期。收尾一般在学期结束、切换到新学期之后做。'
                : '公共课的自动关联在学期切换后会自动封存已经没有进行中任务的部分，剩下的可以在这里一起处理。'
            }}
          </p>
        </template>
      </template>

      <!-- 2 选择 -->
      <template v-else-if="step === 1">
        <div class="wrap-up__label-row">
          <span class="wrap-up__label">要封存的班级课程</span>
          <Checkbox
            :checked="
              classCourses.length > 0 &&
              selectedClassCourseIds.length === classCourses.length
            "
            :indeterminate="
              selectedClassCourseIds.length > 0 &&
              selectedClassCourseIds.length < classCourses.length
            "
            @change="toggleAllClassCourses($event.target.checked)"
          >
            全选
          </Checkbox>
        </div>
        <EmptyState
          v-if="classCourses.length === 0"
          compact
          description="这个学期已经没有在用的班级课程。"
          kind="empty"
          title="没有需要封存的班级课程"
        />
        <Table
          v-else
          :columns="columns"
          :data-source="classCourses"
          :pagination="false"
          :row-selection="rowSelection"
          :scroll="{ y: 280 }"
          row-key="classCourseId"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'source'">
              <StatusTag
                :label="record.autoCreated ? '公共课同步' : '手动添加'"
                tone="neutral"
              />
            </template>
          </template>
        </Table>
        <div class="wrap-up__field wrap-up__classes">
          <div class="wrap-up__label">一起封存的班级</div>
          <div class="wrap-up__hint">
            只列出在本学期有课程、在其他学期没有在用课程的班级（公共课的自动关联不算）。默认不勾选。
          </div>
          <CheckboxGroup
            v-if="classOptions.length > 0"
            v-model:value="selectedClassIds"
            :options="classOptions"
            class="wrap-up__class-list"
          />
          <div v-else class="wrap-up__hint">没有只在本学期上课的班级。</div>
        </div>
      </template>

      <!-- 3 确认 -->
      <template v-else-if="step === 2 && semesterName">
        <h4 class="wrap-up__confirm-title">
          {{
            wrapUpConfirmTitle(
              semesterName,
              selectedClassCourseIds.length,
              selectedClassIds.length,
            )
          }}
        </h4>
        <Alert
          v-if="selectionHasOpenWork"
          :message="WRAP_UP_OPEN_WORK_HINT"
          class="wrap-up__alert"
          show-icon
          type="warning"
        />
        <p class="wrap-up__text">{{ WRAP_UP_ARCHIVE_EFFECT }}</p>
        <p class="wrap-up__text">{{ WRAP_UP_RESTORE_HINT }}</p>
      </template>

      <!-- 4 结果 -->
      <template v-else-if="step === 3 && archiveResult">
        <EmptyState
          :description="WRAP_UP_RESTORE_HINT"
          :title="
            wrapUpResultText(
              archiveResult.semesterName,
              archiveResult.archivedClassCourseCount,
              archiveResult.archivedClassCount,
            )
          "
          compact
          kind="empty"
        />
      </template>
    </Spin>

    <template #footer>
      <template v-if="step < 3">
        <Button :disabled="submitting" @click="goBack">
          {{ step === 0 ? '关闭' : '返回' }}
        </Button>
        <Button
          :disabled="
            loading ||
            !preview ||
            (step === 0 && classCourses.length === 0 && classes.length === 0) ||
            (step >= 1 && nothingSelected)
          "
          :loading="submitting"
          type="primary"
          @click="goNext"
        >
          {{ step === 2 ? (submitting ? '正在封存…' : '封存') : '下一步' }}
        </Button>
      </template>
      <Button v-else type="primary" @click="close">完成</Button>
    </template>
  </Modal>
</template>

<style scoped>
.wrap-up__steps {
  margin-bottom: 20px;
}

.wrap-up__field {
  margin-bottom: 16px;
}

.wrap-up__label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.wrap-up__label {
  display: block;
  margin-bottom: 4px;
  font-size: var(--nus-fs-dense);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
}

.wrap-up__metrics {
  margin-bottom: 12px;
}

.wrap-up__hint {
  margin: 0 0 8px;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.wrap-up__classes {
  margin-top: 16px;
}

.wrap-up__class-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.wrap-up__confirm-title {
  margin: 0 0 12px;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
}

.wrap-up__alert {
  margin-bottom: 12px;
}

.wrap-up__text {
  margin: 0 0 8px;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text-secondary);
}
</style>
