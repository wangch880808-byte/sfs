import type { KeepClassOption } from './class-options';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CapacityCourseApi } from '#/api/capacity/course';
import type { EduHomeworkApi } from '#/api/edu/homework';

import { getDictOptions } from '@vben/hooks';

import { z } from '#/adapter/form';
import { getCoursePage } from '#/api/capacity/course';
import { getIndicatorList } from '#/api/capacity/indicator';
import { getSimpleClassList } from '#/api/edu/class';
import { getClassCourseListByCourse } from '#/api/edu/class-course';
import { getRangePickerDefaultProps } from '#/utils';
import { HOMEWORK_STATUS } from '#/utils/status';
import { isPublicCourse } from '#/views/capacity/course/course-type';

import { buildCourseClassOptions } from './class-options';

/** 实训任务类型字典 */
const EDU_HOMEWORK_TYPE_DICT = 'edu_homework_type';
/** 实训任务等级字典 */
const EDU_HOMEWORK_LEVEL_DICT = 'edu_homework_level';
/** 实训任务状态字典 */
export const EDU_HOMEWORK_STATUS_DICT = 'edu_homework_status';

export const SUBMISSION_MODE_OPTIONS = [
  { label: '纯文本', value: 'text' },
  { label: '图片', value: 'image' },
  { label: '音频', value: 'audio' },
  { label: '视频', value: 'video' },
  { label: '混合', value: 'mixed' },
];

export const GRADING_STRATEGY_OPTIONS = [
  { label: '仅人工批改', value: 'manual' },
  { label: 'AI 初评 + 教师复核', value: 'assist' },
];

export type GradingStrategy = 'assist' | 'manual';

/**
 * 由任务的 autoGrade、aiReviewMode 推出批改方式。
 * 早期任务可能是 autoGrade=1、aiReviewMode=0（原「AI 自动批改」，实际从未生效），
 * 现在一律按「AI 初评 + 教师复核」处理，与后端保存时的归一化一致。
 */
export function resolveGradingStrategy(
  autoGrade?: number,
  _aiReviewMode?: number,
): GradingStrategy {
  return Number(autoGrade) === 1 ? 'assist' : 'manual';
}

export function resolveGradingStrategyConfig(strategy?: string) {
  return strategy === 'assist'
    ? { aiReviewMode: 1, autoGrade: 1 }
    : { aiReviewMode: 0, autoGrade: 0 };
}

export function buildRequiredAttachmentTypesJson(submissionMode?: string) {
  switch (submissionMode) {
    case 'audio': {
      return JSON.stringify(['audio']);
    }
    case 'image': {
      return JSON.stringify(['image']);
    }
    case 'mixed': {
      return JSON.stringify(['image', 'audio', 'video']);
    }
    case 'video': {
      return JSON.stringify(['video']);
    }
    default: {
      return JSON.stringify([]);
    }
  }
}

export interface HomeworkRow extends EduHomeworkApi.Homework {
  courseName?: string;
  className?: string;
  indicatorFirstName?: string;
  indicatorSecondName?: string;
}

interface SelectOption {
  label: string;
  value: number;
}

let indicatorCache:
  | null
  | {
      id?: number;
      indicatorLevel: number;
      indicatorName: string;
      parentIndicatorId: number;
    }[] = null;

async function getIndicatorCache() {
  if (!indicatorCache) {
    indicatorCache = await getIndicatorList();
  }
  return indicatorCache ?? [];
}

export async function getFirstIndicatorOptions(): Promise<SelectOption[]> {
  const list = await getIndicatorCache();
  return list
    .filter((item) => Number(item.indicatorLevel) === 1)
    .map((item) => ({ label: item.indicatorName, value: Number(item.id) }));
}

export async function getSecondIndicatorOptions(
  parentId?: number,
): Promise<SelectOption[]> {
  if (!parentId) return [];
  const list = await getIndicatorCache();
  return list
    .filter(
      (item) =>
        Number(item.indicatorLevel) === 2 &&
        Number(item.parentIndicatorId) === parentId,
    )
    .map((item) => ({ label: item.indicatorName, value: Number(item.id) }));
}

export async function getIndicatorNameMap() {
  const list = await getIndicatorCache();
  return new Map(list.map((item) => [Number(item.id), item.indicatorName]));
}

let courseListCache:
  | null
  | Pick<
      CapacityCourseApi.Course,
      'courseName' | 'id' | 'isPublicCourse' | 'publicCourse'
    >[] = null;

async function getCourseCache() {
  if (!courseListCache) {
    const result = await getCoursePage({ pageNo: 1, pageSize: 200 });
    courseListCache = result.list ?? [];
  }
  return courseListCache;
}

export async function getCourseSelectOptions(): Promise<SelectOption[]> {
  const list = await getCourseCache();
  return list.map((item) => ({
    label: item.courseName,
    value: Number(item.id),
  }));
}

export async function getCourseNameMap() {
  const list = await getCourseCache();
  return new Map(list.map((item) => [Number(item.id), item.courseName]));
}

/** 课程是否公共课（从课程选项缓存里查；查不到时按专属课程） */
export async function isPublicCourseById(courseId?: number) {
  if (!courseId) return false;
  const list = await getCourseCache();
  const course = list.find((item) => Number(item.id) === Number(courseId));
  return course ? isPublicCourse(course) : false;
}

/**
 * 课程下可以布置任务的班级。公共课只含自己能操作（认领）的班级；
 * keep 是修改任务时任务原来的班级，即使已不能操作、已不在列表里也保留，免得只显示编号。
 * 没有说明是否公共课时，从课程选项里查。
 */
export async function getClassOptionsByCourse(
  courseId?: number,
  keep?: KeepClassOption,
): Promise<SelectOption[]> {
  if (!courseId) return [];
  const [list, publicCourse] = await Promise.all([
    getClassCourseListByCourse(courseId),
    keep?.classId && keep.publicCourse === undefined
      ? isPublicCourseById(courseId).catch(() => false)
      : Promise.resolve(keep?.publicCourse === true),
  ]);
  return buildCourseClassOptions(
    list ?? [],
    keep ? { ...keep, publicCourse } : undefined,
  );
}

let classNameMapCache: Map<number, string> | null = null;

export async function getClassNameMap() {
  if (!classNameMapCache) {
    const list = await getSimpleClassList();
    classNameMapCache = new Map(
      (list ?? []).map((item) => [Number(item.id), item.className]),
    );
  }
  return classNameMapCache;
}

/** 新增/修改的表单 */
export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'id',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'homeworkName',
      label: '任务名称',
      component: 'Input',
      componentProps: {
        maxLength: 200,
        placeholder: '请输入任务名称',
      },
      rules: 'required',
    },
    {
      fieldName: 'courseId',
      label: '指定课程',
      component: 'ApiSelect',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
      componentProps: {
        api: getCourseSelectOptions,
        labelField: 'label',
        valueField: 'value',
        placeholder: '请选择课程',
        allowClear: false,
      },
      rules: z.number({ message: '请选择课程' }).optional(),
    },
    {
      fieldName: 'classId',
      label: '指定班级',
      component: 'Select',
      componentProps: {
        placeholder: '请先选择课程',
        options: [],
      },
      // 必填：单选（编辑、从任务列表新建）是班级编号，多选（从课程详情新建）是编号数组；
      // 空值、null 与空数组都提示「请选择班级」。底层写成 optional，表单不会按规则推出默认值 0，
      // 外层 refine 让它仍按必填校验并显示星号
      rules: z
        .union([z.number(), z.array(z.number())], {
          errorMap: () => ({ message: '请选择班级' }),
        })
        .nullish()
        .refine(
          (value) =>
            value !== undefined &&
            value !== null &&
            !(Array.isArray(value) && value.length === 0),
          { message: '请选择班级' },
        ),
    },
    {
      fieldName: 'homeworkType',
      label: '任务类型',
      component: 'RadioGroup',
      componentProps: {
        options: getDictOptions(EDU_HOMEWORK_TYPE_DICT, 'number'),
        optionType: 'button',
      },
      rules: z.number().default(1),
    },
    {
      fieldName: 'homeworkLevel',
      label: '任务等级',
      component: 'Select',
      componentProps: {
        options: getDictOptions(EDU_HOMEWORK_LEVEL_DICT, 'number'),
        placeholder: '请选择任务等级',
      },
      rules: z.number({ message: '请选择任务等级' }).optional(),
    },
    {
      fieldName: 'indicatorFirstId',
      label: '一级指标',
      component: 'ApiSelect',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
      componentProps: {
        api: getFirstIndicatorOptions,
        labelField: 'label',
        valueField: 'value',
        placeholder: '请选择一级指标',
        allowClear: false,
      },
      rules: z.number({ message: '请选择一级指标' }).optional(),
    },
    {
      fieldName: 'indicatorSecondId',
      label: '二级指标',
      component: 'Select',
      componentProps: {
        placeholder: '请先选择一级指标',
        options: [],
      },
      // 一级指标不强制（没选时成绩不计入能力分析）；选了一级指标时二级指标必填
      dependencies: {
        triggerFields: ['indicatorFirstId'],
        rules: (values) => (values.indicatorFirstId ? 'selectRequired' : null),
      },
      rules: z.number({ message: '请选择二级指标' }).optional(),
    },
    {
      fieldName: 'deadline',
      label: '截止时间',
      component: 'DatePicker',
      componentProps: {
        showTime: true,
        format: 'YYYY-MM-DD HH:mm:ss',
        placeholder: '请选择截止时间',
      },
      rules: 'required',
    },
    {
      fieldName: 'gradingStrategy',
      label: '批改方式',
      component: 'RadioGroup',
      componentProps: {
        options: GRADING_STRATEGY_OPTIONS,
        optionType: 'button',
      },
      rules: z.string().default('manual'),
      formItemClass: 'col-span-2',
    },
    {
      fieldName: 'submissionMode',
      label: '提交模式',
      component: 'Select',
      componentProps: {
        options: SUBMISSION_MODE_OPTIONS,
        placeholder: '请选择提交模式',
      },
      rules: z.string().default('text'),
    },
    {
      fieldName: 'autoGrade',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
      rules: z.number().default(0),
    },
    {
      fieldName: 'aiReviewMode',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
      rules: z.number().default(0),
    },
    {
      fieldName: 'requiredAttachmentTypesJson',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'aiConfidenceThreshold',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
      rules: z.number().default(0.7),
    },
    {
      fieldName: 'passScore',
      label: '及格分数线',
      component: 'InputNumber',
      componentProps: {
        min: 0,
        max: 100,
        placeholder: '请输入及格分数线',
      },
      rules: z.number().default(60),
    },
    {
      fieldName: 'score',
      label: '满分值',
      component: 'InputNumber',
      componentProps: {
        min: 0,
        max: 100,
        placeholder: '请输入满分值',
      },
      rules: z.number().default(100),
    },
    {
      fieldName: 'requirement',
      label: '任务内容',
      component: 'RichTextarea',
      rules: 'required',
      formItemClass: 'col-span-2',
    },
    {
      fieldName: 'aiRubricJson',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'aiReferenceJson',
      label: '参考资料（可选）',
      component: 'Textarea',
      componentProps: {
        rows: 3,
        placeholder: '可填写朗读原文、标准答案、评分补充说明等，供 AI 初评参考',
      },
      formItemClass: 'col-span-2',
    },
  ];
}

/** 列表搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'homeworkName',
      label: '任务名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入任务名称',
        allowClear: true,
      },
    },
    {
      fieldName: 'courseId',
      label: '所属课程',
      component: 'ApiSelect',
      componentProps: {
        api: getCourseSelectOptions,
        labelField: 'label',
        valueField: 'value',
        placeholder: '请选择课程',
        allowClear: true,
      },
    },
    {
      fieldName: 'homeworkType',
      label: '任务类型',
      component: 'Select',
      componentProps: {
        options: getDictOptions(EDU_HOMEWORK_TYPE_DICT, 'number'),
        placeholder: '请选择任务类型',
        allowClear: true,
      },
    },
    {
      fieldName: 'homeworkLevel',
      label: '任务等级',
      component: 'Select',
      componentProps: {
        options: getDictOptions(EDU_HOMEWORK_LEVEL_DICT, 'number'),
        placeholder: '请选择任务等级',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        // 不再出现草稿（旧草稿显示为待发布，编辑保存即发布，不单独筛选）；已封存放在最后，
        // 选了已封存时列表自动带上已封存的任务（见 display.ts 的 resolveIncludeArchived）
        options: [
          ...getDictOptions(EDU_HOMEWORK_STATUS_DICT, 'number').filter(
            (item) =>
              Number(item.value) !== HOMEWORK_STATUS.ARCHIVED &&
              Number(item.value) !== HOMEWORK_STATUS.DRAFT,
          ),
          { label: '已封存', value: HOMEWORK_STATUS.ARCHIVED },
        ],
        placeholder: '请选择状态',
        allowClear: true,
      },
    },
    {
      fieldName: 'createTime',
      label: '创建时间',
      component: 'RangePicker',
      componentProps: {
        ...getRangePickerDefaultProps(),
        allowClear: true,
      },
    },
  ];
}

/**
 * 列表字段：状态紧跟任务名称，一眼能看到；状态用字典的圆点＋文字（edu_homework_status 是状态类字典），
 * 类型与等级是普通分类，按文字显示。
 */
export function useGridColumns(): VxeTableGridOptions<HomeworkRow>['columns'] {
  return [
    { type: 'checkbox', width: 40 },
    {
      field: 'id',
      title: '编号',
      width: 56,
    },
    {
      field: 'homeworkName',
      title: '任务名称',
      minWidth: 180,
      showOverflow: 'tooltip',
    },
    {
      field: 'status',
      title: '状态',
      width: 88,
      // 待发布（旧草稿）要加提示，其余按字典显示；见 index.vue 的 status 插槽
      slots: { default: 'status' },
    },
    {
      field: 'courseName',
      title: '所属课程',
      minWidth: 130,
      showOverflow: 'tooltip',
    },
    {
      field: 'className',
      title: '所属班级',
      minWidth: 150,
      showOverflow: 'tooltip',
    },
    {
      field: 'homeworkType',
      title: '任务类型',
      width: 88,
      cellRender: {
        name: 'CellDict',
        props: { type: EDU_HOMEWORK_TYPE_DICT },
      },
    },
    {
      field: 'homeworkLevel',
      title: '任务等级',
      width: 80,
      cellRender: {
        name: 'CellDict',
        props: { type: EDU_HOMEWORK_LEVEL_DICT },
      },
    },
    {
      field: 'deadline',
      title: '截止时间',
      width: 156,
      formatter: 'formatDateTime',
    },
    {
      field: 'createTime',
      title: '创建时间',
      width: 156,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 200,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
