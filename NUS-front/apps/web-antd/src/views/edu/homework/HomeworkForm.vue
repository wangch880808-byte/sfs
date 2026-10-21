<script lang="ts" setup>
import type { KeepClassOption } from './class-options';
import type { ClassCourseRelation } from './class-relations';

import type { EduHomeworkApi } from '#/api/edu/homework';

import { computed, h, nextTick, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Alert, Modal as AntModal, message, Select } from 'ant-design-vue';
import dayjs from 'dayjs';

import { useVbenForm } from '#/adapter/form';
import {
  createHomework,
  getHomework,
  updateHomework,
} from '#/api/edu/homework';
import { getHomeworkCopyTargets } from '#/api/edu/homework/copy';
import { RichHtml } from '#/components/rich-html';
import { snapshotForm, useDirtyGuard } from '#/composables/use-dirty-guard';
import { $t } from '#/locales';
import { discardChangesConfirm } from '#/utils/confirm-copy';
import { filterOperableClasses } from '#/views/capacity/course/class-claim';

import {
  NO_OPERABLE_CLASS_PLACEHOLDER,
  QUICK_CREATE_MISSING_HINT,
  resolveQuickCreateBlockHint,
} from './class-options';
import {
  buildBatchCreatePayloads,
  buildCopyFormValues,
  buildRelationOptions,
  COPY_NO_TARGET_HINT,
  COPY_SOURCE_MISSING_HINT,
  indexRelations,
  relationsFromCopyTargets,
  resolveSelectedRelations,
} from './class-relations';
import {
  buildRequiredAttachmentTypesJson,
  getClassOptionsByCourse,
  getCourseSelectOptions,
  getFirstIndicatorOptions,
  getSecondIndicatorOptions,
  GRADING_STRATEGY_OPTIONS,
  resolveGradingStrategy,
  resolveGradingStrategyConfig,
  SUBMISSION_MODE_OPTIONS,
  useFormSchema,
} from './data';
import { isHomeworkPending } from './display';
import {
  checkHomeworkContext,
  HOMEWORK_INDICATOR_UNSET_NOTICE,
  isHomeworkContextValid,
} from './form-validation';
import { resolvePassScoreChange } from './pass-score-change';

/**
 * 表单的打开方式：
 * - default：任务列表新建、修改任务；
 * - course-detail-create：课程详情快捷新建，课程与一级指标固定，班级多选；
 * - copy：复制任务，课程与一级指标固定为来源任务的，班级多选，其余字段取来源任务的值。
 */
type FormScene = 'copy' | 'course-detail-create' | 'default';

const emit = defineEmits(['success']);
const formData = ref<EduHomeworkApi.Homework>();
/** 编辑模式下设值时，跳过级联清空逻辑 */
const isSettingValues = ref(false);
const formScene = ref<FormScene>('default');
/** 课程固定、班级多选（下拉的值是班课编号）的场景：快捷新建与复制 */
const isFixedCourseScene = computed(() => formScene.value !== 'default');
/** 复制任务的来源任务编号 */
const copySourceHomeworkId = ref<number | undefined>();
/** 课程选项里查不到固定课程时显示的名称（复制时取自可布置的班课） */
const fixedCourseName = ref('');
const quickDeadlineDays = [1, 2, 3, 4, 5, 6, 7];
const activeQuickDeadline = ref<number | undefined>();
const courseOptions = ref<Array<{ label: string; value: number }>>([]);
const classOptions = ref<Array<{ label: string; value: number }>>([]);
const indicatorFirstOptions = ref<Array<{ label: string; value: number }>>([]);
const indicatorSecondOptions = ref<Array<{ label: string; value: number }>>([]);
const selectedCourseId = ref<number | undefined>();
const selectedIndicatorFirstId = ref<number | undefined>();
/** 「指定课程」的行内错误：点确认时没选课程才显示，选了课程即清除 */
const courseError = ref('');
const previewVisible = ref(false);
const previewSubmitting = ref(false);
const previewPayload = ref<
  EduHomeworkApi.Homework | EduHomeworkApi.Homework[]
>();
const previewSummary = ref<Record<string, any>>();
/**
 * 提交前的预览即确认框：标题写动作和对象，确认按钮重复动作（新建或保存），另一个按钮写“返回”。
 * 打开预览时定下文字，关闭动画期间表单重置也不会跟着变。
 */
const previewAction = ref('新建');
const previewTitle = ref('');
/** 修改任务时任务原来的班级：即使已放弃认领也留在「指定班级」的选项里，班级不变时可以保存 */
const editKeepClass = ref<KeepClassOption>();
/** 课程固定场景下可选的班课，按班课编号索引（下拉的值就是班课编号） */
const quickClassRelationMap = ref(new Map<number, ClassCourseRelation>());

interface HomeworkModalExtraData {
  /**
   * 课程详情传入：是否公共课认领模式。新建时没有可布置的班级，公共课提示先认领；
   * 修改时任务原班级已放弃认领，下拉里标「已放弃认领」。其他入口不传，修改时从课程选项里查是否公共课。
   */
  claimMode?: boolean;
  /** 修改任务时任务原来班级的名称（任务列表、课程详情的任务行带入），原班级不在可选班级里时用它显示 */
  className?: string;
  fixedCourseId?: number | string;
  fixedIndicatorFirstId?: number | string;
  scene?: 'copy' | 'course-detail-create';
  /** scene 为 copy 时：复制来源任务的编号 */
  sourceHomeworkId?: number | string;
  courseClassRelations?: Array<{
    /** 公共课里能否给这个班布置任务（认领人）；旧版后端没有时按能 */
    canOperate?: boolean;
    classCourseId: number | string;
    classId: number | string;
    className?: string;
    semesterName?: string;
    status?: string;
  }>;
}

type HomeworkModalData = HomeworkModalExtraData &
  Partial<EduHomeworkApi.Homework>;

const getTitle = computed(() => {
  if (formScene.value === 'copy') {
    return '复制任务';
  }
  return formData.value?.id
    ? $t('ui.actionTitle.edit', ['实训任务'])
    : '新建实训任务';
});

const gradingStrategyLabelMap = new Map(
  GRADING_STRATEGY_OPTIONS.map((item) => [item.value, item.label]),
);
const submissionModeLabelMap = new Map(
  SUBMISSION_MODE_OPTIONS.map((item) => [item.value, item.label]),
);

/** “课程与指标”一节的说明：从课程详情新建、复制任务时课程与一级指标已带入 */
const contextHint = computed(() => {
  const count = quickClassRelationMap.value.size;
  switch (formScene.value) {
    case 'copy': {
      return `课程和一级指标沿用原任务，不能修改；本学期可选班级 ${count} 个，每个班新建一个任务。`;
    }
    case 'course-detail-create': {
      return `课程和一级指标由课程带入，不能修改；可选班级 ${count} 个。`;
    }
    default: {
      return '先选课程和一级指标，班级与二级指标的选项会随之更新。';
    }
  }
});

/** 课程固定时显示的课程名称：课程选项里查不到时用带入的名称 */
const fixedCourseLabel = computed(() => {
  const label = getOptionLabel(courseOptions.value, selectedCourseId.value);
  return label === '-' && fixedCourseName.value ? fixedCourseName.value : label;
});

/** 编辑已发布任务时改了达标分数线：预览里说明影响 */
const passScoreChange = computed(() =>
  resolvePassScoreChange(formData.value, previewSummary.value?.passScore),
);

/** 提交预览里的各项（只做显示） */
const previewItems = computed(() => {
  const summary = previewSummary.value;
  if (!summary) {
    return [];
  }
  return [
    { label: '任务名称', value: summary.homeworkName, wide: true },
    { label: '指定课程', value: summary.courseLabel },
    {
      label: '覆盖班级',
      value: (summary.classLabels ?? []).join('、') || '-',
      hint: summary.isBatchCreate
        ? `将新建 ${summary.classCount} 个任务`
        : '单班级任务',
    },
    { label: '一级指标', value: summary.indicatorFirstLabel },
    { label: '二级指标', value: summary.indicatorSecondLabel },
    { label: '截止时间', value: summary.deadline },
    {
      label: '提交模式',
      value: submissionModeLabelMap.get(summary.submissionMode) || '-',
    },
    { label: '及格分数线', value: `${summary.passScore ?? 60} 分` },
    { label: '满分值', value: `${summary.score ?? 100} 分` },
    {
      label: '批改方式',
      value: gradingStrategyLabelMap.get(summary.gradingStrategy) || '-',
      hint: summary.gradingStrategyDescription,
      wide: true,
    },
  ];
});

function getOptionLabel(
  options: Array<{ label: string; value: number | string }>,
  value?: number | string,
) {
  return (
    options.find((item) => String(item.value) === String(value))?.label || '-'
  );
}

async function loadHeaderOptions() {
  if (courseOptions.value.length === 0) {
    courseOptions.value = await getCourseSelectOptions();
  }
  if (indicatorFirstOptions.value.length === 0) {
    indicatorFirstOptions.value = await getFirstIndicatorOptions();
  }
}

function toValidNumber(value: unknown) {
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? n : undefined;
}

function buildQuickDeadline(days: number) {
  return dayjs().add(days, 'day').hour(23).minute(59).second(59);
}

/** 日期面板底部的快捷截止（面板挂在 body 下，样式见文件末尾的全局类） */
function renderQuickDeadlineFooter() {
  return h(
    'div',
    {
      class: 'homework-quick-deadline',
    },
    [
      h(
        'span',
        {
          class: 'homework-quick-deadline__label',
        },
        '快捷截止',
      ),
      ...quickDeadlineDays.map((days) =>
        h(
          'button',
          {
            class: [
              'homework-quick-deadline__chip',
              { 'is-active': activeQuickDeadline.value === days },
            ],
            type: 'button',
            onMousedown: (event: MouseEvent) => {
              event.preventDefault();
            },
            onClick: (event: MouseEvent) => {
              event.preventDefault();
              void handlePickQuickDeadline(days);
            },
          },
          `${days} 天`,
        ),
      ),
    ],
  );
}

function normalizeCourseClassRelations(
  data?: HomeworkModalData['courseClassRelations'],
) {
  // 公共课只能给自己认领的班级布置任务：课程详情已按 canOperate 过滤，这里再兜底一次
  const list = filterOperableClasses(data || []);
  return list
    .map((item) => ({
      classCourseId: Number(item.classCourseId),
      classId: Number(item.classId),
      className: item.className,
      semesterName: item.semesterName,
      status: item.status,
    }))
    .filter((item) => item.classCourseId > 0 && item.classId > 0);
}

/** 修改任务时，选回任务原来的课程才保留原班级；换了课程不保留 */
function resolveEditKeepClass(courseId?: number) {
  const keep = editKeepClass.value;
  if (!keep || !courseId || Number(formData.value?.courseId) !== courseId) {
    return undefined;
  }
  return keep;
}

/** 「指定班级」的占位文字：没选课程、有可选班级、没有可选班级（公共课要先认领） */
function resolveClassPlaceholder(courseId?: number) {
  if (!courseId) {
    return '请先选择课程';
  }
  return classOptions.value.length > 0
    ? '请选择班级'
    : NO_OPERABLE_CLASS_PLACEHOLDER;
}

async function applyDefaultSchema() {
  classOptions.value = [];
  indicatorSecondOptions.value = [];
  formApi.updateSchema([
    {
      fieldName: 'courseId',
      componentProps: {
        allowClear: false,
        disabled: false,
        placeholder: '请选择课程',
      },
    },
    {
      fieldName: 'classId',
      componentProps: {
        allowClear: false,
        mode: undefined,
        options: [],
        placeholder: '请先选择课程',
      },
    },
    {
      fieldName: 'indicatorFirstId',
      componentProps: {
        allowClear: false,
        disabled: false,
        placeholder: '请选择一级指标',
      },
    },
    {
      fieldName: 'indicatorSecondId',
      componentProps: {
        options: [],
        placeholder: '请先选择一级指标',
      },
    },
    {
      fieldName: 'deadline',
      componentProps: {
        renderExtraFooter: undefined,
      },
    },
  ]);
}

/** 课程固定、班级多选的表单（快捷新建、复制）：班级下拉的值是班课编号 */
async function applyQuickCreateSchema(
  fixedIndicatorFirstId: number | undefined,
  relations: ClassCourseRelation[],
) {
  const secondOptions = await getSecondIndicatorOptions(fixedIndicatorFirstId);
  indicatorSecondOptions.value = secondOptions;
  const quickClassOptions = buildRelationOptions(relations);
  quickClassRelationMap.value = indexRelations(relations);
  classOptions.value = quickClassOptions;
  formApi.updateSchema([
    {
      fieldName: 'courseId',
      componentProps: {
        allowClear: false,
        disabled: true,
        placeholder: '当前课程（不可修改）',
      },
    },
    {
      fieldName: 'classId',
      componentProps: {
        allowClear: true,
        maxTagCount: 'responsive',
        mode: 'multiple',
        options: quickClassOptions,
        optionFilterProp: 'label',
        placeholder: '请选择班级（可多选）',
        showSearch: true,
      },
    },
    {
      fieldName: 'indicatorFirstId',
      componentProps: {
        allowClear: false,
        disabled: true,
        placeholder: '课程一级指标（不可修改）',
      },
    },
    {
      fieldName: 'indicatorSecondId',
      componentProps: {
        options: secondOptions,
        placeholder: fixedIndicatorFirstId
          ? '请选择二级指标'
          : '没有一级指标，不用选',
      },
    },
    {
      fieldName: 'deadline',
      componentProps: {
        renderExtraFooter: renderQuickDeadlineFooter,
      },
    },
  ]);
}

async function syncCourseSelection(courseId?: number) {
  selectedCourseId.value = courseId;
  await formApi.setFieldValue('courseId', courseId);
  if (isFixedCourseScene.value) {
    return;
  }
  classOptions.value = await getClassOptionsByCourse(
    courseId,
    resolveEditKeepClass(courseId),
  );
  formApi.updateSchema([
    {
      fieldName: 'classId',
      componentProps: {
        options: classOptions.value,
        placeholder: resolveClassPlaceholder(courseId),
      },
    },
  ]);
  await formApi.setFieldValue('classId', undefined, false);
}

async function syncIndicatorFirstSelection(indicatorFirstId?: number) {
  selectedIndicatorFirstId.value = indicatorFirstId;
  await formApi.setFieldValue('indicatorFirstId', indicatorFirstId);
  const secondOptions = await getSecondIndicatorOptions(indicatorFirstId);
  indicatorSecondOptions.value = secondOptions;
  formApi.updateSchema([
    {
      fieldName: 'indicatorSecondId',
      componentProps: {
        options: secondOptions,
        placeholder: indicatorFirstId ? '请选择二级指标' : '请先选择一级指标',
      },
    },
  ]);
  await formApi.setFieldValue('indicatorSecondId', undefined, false);
}

function handleHeaderCourseChange(value: any) {
  if (isSettingValues.value) {
    return;
  }
  const courseId = toValidNumber(value);
  if (courseId) {
    courseError.value = '';
  }
  void syncCourseSelection(courseId);
}

function handleHeaderIndicatorFirstChange(value: any) {
  if (isSettingValues.value) {
    return;
  }
  void syncIndicatorFirstSelection(toValidNumber(value));
}

async function handlePickQuickDeadline(days: number) {
  activeQuickDeadline.value = days;
  await formApi.setFieldValue('deadline', buildQuickDeadline(days));
}

function getGradingStrategyDescription(strategy?: string) {
  switch (strategy) {
    case 'assist': {
      return '系统先给出 AI 初评，教师确认后才算成绩。';
    }
    default: {
      return '由教师完成最终批改，适合大多数任务场景。';
    }
  }
}

function normalizeHomeworkPayload(data: Record<string, any>) {
  const payload = { ...data } as Record<string, any>;
  if (payload.deadline && typeof payload.deadline !== 'string') {
    payload.deadline = payload.deadline.format('YYYY-MM-DD HH:mm:ss');
  }
  const strategy = String(payload.gradingStrategy || 'manual');
  const gradingConfig = resolveGradingStrategyConfig(strategy);
  payload.autoGrade = gradingConfig.autoGrade;
  payload.aiReviewMode = gradingConfig.aiReviewMode;
  payload.requiredAttachmentTypesJson = buildRequiredAttachmentTypesJson(
    String(payload.submissionMode || 'text'),
  );
  payload.aiRubricJson = payload.aiRubricJson || '';
  payload.aiReferenceJson = payload.aiReferenceJson || '';
  delete payload.gradingStrategy;
  return payload as EduHomeworkApi.Homework;
}

/** 选中的班级（值是班课编号）换成班课；只按班课编号查，见 class-relations.ts */
function resolveSelectedQuickRelations(value: unknown) {
  return resolveSelectedRelations(value, quickClassRelationMap.value);
}

function relationLabel(relation: ClassCourseRelation) {
  return relation.className?.trim() || `班级 ${relation.classCourseId}`;
}

function resolvePreviewClassLabels(data: Record<string, any>) {
  if (isFixedCourseScene.value) {
    return resolveSelectedQuickRelations(data.classId).map((relation) =>
      relationLabel(relation),
    );
  }
  return classOptions.value
    .filter((item) => String(item.value) === String(data.classId))
    .map((item) => item.label);
}

async function buildPreviewState() {
  // 表单与「课程与指标」一节同时检查，错误一起标出来，不只弹一条提示
  const { valid } = await formApi.validate();
  const data = (await formApi.getValues()) as Record<string, any>;
  const payload = normalizeHomeworkPayload(data);
  const context = checkHomeworkContext(payload);
  courseError.value = context.courseError;
  if (!valid || context.courseError) {
    return null;
  }
  // 二级指标的必填由表单规则标出；规则没生效时兜底提示
  if (!isHomeworkContextValid(context)) {
    message.warning(context.indicatorSecondError);
    return null;
  }
  const homeworkName = String(data.homeworkName || '').trim();
  if (!homeworkName) {
    message.warning('请输入任务名称');
    return null;
  }
  if (!payload.deadline) {
    message.warning('请选择截止时间');
    return null;
  }
  if (!String(payload.requirement || '').trim()) {
    message.warning('请输入任务内容');
    return null;
  }
  const classLabels = resolvePreviewClassLabels(data);
  if (classLabels.length === 0) {
    message.warning('请选择班级');
    return null;
  }
  let submitPayload: EduHomeworkApi.Homework | EduHomeworkApi.Homework[];
  if (formData.value?.id) {
    submitPayload = payload;
  } else if (isFixedCourseScene.value) {
    const selectedRelations = resolveSelectedQuickRelations(data.classId);
    if (selectedRelations.length === 0) {
      message.warning('所选班级的信息已失效，关闭后重试。');
      return null;
    }
    submitPayload = buildBatchCreatePayloads(
      payload,
      selectedRelations,
      formScene.value === 'copy' ? copySourceHomeworkId.value : undefined,
    );
  } else {
    submitPayload = payload;
  }
  return {
    payload: submitPayload,
    summary: {
      aiReferenceJson: String(data.aiReferenceJson || '').trim(),
      classCount: classLabels.length,
      classLabels,
      courseLabel: isFixedCourseScene.value
        ? fixedCourseLabel.value
        : getOptionLabel(courseOptions.value, payload.courseId),
      deadline: payload.deadline,
      gradingStrategy: String(data.gradingStrategy || 'manual'),
      gradingStrategyDescription: getGradingStrategyDescription(
        String(data.gradingStrategy || 'manual'),
      ),
      homeworkName,
      indicatorFirstLabel: context.indicatorUnset
        ? '未选择'
        : getOptionLabel(indicatorFirstOptions.value, payload.indicatorFirstId),
      indicatorUnset: context.indicatorUnset,
      indicatorSecondLabel: getOptionLabel(
        indicatorSecondOptions.value,
        payload.indicatorSecondId,
      ),
      isBatchCreate: Array.isArray(submitPayload),
      passScore: payload.passScore,
      requirement: String(payload.requirement || '').trim(),
      score: payload.score,
      submissionMode: String(payload.submissionMode || 'text'),
    },
  };
}

/**
 * 多班级逐班新建。部分班级没成功时，成功的不再重复新建：预览里只留没成功的班级，
 * 提示后可以再点确认重试；返回 true 表示还有没成功的。
 */
async function createBatch(payloads: EduHomeworkApi.Homework[]) {
  const results = await Promise.allSettled(
    payloads.map((item) => createHomework(item)),
  );
  const failed = payloads.filter(
    (_item, index) => results[index]?.status === 'rejected',
  );
  if (failed.length === 0) {
    return false;
  }
  const createdCount = payloads.length - failed.length;
  if (createdCount === 0) {
    // 全部没成功：原因已由请求的错误提示给出，预览保持原样
    return true;
  }
  emit('success');
  previewPayload.value = failed;
  const failedLabels = failed.map((item) => {
    const relation = quickClassRelationMap.value.get(
      Number(item.classCourseId),
    );
    return relation ? relationLabel(relation) : `班级 ${item.classId}`;
  });
  if (previewSummary.value) {
    previewSummary.value = {
      ...previewSummary.value,
      classCount: failed.length,
      classLabels: failedLabels,
    };
  }
  message.warning(
    `已新建 ${createdCount} 个任务，${failedLabels.join('、')}没有成功。可以再点「${previewAction.value}」重试这些班级。`,
  );
  return true;
}

async function handlePreviewSubmit() {
  if (!previewPayload.value) {
    return;
  }
  previewSubmitting.value = true;
  modalApi.lock();
  try {
    if (Array.isArray(previewPayload.value)) {
      const pending = await createBatch(previewPayload.value);
      if (pending) {
        return;
      }
    } else if (formData.value?.id) {
      await updateHomework(previewPayload.value);
    } else {
      await createHomework(previewPayload.value);
    }
    previewVisible.value = false;
    await dirtyGuard.skipGuard(() => modalApi.close());
    emit('success');
    message.success($t('ui.actionMessage.operationSuccess'));
  } finally {
    previewSubmitting.value = false;
    modalApi.unlock();
  }
}

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-1',
    labelWidth: 110,
  },
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
  wrapperClass: 'grid-cols-2',
  async handleValuesChange(values, fieldsChanged) {
    // 编辑回显设值时，不触发级联清空
    if (isSettingValues.value) return;
    // 快捷新建、复制时课程与一级指标固定，班级与二级指标的选项打开时已定好，不做级联。
    // 不能靠 isSettingValues：这个回调在 watch 里异步执行，到这里时它已复位
    const cascade = !isFixedCourseScene.value;
    if (cascade && fieldsChanged.includes('courseId')) {
      const courseId = values.courseId as number | undefined;
      classOptions.value = await getClassOptionsByCourse(
        courseId,
        resolveEditKeepClass(courseId),
      );
      formApi.updateSchema([
        {
          fieldName: 'classId',
          componentProps: {
            options: classOptions.value,
            placeholder: resolveClassPlaceholder(courseId),
          },
        },
      ]);
      selectedCourseId.value = courseId;
      await formApi.setFieldValue('classId', undefined, false);
    }
    if (cascade && fieldsChanged.includes('indicatorFirstId')) {
      const parentId = values.indicatorFirstId as number | undefined;
      const secondOptions = await getSecondIndicatorOptions(parentId);
      indicatorSecondOptions.value = secondOptions;
      formApi.updateSchema([
        {
          fieldName: 'indicatorSecondId',
          componentProps: {
            options: secondOptions,
            placeholder: '请选择二级指标',
          },
        },
      ]);
      selectedIndicatorFirstId.value = parentId;
      await formApi.setFieldValue('indicatorSecondId', undefined, false);
    }
    if (fieldsChanged.includes('gradingStrategy')) {
      const strategy = String(values.gradingStrategy || 'manual');
      const config = resolveGradingStrategyConfig(strategy);
      await formApi.setValues(config);
    }
    if (fieldsChanged.includes('submissionMode')) {
      await formApi.setFieldValue(
        'requiredAttachmentTypesJson',
        buildRequiredAttachmentTypesJson(
          String(values.submissionMode || 'text'),
        ),
      );
    }
  },
});

/** 打开并回填完成后的表单快照；关闭时清空。与当前值不同即有没保存的修改 */
const formBaseline = ref<null | string>(null);
let modalOpen = false;
function currentFormSnapshot() {
  return snapshotForm({
    course: selectedCourseId.value,
    indicator: selectedIndicatorFirstId.value,
    values: formApi.form?.values,
  });
}
/** 回填与级联选项触发的赋值都完成后再记快照 */
async function markFormClean() {
  await nextTick();
  await nextTick();
  formBaseline.value = currentFormSnapshot();
}
const dirtyGuard = useDirtyGuard(
  () =>
    formBaseline.value !== null && currentFormSnapshot() !== formBaseline.value,
  { copy: discardChangesConfirm('任务设置还没有保存，关闭后不会保留。') },
);

function resolvePreviewAction() {
  if (formScene.value === 'copy') {
    return '复制';
  }
  if (!formData.value?.id) {
    return '新建';
  }
  // 待发布（旧草稿）的任务保存即发布：分配学生、发通知
  return isHomeworkPending(formData.value.status) ? '发布' : '保存';
}

const [Modal, modalApi] = useVbenModal({
  onBeforeClose: dirtyGuard.onBeforeClose,
  async onConfirm() {
    const previewState = await buildPreviewState();
    if (!previewState) {
      return;
    }
    previewPayload.value = previewState.payload;
    previewSummary.value = previewState.summary;
    previewAction.value = resolvePreviewAction();
    previewTitle.value = `${previewAction.value}实训任务「${previewState.summary.homeworkName}」？`;
    previewVisible.value = true;
  },
  async onOpenChange(isOpen: boolean) {
    modalOpen = isOpen;
    if (!isOpen) {
      formBaseline.value = null;
      courseError.value = '';
      formData.value = undefined;
      editKeepClass.value = undefined;
      formScene.value = 'default';
      copySourceHomeworkId.value = undefined;
      fixedCourseName.value = '';
      activeQuickDeadline.value = undefined;
      quickClassRelationMap.value.clear();
      previewVisible.value = false;
      previewPayload.value = undefined;
      previewSummary.value = undefined;
      selectedCourseId.value = undefined;
      selectedIndicatorFirstId.value = undefined;
      classOptions.value = [];
      indicatorSecondOptions.value = [];
      return;
    }
    const data = modalApi.getData<HomeworkModalData>();
    modalApi.lock();
    try {
      await loadHeaderOptions();
      if (data?.scene === 'copy') {
        await openCopyScene(data.sourceHomeworkId);
        return;
      }
      const isQuickCreateScene =
        data?.scene === 'course-detail-create' && !data?.id;
      if (isQuickCreateScene) {
        const fixedCourseId = toValidNumber(data.fixedCourseId);
        const fixedIndicatorFirstId = toValidNumber(data.fixedIndicatorFirstId);
        const relations = normalizeCourseClassRelations(
          data.courseClassRelations,
        );
        // 公共课里没有自己认领的班级时提示先认领；专属课程保持原来的提示
        const blockHint = resolveQuickCreateBlockHint({
          claimMode: data.claimMode,
          fixedCourseId,
          fixedIndicatorFirstId,
          relationCount: relations.length,
        });
        if (blockHint || !fixedCourseId || !fixedIndicatorFirstId) {
          message.warning(blockHint || QUICK_CREATE_MISSING_HINT);
          await modalApi.close();
          return;
        }
        formData.value = undefined;
        formScene.value = 'course-detail-create';
        activeQuickDeadline.value = undefined;
        await applyQuickCreateSchema(fixedIndicatorFirstId, relations);
        isSettingValues.value = true;
        await formApi.setValues({
          aiConfidenceThreshold: 0.7,
          aiReviewMode: 0,
          autoGrade: 0,
          gradingStrategy: 'manual',
          classId: undefined,
          courseId: fixedCourseId,
          deadline: buildQuickDeadline(1),
          homeworkLevel: 1,
          homeworkType: 1,
          indicatorFirstId: fixedIndicatorFirstId,
          requiredAttachmentTypesJson: buildRequiredAttachmentTypesJson('text'),
          submissionMode: 'text',
          passScore: 60,
          score: 100,
        });
        isSettingValues.value = false;
        selectedCourseId.value = fixedCourseId;
        selectedIndicatorFirstId.value = fixedIndicatorFirstId;
        activeQuickDeadline.value = 1;
        return;
      }
      formScene.value = 'default';
      activeQuickDeadline.value = undefined;
      quickClassRelationMap.value.clear();
      await applyDefaultSchema();
      if (!data?.id) {
        await formApi.setValues({
          aiConfidenceThreshold: 0.7,
          aiReviewMode: 0,
          autoGrade: 0,
          gradingStrategy: 'manual',
          requiredAttachmentTypesJson: buildRequiredAttachmentTypesJson('text'),
          submissionMode: 'text',
        });
        selectedCourseId.value = undefined;
        selectedIndicatorFirstId.value = undefined;
        return;
      }
      formData.value = await getHomework(data.id);
      // 修改任务：任务原来的班级即使已不能操作、已不在可选班级里（公共课放弃认领后）也保留在选项里，
      // 显示班级名称；班级不变时可以保存，换班级只能选能操作的班级
      editKeepClass.value = {
        classId: formData.value.classId,
        className: data.className,
        publicCourse: data.claimMode,
      };
      // 先加载级联选项再设值
      if (formData.value.courseId) {
        classOptions.value = await getClassOptionsByCourse(
          formData.value.courseId,
          editKeepClass.value,
        );
        formApi.updateSchema([
          {
            fieldName: 'classId',
            componentProps: {
              options: classOptions.value,
              placeholder: '请选择班级',
            },
          },
        ]);
      }
      if (formData.value.indicatorFirstId) {
        indicatorSecondOptions.value = await getSecondIndicatorOptions(
          formData.value.indicatorFirstId,
        );
        formApi.updateSchema([
          {
            fieldName: 'indicatorSecondId',
            componentProps: {
              options: indicatorSecondOptions.value,
              placeholder: '请选择二级指标',
            },
          },
        ]);
      }
      // 截止时间：后端返回时间戳，转为 dayjs 对象供 DatePicker 使用
      const formValues = { ...formData.value } as Record<string, any>;
      if (formValues.deadline) {
        formValues.deadline = dayjs(formValues.deadline);
      }
      formValues.gradingStrategy = resolveGradingStrategy(
        formValues.autoGrade,
        formValues.aiReviewMode,
      );
      formValues.requiredAttachmentTypesJson = buildRequiredAttachmentTypesJson(
        formValues.submissionMode,
      );
      // 保存需要回显的级联字段值
      const classIdToRestore = formValues.classId;
      const indicatorSecondIdToRestore = formValues.indicatorSecondId;
      // 设值（会触发 handleValuesChange 清空级联子字段）
      isSettingValues.value = true;
      await formApi.setValues(formValues);
      // 等待异步 handleValuesChange 执行完毕
      await new Promise((resolve) => setTimeout(resolve, 100));
      isSettingValues.value = false;
      // 恢复被清空的级联字段
      if (classIdToRestore) {
        await formApi.setFieldValue('classId', classIdToRestore);
      }
      if (indicatorSecondIdToRestore) {
        await formApi.setFieldValue(
          'indicatorSecondId',
          indicatorSecondIdToRestore,
        );
      }
      selectedCourseId.value = formData.value.courseId
        ? Number(formData.value.courseId)
        : undefined;
      selectedIndicatorFirstId.value = formData.value.indicatorFirstId
        ? Number(formData.value.indicatorFirstId)
        : undefined;
    } finally {
      modalApi.unlock();
      if (modalOpen) {
        await markFormClean();
      }
    }
  },
});
dirtyGuard.bindPopup(modalApi);

/**
 * 复制任务：读来源任务与本学期我能布置任务的班课（只留来源任务那门课的）；没有可选班级时提示并关闭。
 * 课程与一级指标固定为来源任务的，班级多选，截止时间清空要求重新选，其余字段取来源任务的值。
 */
async function openCopyScene(sourceId: unknown) {
  const sourceHomeworkId = toValidNumber(sourceId);
  if (!sourceHomeworkId) {
    message.warning(COPY_SOURCE_MISSING_HINT);
    await modalApi.close();
    return;
  }
  const [source, targets] = await Promise.all([
    getHomework(sourceHomeworkId),
    getHomeworkCopyTargets(),
  ]);
  const courseId = toValidNumber(source?.courseId);
  if (!source || !courseId) {
    message.warning(COPY_SOURCE_MISSING_HINT);
    await modalApi.close();
    return;
  }
  const relations = relationsFromCopyTargets(targets, courseId);
  if (relations.length === 0) {
    message.warning(COPY_NO_TARGET_HINT);
    await modalApi.close();
    return;
  }
  formData.value = undefined;
  formScene.value = 'copy';
  copySourceHomeworkId.value = sourceHomeworkId;
  fixedCourseName.value =
    targets.find((item) => Number(item.courseId) === courseId)?.courseName ??
    '';
  activeQuickDeadline.value = undefined;
  const values = buildCopyFormValues(source);
  await applyQuickCreateSchema(values.indicatorFirstId, relations);
  isSettingValues.value = true;
  await formApi.setValues(values);
  isSettingValues.value = false;
  selectedCourseId.value = courseId;
  selectedIndicatorFirstId.value = values.indicatorFirstId;
}
</script>

<template>
  <Modal :title="getTitle" class="w-[72%] max-w-[1100px]">
    <div class="homework-form">
      <section class="homework-form__section">
        <h3 class="homework-form__title">课程与指标</h3>
        <p class="homework-form__hint">{{ contextHint }}</p>
        <div class="homework-form__context">
          <div class="homework-form__field">
            <span class="homework-form__label homework-form__label--required">
              指定课程
            </span>
            <div class="homework-form__control">
              <div v-if="isFixedCourseScene" class="homework-form__value">
                {{ fixedCourseLabel }}
              </div>
              <template v-else>
                <Select
                  :options="courseOptions"
                  :status="courseError ? 'error' : undefined"
                  :value="selectedCourseId"
                  class="w-full"
                  placeholder="请选择课程"
                  @update:value="handleHeaderCourseChange"
                />
                <p v-if="courseError" class="homework-form__error" role="alert">
                  {{ courseError }}
                </p>
              </template>
            </div>
          </div>
          <div class="homework-form__field">
            <span class="homework-form__label">一级指标</span>
            <div class="homework-form__control">
              <div v-if="isFixedCourseScene" class="homework-form__value">
                {{
                  selectedIndicatorFirstId
                    ? getOptionLabel(
                        indicatorFirstOptions,
                        selectedIndicatorFirstId,
                      )
                    : '未设置'
                }}
              </div>
              <Select
                v-else
                :options="indicatorFirstOptions"
                :value="selectedIndicatorFirstId"
                class="w-full"
                placeholder="请选择一级指标"
                @update:value="handleHeaderIndicatorFirstChange"
              />
            </div>
          </div>
        </div>
      </section>

      <section class="homework-form__section">
        <h3 class="homework-form__title">任务配置</h3>
        <p class="homework-form__hint">
          补充班级、类型、等级、截止时间和任务内容；保存前会先预览，确认后才保存。
        </p>
        <Form class="homework-form__body" />
      </section>
    </div>
  </Modal>

  <AntModal
    v-model:open="previewVisible"
    :confirm-loading="previewSubmitting"
    :width="640"
    :ok-text="previewAction"
    :title="previewTitle"
    cancel-text="返回"
    @ok="handlePreviewSubmit"
  >
    <div v-if="previewSummary" class="homework-preview">
      <Alert
        v-if="passScoreChange"
        :message="passScoreChange.message"
        class="mb-4"
        show-icon
        type="warning"
      />
      <Alert
        v-if="previewSummary.indicatorUnset"
        :message="HOMEWORK_INDICATOR_UNSET_NOTICE"
        class="mb-4"
        show-icon
        type="warning"
      />
      <dl class="homework-preview__grid">
        <div
          v-for="item in previewItems"
          :key="item.label"
          :class="{ 'is-wide': item.wide }"
          class="homework-preview__item"
        >
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
          <dd v-if="item.hint" class="homework-preview__hint">
            {{ item.hint }}
          </dd>
        </div>
      </dl>

      <section class="homework-preview__block">
        <h4 class="homework-preview__block-title">任务内容</h4>
        <RichHtml
          :html="previewSummary.requirement"
          :max-height="360"
          class="homework-preview__sunken"
          empty-text="还没有填写任务内容"
        />
      </section>

      <section
        v-if="previewSummary.aiReferenceJson"
        class="homework-preview__block"
      >
        <h4 class="homework-preview__block-title">参考资料</h4>
        <div class="homework-preview__sunken homework-preview__reference">
          {{ previewSummary.aiReferenceJson }}
        </div>
      </section>
    </div>
  </AntModal>
</template>

<style scoped>
/* 表单分两节：课程与指标、任务配置；节标题 15/600，说明 12 辅助色，节与节之间一条淡分隔线 */
.homework-form__section + .homework-form__section {
  padding-top: var(--nus-space-5);
  margin-top: var(--nus-space-1);
  border-top: 1px solid var(--nus-border-subtle);
}

.homework-form__title {
  margin: 0;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
}

.homework-form__hint {
  margin: 2px 0 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

/* 课程与一级指标两项，排布与下面的表单一致：两列、标签宽 110、标签 13 次要色 */
.homework-form__context {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: var(--nus-space-4);
  margin-top: var(--nus-space-4);
}

.homework-form__field {
  display: flex;
  align-items: center;
  padding-bottom: var(--nus-space-4);
}

.homework-form__label {
  display: flex;
  flex: none;
  justify-content: flex-end;
  width: 110px;
  margin-right: var(--nus-space-2);
  font-size: var(--nus-fs-dense);
  line-height: 24px;
  color: var(--nus-text-secondary);
}

.homework-form__control {
  position: relative;
  flex: auto;
  min-width: 0;
  padding: 1px;
}

/* 必填项：标签前的红色星号，与下面表单的必填标记一致 */
.homework-form__label--required::before {
  margin-right: 2px;
  color: var(--nus-danger);
  content: '*';
}

/* 行内错误：放在下拉框下方的留白里（不撑高这一行，标签不跟着移动），与表单校验的错误提示一致 */
.homework-form__error {
  position: absolute;
  top: 100%;
  left: 0;
  margin: 0;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-danger);
}

.homework-form__value {
  min-height: 32px;
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: 32px;
  color: var(--nus-text);
  overflow-wrap: anywhere;
}

.homework-form__body {
  margin-top: var(--nus-space-4);
}

/* 提交预览：两列的描述列表，任务内容与参考资料放在凹陷块里 */
.homework-preview {
  display: flex;
  flex-direction: column;
  gap: var(--nus-space-5);
  padding-top: var(--nus-space-2);
}

.homework-preview__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--nus-space-4) var(--nus-space-6);
  margin: 0;
}

.homework-preview__item.is-wide {
  grid-column: 1 / -1;
}

.homework-preview__item dt {
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-secondary);
}

.homework-preview__item dd {
  margin: 2px 0 0;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  overflow-wrap: anywhere;
}

.homework-preview__item .homework-preview__hint {
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.homework-preview__block-title {
  margin: 0 0 var(--nus-space-2);
  font-size: var(--nus-fs-dense);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text);
}

.homework-preview__sunken {
  padding: var(--nus-space-3);
  font-size: var(--nus-fs-body);
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius-lg);
}

.homework-preview__reference {
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  white-space: pre-wrap;
}
</style>

<style>
/* 截止时间的快捷选项渲染在日期面板里（面板挂在 body 下，scoped 样式管不到），用全局类 */
.homework-quick-deadline {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-2);
  align-items: center;
  padding: var(--nus-space-2) var(--nus-space-3);
}

.homework-quick-deadline__label {
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.homework-quick-deadline__chip {
  padding: 0 var(--nus-space-2);
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-secondary);
  cursor: pointer;
  background: transparent;
  border: 1px solid var(--nus-border);
  border-radius: var(--nus-radius-sm);
  transition: var(--nus-transition-colors);
}

.homework-quick-deadline__chip:hover {
  color: var(--nus-text);
  background: var(--nus-bg-hover);
}

.homework-quick-deadline__chip.is-active {
  color: var(--nus-primary);
  background: var(--nus-bg-selected);
  border-color: var(--nus-primary);
}
</style>
