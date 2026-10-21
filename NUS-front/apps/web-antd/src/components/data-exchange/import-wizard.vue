<script lang="ts" setup>
import type { TableColumnsType, UploadProps } from 'ant-design-vue';

import type { EduDataExchangeApi } from '#/api/edu/data-exchange';

import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';
import { downloadFileFromBlobPart } from '@vben/utils';

import {
  Alert,
  Button,
  Checkbox,
  Descriptions,
  DescriptionsItem,
  Input,
  message,
  Modal,
  Pagination,
  Space,
  Steps,
  Table,
  Upload,
} from 'ant-design-vue';

import {
  cancelDataExchangeImport,
  confirmDataExchangeImport,
  downloadDataExchangeTemplate,
  getDataExchangeErrorFileUrl,
  getDataExchangeImport,
  getDataExchangeImportItems,
  previewDataExchangeImport,
} from '#/api/edu/data-exchange';
import { StatusTag } from '#/components/status-tag';
import { getImportValidationMeta } from '#/utils/status-infra-ai';

import {
  isImportFinished,
  isRequestTimeout,
  waitForImportBatch,
} from './import-polling';
import {
  formatSkipBreakdown,
  getSkipReasonLabel,
  hasNothingToWrite,
} from './import-summary';

interface Props {
  allowUpdate?: boolean;
  bizType: string;
  open: boolean;
  options?: Record<string, unknown>;
  templateDownloader?: () => Promise<BlobPart>;
  templateFileName: string;
  title: string;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  success: [];
  'update:open': [value: boolean];
}>();

const currentStep = ref(0);
const selectedFile = ref<File>();
const batch = ref<EduDataExchangeApi.ImportBatch>();
const items = ref<EduDataExchangeApi.ImportItem[]>([]);
const itemTotal = ref(0);
const itemPage = ref(1);
const itemPageSize = ref(20);
const busy = ref(false);
const updateSupport = ref(false);
const initialPassword = ref('');
/** 确认导入后在后台执行：轮询期间显示进度，窗口关闭后停止轮询 */
const importing = ref(false);
const importSeconds = ref(0);
let importTimer: ReturnType<typeof setInterval> | undefined;
let stopPolling = false;

const canConfirm = computed(
  () =>
    batch.value?.status === 'PREVIEW_READY' &&
    (batch.value.invalidCount ?? 0) === 0,
);

interface PreviewDisplayItem extends EduDataExchangeApi.ImportItem {
  previewData: Record<string, unknown>;
}

const fieldTitles: Record<string, string> = {
  accountAction: '账号动作',
  classId: '班级编号',
  className: '班级名称',
  comment: '教师评语',
  deptCode: '组织编码',
  deptType: '组织类型',
  educationLevel: '培养层次',
  email: '邮箱',
  gradeYear: '入学年份',
  homeworkId: '任务编号',
  homeworkLevel: '任务等级',
  indicatorFirstName: '一级能力指标',
  indicatorSecondName: '二级能力指标',
  leaderUsername: '负责人账号',
  majorCode: '专业编码',
  mobile: '手机号',
  name: '名称',
  nickname: '姓名',
  parentDeptCode: '上级组织编码',
  phone: '电话',
  professionalTitle: '职称',
  relationAction: '班级关系动作',
  remark: '备注',
  requirement: '任务要求',
  schoolingYear: '学制',
  score: '成绩',
  sex: '性别',
  skipReason: '跳过原因',
  sort: '排序',
  status: '状态',
  studentId: '学生编号',
  title: '任务标题',
  username: '账号/学号',
};

const hiddenPreviewFields = new Set([
  // 成绩册：任务编号每行相同，学生以学号、姓名显示
  'homeworkId',
  'indicatorFirstId',
  'indicatorSecondId',
  'levelName',
  'recordId',
  'rowToken',
  'studentId',
  'templateChecksum',
]);

const displayItems = computed<PreviewDisplayItem[]>(() =>
  items.value.map((item) => ({
    ...item,
    previewData: parseObject(item.normalizedData),
  })),
);

const previewFieldKeys = computed(() => {
  const keys = new Set<string>();
  for (const item of displayItems.value) {
    Object.keys(item.previewData).forEach((key) => {
      if (!hiddenPreviewFields.has(key)) keys.add(key);
    });
  }
  return [...keys];
});

const columns = computed<TableColumnsType<PreviewDisplayItem>>(() => [
  { title: '工作表', dataIndex: 'sheetName', fixed: 'left', width: 110 },
  { title: '行号', dataIndex: 'rowNumber', fixed: 'left', width: 68 },
  ...previewFieldKeys.value.map((key) => ({
    title: fieldTitles[key] || key,
    key: `data:${key}`,
    ellipsis: true,
    width:
      key === 'requirement' ||
      key === 'comment' ||
      key === 'remark' ||
      key === 'skipReason'
        ? 220
        : 140,
  })),
  {
    title: '动作',
    dataIndex: 'action',
    key: 'action',
    fixed: 'right',
    width: 82,
  },
  {
    title: '校验结果',
    dataIndex: 'validationStatus',
    key: 'validationStatus',
    fixed: 'right',
    width: 92,
  },
  {
    title: '校验信息',
    dataIndex: 'validationErrors',
    key: 'validationErrors',
    fixed: 'right',
    width: 220,
  },
]);

/** 跳过行按原因拆分（目前只有成绩册），例如「未改动 3 · 未填成绩 2」 */
const skipBreakdown = computed(() =>
  formatSkipBreakdown(batch.value?.skipReasonCounts),
);
const nothingToWrite = computed(() => hasNothingToWrite(batch.value));

const hasPasswordPolicy = computed(() => !!batch.value?.initialPasswordMode);
/** 学号作初始密码（USERNAME）或系统配置了默认初始密码时，可以留空 */
const hasDefaultPassword = computed(
  () =>
    batch.value?.initialPasswordMode === 'USERNAME' ||
    !!batch.value?.defaultInitialPassword,
);
/** 没有默认初始密码且本批有新增账号时，初始密码必填 */
const passwordRequired = computed(
  () =>
    hasPasswordPolicy.value &&
    !hasDefaultPassword.value &&
    (batch.value?.insertedCount ?? 0) > 0,
);
const passwordHint = computed(() => {
  if (batch.value?.initialPasswordMode === 'USERNAME') {
    return '新建的学生账号默认用各自的学号作初始密码，也可以在这里统一设置；已有账号的密码不变。';
  }
  if (!hasDefaultPassword.value) {
    return '系统没有设置默认初始密码，新建的账号要在这里设置初始密码（4～16 位）；已有账号的密码不变。';
  }
  return `新建账号的默认密码是 ${batch.value?.defaultInitialPassword}，也可以在这里统一设置；已有账号的密码不变。`;
});

/** 导入动作是分类信息，按普通文字显示（V1.0.1.6 界面规范：颜色只给需要注意的状态） */
const actionMeta: Record<string, { text: string }> = {
  INSERT: { text: '新建' },
  SKIP: { text: '跳过' },
  UPDATE: { text: '更新' },
};

function reset() {
  currentStep.value = 0;
  selectedFile.value = undefined;
  batch.value = undefined;
  items.value = [];
  itemTotal.value = 0;
  itemPage.value = 1;
  busy.value = false;
  updateSupport.value = false;
  initialPassword.value = '';
}

watch(
  () => props.open,
  (open) => {
    if (open) reset();
    else stopPolling = true;
  },
);

onBeforeUnmount(() => {
  stopPolling = true;
  stopImportClock();
});

function close() {
  if (!busy.value) emit('update:open', false);
}

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  selectedFile.value = file as File;
  return false;
};

async function downloadTemplate() {
  busy.value = true;
  try {
    const source = props.templateDownloader
      ? await props.templateDownloader()
      : await downloadDataExchangeTemplate(props.bizType);
    downloadFileFromBlobPart({ fileName: props.templateFileName, source });
  } finally {
    busy.value = false;
  }
}

async function loadItems(page = itemPage.value) {
  if (!batch.value?.batchNo) return;
  const result = await getDataExchangeImportItems(batch.value.batchNo, {
    pageNo: page,
    pageSize: itemPageSize.value,
  });
  items.value = result.list ?? [];
  itemTotal.value = result.total ?? 0;
  itemPage.value = page;
}

async function preview() {
  if (!selectedFile.value) {
    message.warning('先选择要导入的文件');
    return;
  }
  busy.value = true;
  try {
    batch.value = await previewDataExchangeImport(
      props.bizType,
      selectedFile.value,
      {
        ...props.options,
        ...(props.allowUpdate ? { updateSupport: updateSupport.value } : {}),
      },
    );
    initialPassword.value = batch.value.defaultInitialPassword || '';
    currentStep.value = 1;
    await loadItems(1);
  } finally {
    busy.value = false;
  }
}

function startImportClock() {
  importing.value = true;
  importSeconds.value = 0;
  stopPolling = false;
  importTimer = setInterval(() => (importSeconds.value += 1), 1000);
}

function stopImportClock() {
  importing.value = false;
  if (importTimer) clearInterval(importTimer);
  importTimer = undefined;
}

async function confirmImport() {
  if (!batch.value?.batchNo || !canConfirm.value) return;
  const password = initialPassword.value.trim();
  if (passwordRequired.value && !password) {
    message.warning('系统没有设置默认初始密码，先填写新建账号的初始密码');
    return;
  }
  if (password && (password.length < 4 || password.length > 16)) {
    message.warning('初始密码要 4～16 位');
    return;
  }
  const batchNo = batch.value.batchNo;
  busy.value = true;
  startImportClock();
  try {
    try {
      batch.value = await confirmDataExchangeImport(batchNo, {
        ...(password ? { initialPassword: password } : {}),
      });
    } catch (error) {
      // 受理请求超时不代表失败：后端可能已经在导入，改为查询批次状态
      if (!isRequestTimeout(error)) throw error;
    }
    const finished = isImportFinished(batch.value?.status)
      ? batch.value
      : await waitForImportBatch(() => getDataExchangeImport(batchNo), {
          onUpdate: (latest) => (batch.value = latest),
          shouldStop: () => stopPolling,
        });
    if (!finished) return;
    batch.value = finished;
    currentStep.value = 2;
    if (finished.status === 'SUCCESS') {
      message.success('导入完成');
      emit('success');
    }
  } finally {
    stopImportClock();
    busy.value = false;
  }
}

/** 导入较慢时可以先关闭窗口，导入在后台继续，完成后刷新列表即可看到 */
function closeWhileImporting() {
  stopPolling = true;
  stopImportClock();
  busy.value = false;
  message.info('导入在后台继续进行，完成后刷新列表即可看到结果');
  emit('update:open', false);
  emit('success');
}

async function cancelImport() {
  if (!batch.value?.batchNo) return;
  busy.value = true;
  try {
    batch.value = await cancelDataExchangeImport(batch.value.batchNo);
    message.success('导入批次已取消');
    close();
  } finally {
    busy.value = false;
  }
}

async function downloadErrors() {
  if (!batch.value?.batchNo) return;
  const url = await getDataExchangeErrorFileUrl(batch.value.batchNo);
  if (url) window.open(url, '_blank', 'noopener,noreferrer');
}

function chooseAgain() {
  selectedFile.value = undefined;
  batch.value = undefined;
  items.value = [];
  itemTotal.value = 0;
  currentStep.value = 0;
}

function parseObject(value?: string): Record<string, unknown> {
  if (!value) return {};
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed
      : {};
  } catch {
    return {};
  }
}

function formatFieldValue(key: string, value: unknown) {
  if (value === undefined || value === null || value === '') return '—';
  if (key === 'sex')
    return (
      ({ 0: '未知', 1: '男', 2: '女' } as Record<number, string>)[
        Number(value)
      ] || value
    );
  if (key === 'status')
    return (
      ({ 0: '正常', 1: '停用' } as Record<number, string>)[Number(value)] ||
      value
    );
  if (key === 'professionalTitle') {
    return (
      (
        {
          1: '助教（高校）',
          2: '讲师（高校）',
          3: '副教授',
          4: '教授',
        } as Record<number, string>
      )[Number(value)] || value
    );
  }
  if (key === 'deptType')
    return (
      (
        { COLLEGE: '学院', MAJOR: '专业', SCHOOL: '学校' } as Record<
          string,
          string
        >
      )[String(value)] || value
    );
  if (key === 'skipReason') return getSkipReasonLabel(value);
  if (key === 'accountAction' || key === 'relationAction') {
    return actionMeta[String(value)]?.text || value;
  }
  if (Array.isArray(value)) return value.join('、') || '—';
  if (typeof value === 'object')
    return Object.values(value).filter(Boolean).join(' / ') || '—';
  return String(value);
}

function formatErrors(value?: string) {
  if (!value) return '—';
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.join('；') : String(parsed);
  } catch {
    return value;
  }
}
</script>

<template>
  <Modal
    :open="open"
    :title="title"
    :width="1080"
    :closable="!busy"
    :mask-closable="false"
    :footer="null"
    destroy-on-close
    @cancel="close"
  >
    <Steps
      class="mb-6"
      :current="currentStep"
      :items="[
        { title: '选择文件' },
        { title: '预检确认' },
        { title: '导入结果' },
      ]"
    />

    <div v-if="currentStep === 0" class="space-y-4">
      <div class="flex justify-end">
        <Button :loading="busy" @click="downloadTemplate">
          <template #icon><IconifyIcon icon="lucide:download" /></template>
          下载模板
        </Button>
      </div>
      <Upload.Dragger
        :before-upload="beforeUpload"
        :max-count="1"
        :show-upload-list="false"
        accept=".xlsx"
      >
        <div class="flex flex-col items-center gap-2 py-8">
          <IconifyIcon
            icon="lucide:file-spreadsheet"
            class="size-10 text-muted-foreground"
          />
          <div class="text-sm font-medium">
            {{ selectedFile?.name || '选择 XLSX 文件' }}
          </div>
        </div>
      </Upload.Dragger>
      <Checkbox v-if="allowUpdate" v-model:checked="updateSupport">
        覆盖已有账号（空单元格不覆盖原值）
      </Checkbox>
      <div class="flex justify-end gap-2">
        <Button @click="close">取消</Button>
        <Button type="primary" :loading="busy" @click="preview">
          扫描并预览
        </Button>
      </div>
    </div>

    <div v-else-if="currentStep === 1 && batch" class="space-y-4">
      <Alert
        v-if="importing"
        type="info"
        show-icon
        message="正在导入，请稍候"
        :description="`共 ${batch.validCount ?? batch.totalCount} 行，已用时 ${importSeconds} 秒。行数较多时需要一两分钟；也可以关闭窗口，导入会在后台继续。`"
      />
      <Alert
        v-if="batch.invalidCount > 0"
        type="error"
        show-icon
        message="预检发现错误，不能导入。按「校验信息」一栏改好文件后，点「重新选择」重新上传。"
      />
      <Alert
        v-else-if="nothingToWrite"
        type="info"
        show-icon
        message="预检通过，但文件里没有要写入的改动，所有行都会跳过。确认导入不会改动任何数据，也不会发通知。"
      />
      <Alert
        v-else
        type="success"
        show-icon
        message="预检通过，核对下面的明细后点「确认导入」。"
      />
      <Descriptions bordered size="small" :column="4">
        <DescriptionsItem label="批次号" :span="2">
          {{ batch.batchNo }}
        </DescriptionsItem>
        <DescriptionsItem label="模板版本">
          {{ batch.templateVersion }}
        </DescriptionsItem>
        <DescriptionsItem label="总行数">
          {{ batch.totalCount }}
        </DescriptionsItem>
        <DescriptionsItem label="有效">{{ batch.validCount }}</DescriptionsItem>
        <DescriptionsItem label="错误">
          {{ batch.invalidCount }}
        </DescriptionsItem>
        <DescriptionsItem label="新建">
          {{ batch.insertedCount }}
        </DescriptionsItem>
        <DescriptionsItem label="更新">
          {{ batch.updatedCount }}
        </DescriptionsItem>
        <DescriptionsItem label="跳过">
          {{ batch.skippedCount }}
        </DescriptionsItem>
        <DescriptionsItem label="文件" :span="3">
          {{ batch.sourceFileName }}
        </DescriptionsItem>
        <DescriptionsItem v-if="skipBreakdown" label="跳过原因" :span="4">
          {{ skipBreakdown }}
        </DescriptionsItem>
      </Descriptions>

      <div
        v-if="hasPasswordPolicy"
        class="flex flex-col gap-3 rounded-md border border-solid border-border p-4 sm:flex-row sm:items-center"
      >
        <div class="min-w-0 flex-1">
          <div class="font-medium">
            新建账号的初始密码
            <span v-if="passwordRequired" class="text-destructive">*</span>
          </div>
          <div class="mt-1 text-sm text-muted-foreground">
            {{ passwordHint }}
          </div>
        </div>
        <Input.Password
          v-model:value="initialPassword"
          class="w-full sm:w-64"
          :maxlength="16"
          :placeholder="
            hasDefaultPassword
              ? '留空则使用默认值'
              : '请输入初始密码（4～16 位）'
          "
          autocomplete="new-password"
        />
      </div>

      <Table
        :row-key="(record) => `${record.sheetName}:${record.rowNumber}`"
        size="small"
        :columns="columns"
        :data-source="displayItems"
        :loading="busy"
        :pagination="false"
        :scroll="{ x: 'max-content', y: 360 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            {{ actionMeta[record.action]?.text || record.action }}
          </template>
          <template v-else-if="column.key === 'validationStatus'">
            <StatusTag
              v-bind="getImportValidationMeta(record.validationStatus)"
            />
          </template>
          <template v-else-if="column.dataIndex === 'validationErrors'">
            <span
              :class="
                record.validationStatus === 'INVALID' ? 'text-destructive' : ''
              "
            >
              {{ formatErrors(record.validationErrors) }}
            </span>
          </template>
          <template v-else-if="String(column.key).startsWith('data:')">
            {{
              formatFieldValue(
                String(column.key).slice(5),
                record.previewData[String(column.key).slice(5)],
              )
            }}
          </template>
        </template>
      </Table>
      <div class="flex items-center justify-between gap-3">
        <Pagination
          v-model:current="itemPage"
          :page-size="itemPageSize"
          :total="itemTotal"
          size="small"
          show-less-items
          @change="loadItems"
        />
        <Space>
          <Button v-if="batch.hasErrorFile" @click="downloadErrors">
            <template #icon>
              <IconifyIcon icon="lucide:file-warning" />
            </template>
            错误回执
          </Button>
          <Button @click="chooseAgain">重新选择</Button>
          <Button @click="cancelImport">取消批次</Button>
          <Button v-if="importing" @click="closeWhileImporting">
            关闭窗口，后台继续
          </Button>
          <Button
            type="primary"
            :disabled="!canConfirm"
            :loading="busy"
            @click="confirmImport"
          >
            {{ importing ? `正在导入（${importSeconds} 秒）` : '确认导入' }}
          </Button>
        </Space>
      </div>
    </div>

    <div v-else-if="currentStep === 2 && batch" class="space-y-4">
      <Alert
        :type="batch.status === 'SUCCESS' ? 'success' : 'error'"
        show-icon
        :message="batch.status === 'SUCCESS' ? '导入完成' : '导入失败'"
        :description="batch.failureReason"
      />
      <Descriptions bordered size="small" :column="3">
        <DescriptionsItem label="新建">
          {{ batch.insertedCount }}
        </DescriptionsItem>
        <DescriptionsItem label="更新">
          {{ batch.updatedCount }}
        </DescriptionsItem>
        <DescriptionsItem label="跳过">
          {{ batch.skippedCount }}
        </DescriptionsItem>
        <DescriptionsItem v-if="skipBreakdown" label="跳过原因" :span="3">
          {{ skipBreakdown }}
        </DescriptionsItem>
      </Descriptions>
      <div class="flex justify-end">
        <Button type="primary" @click="close">关闭</Button>
      </div>
    </div>
  </Modal>
</template>
