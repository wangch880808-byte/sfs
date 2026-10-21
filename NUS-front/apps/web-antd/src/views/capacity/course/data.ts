import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CapacityCourseApi } from '#/api/capacity/course';
import type { CapacityIndicatorApi } from '#/api/capacity/indicator';
import type { SystemUserApi } from '#/api/system/user';

import { CommonStatusEnum } from '@vben/constants';

import { z } from '#/adapter/form';
import { getIndicatorList } from '#/api/capacity/indicator';
import { getSimpleUserList } from '#/api/system/user';
import { getRangePickerDefaultProps } from '#/utils';

import { isPublicCourse } from './course-type';

export interface CourseRow extends CapacityCourseApi.Course {
  teacherName?: string;
  teachAssistantNames?: string;
}

interface SelectOption {
  label: string;
  value: number;
}

let indicatorListCache: CapacityIndicatorApi.Indicator[] | null = null;
let userListCache: null | SystemUserApi.User[] = null;
let teacherUserListCache: null | SystemUserApi.User[] = null;

const TEACHER_USER_TYPE = 1;

export const PUBLIC_COURSE_OPTIONS = [
  { label: '是', value: 0 },
  { label: '否', value: 1 },
];

export const PUBLIC_AUDIT_STATUS_OPTIONS = [
  { label: '无需审批', value: 0 },
  { label: '待审批', value: 1 },
  { label: '审批通过', value: 2 },
  { label: '审批驳回', value: 3 },
];

export function resolvePublicCourseLabel(value?: number) {
  return (
    PUBLIC_COURSE_OPTIONS.find((item) => item.value === value)?.label ?? '-'
  );
}

export function resolvePublicAuditStatusLabel(value?: number) {
  return (
    PUBLIC_AUDIT_STATUS_OPTIONS.find((item) => item.value === value)?.label ??
    '-'
  );
}

function resolveIndicatorLevelLabel(level?: number) {
  if (level === 1) {
    return '一级指标';
  }
  if (level === 2) {
    return '二级指标';
  }
  return '指标';
}

async function getIndicatorCache() {
  if (!indicatorListCache) {
    indicatorListCache = await getIndicatorList();
  }
  return indicatorListCache;
}

async function getUserCache() {
  if (!userListCache) {
    userListCache = await getSimpleUserList();
  }
  return userListCache;
}

async function getTeacherUserCache() {
  if (!teacherUserListCache) {
    teacherUserListCache = await getSimpleUserList({
      userType: TEACHER_USER_TYPE,
    });
  }
  return teacherUserListCache;
}

export async function getIndicatorSelectOptions(): Promise<SelectOption[]> {
  const indicators = await getIndicatorCache();
  return (indicators ?? [])
    .filter((item) => Number(item.indicatorLevel) === 1)
    .map((item) => ({
      label: `[${resolveIndicatorLevelLabel(item.indicatorLevel)}] ${item.indicatorName}`,
      value: Number(item.id),
    }));
}

export async function getUserSelectOptions(
  excludedUserId?: number,
): Promise<SelectOption[]> {
  const users = await getTeacherUserCache();
  return (users ?? [])
    .filter((item) => Number(item.id) !== excludedUserId)
    .map((item) => ({
      label: item.nickname,
      value: Number(item.id),
    }));
}

export async function getIndicatorNameMap() {
  const indicators = await getIndicatorCache();
  return new Map(
    (indicators ?? []).map((item) => [Number(item.id), item.indicatorName]),
  );
}

export async function getUserNameMap() {
  const users = await getUserCache();
  return new Map((users ?? []).map((item) => [Number(item.id), item.nickname]));
}

export function parseAssistantIds(value?: number[] | string) {
  if (!value) {
    return [] as number[];
  }
  if (Array.isArray(value)) {
    return [...new Set(value.map(Number).filter(Boolean))];
  }
  return [
    ...new Set(
      value
        .split(',')
        .map((item) => Number(item.trim()))
        .filter(Boolean),
    ),
  ];
}

export function stringifyAssistantIds(value?: number[] | string) {
  const assistantIds = parseAssistantIds(value);
  return assistantIds.length > 0 ? assistantIds.join(',') : undefined;
}

/**
 * 新增/修改的表单：名称、简介、图片在前，开设方式（关联指标、是否申请公共课、助教）在后。
 * 课程图片由表单弹窗用同名插槽渲染（上传或选默认图片），值仍写在 courseImageUrl。
 */
export function useFormSchema(currentUserId?: number): VbenFormSchema[] {
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
      fieldName: 'courseName',
      label: '课程名称',
      component: 'Input',
      componentProps: {
        maxLength: 255,
        placeholder: '请输入实训课程名称',
      },
      rules: 'required',
    },
    {
      fieldName: 'courseIntroduction',
      label: '课程简介',
      component: 'Textarea',
      componentProps: {
        maxLength: 1000,
        rows: 4,
        placeholder: '请输入课程简介',
      },
    },
    {
      fieldName: 'courseImageUrl',
      label: '课程图片',
      component: 'Input',
      // 控件较高，标签与第一行对齐
      labelClass: 'self-start pt-1',
    },
    {
      fieldName: 'indicatorId',
      label: '关联指标',
      component: 'ApiSelect',
      componentProps: {
        api: getIndicatorSelectOptions,
        labelField: 'label',
        valueField: 'value',
        placeholder: '请选择关联指标',
        allowClear: false,
      },
      rules: z.number({ message: '请选择关联指标' }).optional(),
    },
    {
      fieldName: 'isPublicCourse',
      label: '申请公共课',
      component: 'RadioGroup',
      componentProps: {
        options: PUBLIC_COURSE_OPTIONS,
        buttonStyle: 'solid',
        optionType: 'button',
      },
      rules: z.number().default(1),
    },
    {
      fieldName: 'teachAssistantIds',
      label: '助教团队',
      component: 'ApiSelect',
      componentProps: {
        api: () => getUserSelectOptions(currentUserId),
        labelField: 'label',
        valueField: 'value',
        mode: 'multiple',
        placeholder: '请选择助教教师团队',
        allowClear: true,
      },
    },
    {
      fieldName: 'teacherId',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Input',
      dependencies: { triggerFields: [''], show: () => false },
      rules: z.number().default(CommonStatusEnum.ENABLE),
    },
  ];
}

/** 列表搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'courseName',
      label: '课程名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入课程名称',
        allowClear: true,
      },
    },
    {
      fieldName: 'indicatorId',
      label: '关联指标',
      component: 'ApiSelect',
      componentProps: {
        api: getIndicatorSelectOptions,
        labelField: 'label',
        valueField: 'value',
        placeholder: '请选择关联指标',
        allowClear: true,
      },
    },
    {
      fieldName: 'teacherId',
      label: '所属教师',
      component: 'ApiSelect',
      componentProps: {
        api: getUserSelectOptions,
        labelField: 'label',
        valueField: 'value',
        placeholder: '请选择所属教师',
        allowClear: true,
      },
    },
    {
      fieldName: 'isPublicCourse',
      label: '公共课程',
      component: 'Select',
      componentProps: {
        options: PUBLIC_COURSE_OPTIONS,
        placeholder: '请选择是否公共课程',
        allowClear: true,
      },
    },
    {
      fieldName: 'publicAuditStatus',
      label: '公开审批',
      component: 'Select',
      componentProps: {
        options: PUBLIC_AUDIT_STATUS_OPTIONS,
        placeholder: '请选择审批状态',
        allowClear: true,
      },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        options: [
          { label: '正常', value: 0 },
          { label: '已封存', value: 1 },
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

/** 列表字段 */
export function useGridColumns(): VxeTableGridOptions<CourseRow>['columns'] {
  return [
    { type: 'checkbox', width: 40 },
    {
      field: 'id',
      title: '编号',
      width: 90,
    },
    {
      field: 'courseImageUrl',
      title: '课程图片',
      width: 96,
      cellRender: {
        name: 'CellImage',
        props: {
          width: 44,
          height: 44,
        },
      },
    },
    {
      field: 'courseInfo',
      title: '课程信息',
      minWidth: 280,
      slots: { default: 'courseInfo' },
    },
    {
      field: 'indicatorName',
      title: '关联指标',
      minWidth: 180,
      showOverflow: 'tooltip',
    },
    {
      field: 'publicCourse',
      title: '公共课程',
      width: 100,
      formatter: ({ row }) =>
        resolvePublicCourseLabel(isPublicCourse(row) ? 0 : 1),
    },
    {
      field: 'teacherName',
      title: '所属教师',
      minWidth: 120,
      showOverflow: 'tooltip',
    },
    {
      field: 'teachAssistantNames',
      title: '助教团队',
      minWidth: 180,
      showOverflow: 'tooltip',
    },
    {
      field: 'status',
      title: '状态',
      width: 90,
      formatter: ({ cellValue }) =>
        Number(cellValue) === 1 ? '已封存' : '正常',
    },
    {
      field: 'createTime',
      title: '创建时间',
      minWidth: 180,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 170,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
