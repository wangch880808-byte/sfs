<script lang="ts" setup>
import type { UploadChangeParam, UploadFile } from 'ant-design-vue';
import type { UploadRequestOption } from 'ant-design-vue/lib/vc-upload/interface';

import type { AttachmentCategory } from './attachment-rules';
import type { StudentLoadError } from './load-error';
import type { FactItem } from './task-display';
import type { DraftAttachment, DraftContent } from './use-homework-draft';

import type { StudentHomeworkApi } from '#/api/edu/student/homework';

import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import {
  Alert,
  Button,
  Card,
  Input,
  message,
  Spin,
  Upload,
} from 'ant-design-vue';

import { getUnlockStatus } from '#/api/edu/student/course';
import {
  downloadHomeworkAttachment,
  getStudentHomeworkDetail,
  submitHomework,
  updateHomeworkSubmit,
  uploadHomeworkAttachment,
} from '#/api/edu/student/homework';
import { EmptyState } from '#/components/empty-state';
import { isRichHtmlEmpty } from '#/components/rich-html';
import { StatusTag } from '#/components/status-tag';
import { useDirtyGuard } from '#/composables/use-dirty-guard';
import { CONFIRM_CANCEL_TEXT } from '#/utils/confirm-copy';
import {
  canSubmitHomework,
  formatDeadlineTime,
  isDeadlinePassed,
  isRedoDeadlineEffective,
  resolveEffectiveDeadline,
  toDeadlineTime,
} from '#/utils/homework-deadline';
import {
  clearLegacyHomeworkDrafts,
  getHomeworkDraftKey,
} from '#/utils/homework-draft';
import { getRequestErrorReason } from '#/utils/request-error';
import {
  getHomeworkRecordStatusMeta,
  HOMEWORK_RECORD_STATUS,
} from '#/utils/status';
import { isPendingReviewStatus } from '#/utils/status-grading';
import { isAlreadySubmittedStatus } from '#/utils/status-student-homework';

import {
  buildUploadAccept,
  checkAttachmentBatch,
  describeAttachmentLimits,
  formatAllowedTypes,
  getAttachmentCategory,
  getAttachmentCategoryLabel,
  resolveAttachmentLimits,
  resolveRequiredTypes,
} from './attachment-rules';
import FactList from './components/fact-list.vue';
import RichText from './components/rich-text.vue';
import TaskLoadState from './components/task-load-state.vue';
import { getLevelLockedDetail, resolveStudentLoadError } from './load-error';
import { buildTaskMeta, readTaskRouteQuery } from './task-display';
import { isSameDraftContent, useHomeworkDraft } from './use-homework-draft';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const submitting = ref(false);
/** 提交成功后不再保存草稿，也不再拦截离开 */
const submitted = ref(false);
/** 拿到任务后再渲染带页头的页面：页面按页头的实际高度计算内容区 */
const detail = ref<StudentHomeworkApi.HomeworkDetail>();
/** 任务没有读取成功时的原因（等级未解锁、不存在或其他失败） */
const loadError = ref<null | StudentLoadError>(null);
/** 等级未解锁时该等级的进度说明（来自解锁状态接口的 reasonText） */
const lockedDetail = ref('');
const content = ref('');
const fileList = ref<UploadFile[]>([]);
/** 上次提交的内容（没提交过为空）：草稿与离开确认以此为准 */
const serverState = ref<DraftContent>({ attachments: [], content: '' });
/** 上传用的原始文件，失败后重试时再用 */
const uploadSources = new Map<string, File>();
/** 同一批选中的文件只检查一次（antd 对每个文件分别调用 beforeUpload） */
const batchChecks = new WeakMap<object, Array<null | string>>();

const homeworkId = computed(() => Number(route.params.homeworkId));
const isResubmit = computed(
  () => detail.value?.status === HOMEWORK_RECORD_STATUS.REDO,
);
const draftKey = computed(() => {
  const userId = userStore.userInfo?.id;
  return userId && homeworkId.value
    ? getHomeworkDraftKey(userId, homeworkId.value)
    : '';
});
let nowTimer: null | ReturnType<typeof setInterval> = null;
/** 当前时间，每 30 秒刷新，用于判断是否已过有效截止时间 */
const now = ref(Date.now());
/** 已过有效截止时间或后端判定不可提交时，上传与提交置灰 */
const canSubmit = computed(
  () => !!detail.value && canSubmitHomework(detail.value, now.value),
);
/** 已提交（待批改、已批改）的任务不能再提交，提示条上给出查看成绩反馈的入口 */
const isAlreadySubmitted = computed(() =>
  isAlreadySubmittedStatus(Number(detail.value?.status)),
);
const submitClosedTip = computed(() => {
  if (!detail.value || canSubmit.value) return '';
  const status = Number(detail.value.status);
  if (isPendingReviewStatus(status)) {
    return '已提交，等待教师批改，批改前不能修改。';
  }
  if (status === HOMEWORK_RECORD_STATUS.CORRECTED) {
    return '这个任务已经批改，不能再提交。';
  }
  if (isDeadlinePassed(detail.value, now.value)) {
    return isResubmit.value
      ? '已过重做截止时间，不能再重新提交。'
      : '已过截止时间，不能再提交。';
  }
  return '当前任务状态不可提交。';
});

const SUBMISSION_MODE_LABELS: Record<string, string> = {
  text: '纯文本',
  image: '图片',
  audio: '音频',
  video: '视频',
  mixed: '混合',
};

function getFileNameFromUrl(url: string) {
  try {
    const lastPart = new URL(url).pathname.split('/').findLast(Boolean);
    return lastPart ? decodeURIComponent(lastPart) : '附件';
  } catch {
    const split = url.split('/').filter(Boolean);
    const lastPart = split.at(-1);
    return lastPart ? decodeURIComponent(lastPart) : '附件';
  }
}

function getFileExt(name: string) {
  const cleanName = name.split('?')[0] || name;
  const ext = cleanName.split('.').pop();
  return ext ? `.${ext}` : '';
}

function buildRenamedUploadFileName(file: File, currentUid?: number | string) {
  const category: AttachmentCategory = getAttachmentCategory(file);
  const sameCategoryItems = fileList.value.filter(
    (item) =>
      getAttachmentCategory({
        name: item.name,
        type: item.type,
        url: item.url,
      }) === category,
  );
  const currentIndex = sameCategoryItems.findIndex(
    (item) => String(item.uid) === String(currentUid),
  );
  const index =
    currentIndex === -1 ? sameCategoryItems.length + 1 : currentIndex + 1;
  return `${getAttachmentCategoryLabel(category)}${Math.max(1, index)}${getFileExt(file.name)}`;
}

/** 按 uid 更新列表里的一项（上传进度、结果、重试） */
function patchUploadItem(uid: number | string, patch: Partial<UploadFile>) {
  fileList.value = fileList.value.map((item) =>
    String(item.uid) === String(uid) ? { ...item, ...patch } : item,
  );
}

function parseAttachmentUrls(value?: string) {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((item) => typeof item === 'string' && item.trim());
  } catch {
    return [];
  }
}

function getUploadedUrl(file: UploadFile): string {
  const url = file?.url || file?.response?.url || file?.response?.data;
  return typeof url === 'string' ? url.trim() : '';
}

/** 已上传完成的附件（提交与草稿只包括这些） */
function getUploadedAttachments(): DraftAttachment[] {
  return fileList.value
    .filter((file) => file.status === 'done' && getUploadedUrl(file))
    .map((file) => ({
      name: file.name,
      size: Number(file.size) > 0 ? Number(file.size) : undefined,
      url: getUploadedUrl(file),
    }));
}

function toUploadItems(attachments: DraftAttachment[], prefix: string) {
  return attachments.map(
    (item, index) =>
      ({
        name: item.name,
        size: item.size,
        status: 'done',
        uid: `${prefix}-${index}`,
        url: item.url,
      }) as UploadFile,
  );
}

function buildAttachmentUrlsPayload() {
  return JSON.stringify(getUploadedAttachments().map((item) => item.url));
}

/** 任务要求必须上传的附件类型（为空表示可以只交文字） */
const requiredAttachmentTypes = computed(() =>
  detail.value ? resolveRequiredTypes(detail.value) : [],
);

/** 附件上限与允许的类型（后端返回，旧版后端时用默认值） */
const attachmentLimits = computed(() =>
  resolveAttachmentLimits(detail.value ?? {}),
);

/** 附件类型的中文写法（不在界面上露出 image、audio 这类原值） */
const requiredAttachmentLabel = computed(() =>
  formatAllowedTypes(requiredAttachmentTypes.value),
);

const uploadAccept = computed(() =>
  buildUploadAccept(attachmentLimits.value.allowedTypes),
);

const attachmentHint = computed(() => {
  if (requiredAttachmentTypes.value.length === 0) {
    return '如有作品文件、截图、实验报告或补充材料，可一并附上。';
  }
  const modeLabel = requiredAttachmentLabel.value;
  if (
    detail.value?.submissionMode === 'video' &&
    detail.value?.aiReviewMode === 1
  ) {
    return `这个任务要求上传${modeLabel}。视频只做存档，不会进行 AI 初评。`;
  }
  if (detail.value?.aiReviewMode === 1) {
    return `这个任务要求上传${modeLabel}，上传后先由 AI 初评，再由教师复核。`;
  }
  return `这个任务要求上传${modeLabel}，由教师批改。`;
});

/** 附件上限写进提示，上传前也按这些检查 */
const attachmentLimitHint = computed(() =>
  describeAttachmentLimits(attachmentLimits.value),
);

const canSubmitWithoutText = computed(() =>
  ['audio', 'image', 'mixed', 'video'].includes(
    detail.value?.submissionMode || '',
  ),
);

const hasActiveUploads = computed(() =>
  fileList.value.some((file) => file.status === 'uploading'),
);

// ========== 草稿：文字和已上传的附件存在本机 ==========
const draft = useHomeworkDraft({
  baseline: () => serverState.value,
  enabled: () => canSubmit.value && !submitted.value,
  key: () => draftKey.value,
  read: () => ({
    attachments: getUploadedAttachments(),
    content: content.value,
  }),
});

function formatClock(time: number) {
  return new Date(time).toLocaleTimeString();
}

/** 草稿状态：输入后自动存在本机，提交成功后清除 */
const draftText = computed(() => {
  if (draft.lastSavedAt.value) {
    return `草稿已保存在本机 ${formatClock(draft.lastSavedAt.value)}`;
  }
  return '输入后自动保存草稿在本机';
});

/** 恢复草稿的提示：保存时间与附件个数 */
const restoredDraftDescription = computed(() => {
  const restored = draft.restoredDraft.value;
  if (!restored) return '';
  const parts = [
    restored.savedAt ? `保存于 ${formatClock(restored.savedAt)}` : '',
    restored.attachments.length > 0
      ? `包括 ${restored.attachments.length} 个附件`
      : '',
  ].filter(Boolean);
  const head = parts.length > 0 ? `${parts.join('，')}。` : '';
  return `${head}不需要时可以点「不用草稿」，回到上次提交的内容。`;
});

/** 不用恢复的草稿：回到上次提交的内容并删除草稿 */
function discardRestoredDraft() {
  content.value = serverState.value.content;
  fileList.value = toUploadItems(serverState.value.attachments, 'remote');
  draft.clear();
}

// ========== 离开确认：没提交的改动或正在上传的附件 ==========
const isPageDirty = () =>
  canSubmit.value &&
  !submitted.value &&
  (hasActiveUploads.value ||
    !isSameDraftContent(
      { attachments: getUploadedAttachments(), content: content.value },
      serverState.value,
    ));

useDirtyGuard(isPageDirty, {
  copy: {
    cancelText: CONFIRM_CANCEL_TEXT,
    confirmText: '离开',
    content:
      '文字和已上传完成的附件已存为本机草稿，下次打开会恢复；正在上传的附件会中断。',
    title: '还没有提交，离开这个页面？',
  },
});

const statusMeta = computed(() =>
  getHomeworkRecordStatusMeta(detail.value?.status, 'student'),
);

const pageDescription = computed(() =>
  detail.value ? buildTaskMeta(detail.value) : '',
);

function hasValue(value: unknown) {
  return value !== undefined && value !== null && value !== '';
}

/**
 * 任务信息：状态、截止时间（打回时写明重做截止与原截止时间；教师单独延期时写明延期后与原截止时间）、
 * 分值与提交方式
 */
const taskFacts = computed<FactItem[]>(() => {
  const task = detail.value;
  if (!task) return [];
  const facts: FactItem[] = [{ label: '状态', slot: 'status' }];
  if (isRedoDeadlineEffective(task)) {
    facts.push(
      {
        label: '重做截止',
        value: formatDeadlineTime(resolveEffectiveDeadline(task)),
      },
      { label: '原截止时间', value: formatDeadlineTime(task.deadline) },
    );
  } else if (hasValue(task.personalDeadline)) {
    const effective = hasValue(task.effectiveDeadline)
      ? task.effectiveDeadline
      : task.personalDeadline;
    facts.push(
      {
        label: '截止时间',
        value: `${formatDeadlineTime(effective)}（教师已为你延期）`,
      },
      { label: '原截止时间', value: formatDeadlineTime(task.deadline) },
    );
  } else {
    facts.push({
      label: '截止时间',
      value: formatDeadlineTime(resolveEffectiveDeadline(task)),
    });
  }
  facts.push(
    { label: '满分', value: `${task.score ?? 0} 分` },
    { label: '及格线', value: `${task.passScore ?? 0} 分` },
    {
      label: '提交方式',
      value: SUBMISSION_MODE_LABELS[task.submissionMode || 'text'] || '纯文本',
    },
  );
  if (task.submitTime) {
    facts.push({
      label: '提交时间',
      value: formatDeadlineTime(task.submitTime),
    });
  }
  return facts;
});

/** 页面上最多一条提示：不能提交的原因优先，其次是打回重做的要求与教师评语 */
const banner = computed(() => {
  if (!detail.value) return null;
  const redoComment = isResubmit.value
    ? `教师评语：${detail.value.comment || '本次打回未填写评语，请结合任务要求检查提交内容。'}`
    : '';
  if (submitClosedTip.value) {
    return {
      description: redoComment,
      message: submitClosedTip.value,
      type: isAlreadySubmitted.value ? ('info' as const) : ('warning' as const),
    };
  }
  if (isResubmit.value) {
    return {
      description: redoComment,
      message: '请根据教师反馈修改后重新提交。',
      type: 'warning' as const,
    };
  }
  return null;
});

/** 等级未解锁：按进入页面时带的课程和等级读出该等级的解锁原因；读不到就只显示通用说明 */
async function loadLockedDetail() {
  lockedDetail.value = '';
  const { courseId, level } = readTaskRouteQuery(route.query);
  if (!courseId || level === undefined) return;
  try {
    const status = await getUnlockStatus(courseId, { silentError: true });
    lockedDetail.value = getLevelLockedDetail(status?.levels, level);
  } catch {
    // 只是补充说明，读不到不影响页面
  }
}

async function loadData() {
  if (!homeworkId.value) {
    router.back();
    return;
  }
  loading.value = true;
  loadError.value = null;
  try {
    // 读取失败（含等级未解锁）由下面的页面状态说明原因，不再弹全局提示
    detail.value = await getStudentHomeworkDetail(homeworkId.value, {
      silentError: true,
    });
    const attachments = parseAttachmentUrls(detail.value.attachmentUrls).map(
      (url) => ({ name: getFileNameFromUrl(url), url }),
    );
    serverState.value = {
      attachments,
      content: detail.value.content || '',
    };
    content.value = serverState.value.content;
    fileList.value = toUploadItems(attachments, 'remote');
    try {
      clearLegacyHomeworkDrafts(localStorage);
    } catch {
      // 浏览器禁用存储时忽略
    }
    const restored = draft.restore(toDeadlineTime(detail.value.submitTime));
    if (restored) {
      content.value = restored.content;
      fileList.value = toUploadItems(restored.attachments, 'draft');
    }
  } catch (error) {
    // 页面写明原因与下一步，等级未解锁时说明怎么解锁
    loadError.value = resolveStudentLoadError(error);
    if (loadError.value.kind === 'locked') {
      void loadLockedDetail();
    }
  } finally {
    loading.value = false;
  }
}

function goFeedback() {
  router.push({ path: `/student/homework/feedback/${homeworkId.value}` });
}

/** 上传前检查类型、大小、个数与合计大小，不通过的不加入列表 */
function handleBeforeUpload(file: File, batch: File[]) {
  const files = Array.isArray(batch) && batch.length > 0 ? batch : [file];
  let results = batchChecks.get(files);
  if (!results) {
    results = checkAttachmentBatch(
      files,
      fileList.value.map((item) => ({ size: item.size })),
      attachmentLimits.value,
    );
    batchChecks.set(files, results);
  }
  const reason = results[files.indexOf(file)] ?? null;
  if (reason) {
    message.warning(reason);
    return Upload.LIST_IGNORE;
  }
  return true;
}

/** 自己维护列表：返回 false 让组件不再改列表 */
function handleRemoveFile(file: UploadFile) {
  fileList.value = fileList.value.filter((f) => f.uid !== file.uid);
  uploadSources.delete(String(file.uid));
  return false;
}

/** 组件只在选中新文件时通知；已有的项以本页维护的状态为准 */
function handleUploadChange(info: UploadChangeParam) {
  const known = new Map(
    fileList.value.map((item) => [String(item.uid), item] as const),
  );
  fileList.value = info.fileList.map(
    (item) => known.get(String(item.uid)) ?? item,
  );
}

async function handlePreviewFile(file: UploadFile) {
  try {
    const localFile = file.originFileObj ?? uploadSources.get(String(file.uid));
    const blob =
      localFile ||
      (file.url ? await downloadHomeworkAttachment(file.url) : null);
    if (!blob) {
      message.warning('附件尚未上传完成');
      return;
    }
    const objectUrl = URL.createObjectURL(blob);
    window.open(objectUrl, '_blank', 'noopener,noreferrer');
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
  } catch {
    message.error('附件没有打开，请确认登录状态后重试。');
  }
}

/** 上传一个文件；各文件互不阻塞，失败的可以单独重试 */
async function runUpload(uid: number | string, file: File) {
  const renamedFileName = buildRenamedUploadFileName(file, uid);
  const renamedFile = new File([file], renamedFileName, {
    type: file.type,
    lastModified: file.lastModified,
  });
  uploadSources.set(String(uid), file);
  patchUploadItem(uid, {
    error: undefined,
    name: renamedFileName,
    percent: 0,
    response: undefined,
    size: file.size,
    status: 'uploading',
  });
  try {
    const url = await uploadHomeworkAttachment(
      homeworkId.value,
      renamedFile,
      (event) => {
        if (event.total) {
          patchUploadItem(uid, {
            percent: Math.round((event.loaded / event.total) * 100),
          });
        }
      },
      // 失败原因写在附件列表下面（每个文件一行，可以重试），不再弹全局提示
      { silentError: true },
    );
    patchUploadItem(uid, {
      percent: 100,
      response: { name: renamedFileName, url },
      status: 'done',
      url,
    });
  } catch (error) {
    // 列表下面写明失败原因，可以单独重试
    patchUploadItem(uid, {
      error,
      response: getRequestErrorReason(error),
      status: 'error',
    });
  }
}

function handleCustomUpload(options: UploadRequestOption) {
  const file = options.file as File & { uid?: number | string };
  const uid = file.uid ?? `${Date.now()}-${file.name}`;
  void runUpload(uid, file);
}

/** 重试上传失败的附件 */
function retryUpload(item: UploadFile) {
  const source = item.originFileObj ?? uploadSources.get(String(item.uid));
  if (!source) {
    message.warning(`「${item.name}」需要重新选择文件后上传。`);
    return;
  }
  void runUpload(item.uid, source);
}

/** 上传失败的附件：列在附件列表下面，写明原因，可以单独重试 */
const failedUploads = computed(() =>
  fileList.value.filter((file) => file.status === 'error'),
);

function getUploadErrorText(item: UploadFile) {
  const reason =
    typeof item.response === 'string' && item.response
      ? `：${item.response}`
      : '';
  return `「${item.name}」没有上传成功${reason}。`;
}

async function handleSubmit() {
  if (!canSubmit.value) {
    message.warning(submitClosedTip.value || '当前任务不可提交');
    return;
  }
  if (hasActiveUploads.value) {
    message.warning('附件仍在上传中，请稍候再提交');
    return;
  }
  if (fileList.value.some((file) => file.status === 'error')) {
    message.warning('有附件没有上传成功，请重试或删掉后再提交。');
    return;
  }
  const attachmentUrls = buildAttachmentUrlsPayload();
  const attachments = parseAttachmentUrls(attachmentUrls);
  if (
    !content.value.trim() &&
    !(canSubmitWithoutText.value && attachments.length > 0)
  ) {
    message.warning('请填写提交内容，或上传附件');
    return;
  }
  if (requiredAttachmentTypes.value.length > 0 && attachments.length === 0) {
    message.warning('请先上传任务要求的附件');
    return;
  }
  submitting.value = true;
  try {
    if (isResubmit.value) {
      if (!detail.value?.recordId) {
        message.error('没有读到上一次提交，请刷新后重试。');
        return;
      }
      await updateHomeworkSubmit({
        id: detail.value.recordId,
        content: content.value,
        attachmentUrls,
      });
    } else {
      await submitHomework({
        homeworkId: homeworkId.value,
        content: content.value,
        attachmentUrls,
      });
    }
    submitted.value = true;
    draft.clear();
    message.success('任务提交成功');
    router.back();
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  loadData();
  nowTimer = setInterval(() => {
    now.value = Date.now();
  }, 30_000);
});

onUnmounted(() => {
  if (nowTimer) {
    clearInterval(nowTimer);
    nowTimer = null;
  }
});
</script>

<template>
  <Page
    v-if="detail"
    :description="pageDescription"
    :title="detail.homeworkName"
    auto-content-height
  >
    <template #extra>
      <Button @click="router.back()">返回</Button>
    </template>

    <Spin :spinning="loading">
      <div class="page-stack">
        <Alert
          v-if="banner"
          :message="banner.message"
          :type="banner.type"
          show-icon
        >
          <template v-if="banner.description" #description>
            <span class="submit-banner__comment">{{ banner.description }}</span>
          </template>
          <template v-if="isAlreadySubmitted" #action>
            <Button size="small" @click="goFeedback">查看反馈</Button>
          </template>
        </Alert>

        <div class="submit-layout">
          <Card class="submit-layout__facts" title="任务信息">
            <FactList :items="taskFacts" class="submit-facts">
              <template #status>
                <StatusTag :label="statusMeta.label" :tone="statusMeta.tone" />
              </template>
            </FactList>
          </Card>

          <div class="submit-layout__main page-stack">
            <Card title="任务要求">
              <RichText
                v-if="!isRichHtmlEmpty(detail.requirement)"
                :html="detail.requirement"
              />
              <EmptyState
                v-else
                compact
                description="有疑问可以在「我的留言」里问教师。"
                title="教师没有填写任务要求"
              />
            </Card>

            <Card title="提交内容">
              <template v-if="canSubmit" #extra>
                <span class="submit-caption">{{ draftText }}</span>
              </template>
              <Alert
                v-if="canSubmit && draft.restoredDraft.value"
                :description="restoredDraftDescription"
                class="submit-draft-note"
                message="已恢复上次未提交的草稿"
                show-icon
                type="info"
              >
                <template #action>
                  <Button size="small" @click="discardRestoredDraft">
                    不用草稿
                  </Button>
                </template>
              </Alert>
              <Input.TextArea
                v-model:value="content"
                :readonly="!canSubmit"
                :rows="10"
                aria-label="提交内容"
                placeholder="写清实践过程、完成结果和关键结论"
              />

              <div class="submit-attachments">
                <div class="submit-field-label">附件</div>
                <Upload
                  :file-list="fileList"
                  :before-upload="handleBeforeUpload"
                  :custom-request="handleCustomUpload"
                  :accept="uploadAccept || undefined"
                  :disabled="!canSubmit"
                  :max-count="attachmentLimits.maxCount"
                  :remove="handleRemoveFile"
                  @change="handleUploadChange"
                  @preview="handlePreviewFile"
                  multiple
                >
                  <Button :disabled="!canSubmit">选择文件</Button>
                </Upload>
                <ul
                  v-if="failedUploads.length > 0"
                  class="submit-upload-failures"
                >
                  <li
                    v-for="item in failedUploads"
                    :key="item.uid"
                    class="submit-upload-failures__item"
                  >
                    <span>{{ getUploadErrorText(item) }}</span>
                    <Button
                      :disabled="!canSubmit"
                      size="small"
                      type="link"
                      @click="retryUpload(item)"
                    >
                      重试
                    </Button>
                  </li>
                </ul>
                <p class="submit-caption submit-attachments__hint">
                  {{ attachmentHint }}{{ attachmentLimitHint }}
                </p>
              </div>

              <div class="submit-footer">
                <Button
                  :disabled="!canSubmit"
                  :loading="submitting"
                  type="primary"
                  @click="handleSubmit"
                >
                  {{ isResubmit ? '重新提交' : '提交' }}
                </Button>
                <span v-if="canSubmit" class="submit-caption">
                  提交后，教师批改前不能修改。
                </span>
              </div>
            </Card>
          </div>
        </div>
      </div>
    </Spin>
  </Page>

  <Page v-else auto-content-height title="任务提交">
    <TaskLoadState
      :error="loadError"
      :loading="loading"
      :locked-detail="lockedDetail"
      @retry="loadData"
    />
  </Page>
</template>

<style scoped>
.submit-layout {
  display: grid;
  grid-template-areas:
    'facts'
    'main';
  gap: 16px;
  align-items: start;
}

.submit-layout__facts {
  grid-area: facts;
}

.submit-layout__facts .submit-facts {
  /* 任务信息在上方横跨整行时，属性按列排开，少占高度 */
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  column-gap: 24px;
}

.submit-layout__main {
  grid-area: main;
  min-width: 0;
}

.submit-banner__comment {
  white-space: pre-line;
}

.submit-caption {
  font-size: var(--nus-fs-caption);
  font-weight: var(--nus-fw-regular);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.submit-field-label {
  margin-bottom: 8px;
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.submit-attachments {
  margin-top: 20px;
}

.submit-attachments__hint {
  margin: 8px 0 0;
}

.submit-draft-note {
  margin-bottom: 12px;
}

.submit-upload-failures {
  padding: 0;
  margin: 8px 0 0;
  list-style: none;
}

.submit-upload-failures__item {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-danger);
}

.submit-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding-top: 16px;
  margin-top: 20px;
  border-top: 1px solid var(--nus-border-subtle);
}

@media (width >= 1280px) {
  .submit-layout {
    /* 宽屏：左边任务要求与提交内容，右边任务信息；窄屏时任务信息在最上面 */
    grid-template-areas: 'main facts';
    grid-template-columns: minmax(0, 1fr) 300px;
  }

  .submit-layout__facts .submit-facts {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
