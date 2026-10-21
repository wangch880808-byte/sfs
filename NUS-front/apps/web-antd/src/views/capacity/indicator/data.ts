import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CapacityIndicatorApi } from '#/api/capacity/indicator';

import { CommonStatusEnum, DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { z } from '#/adapter/form';

export const FIRST_LEVEL = 1;
export const SECOND_LEVEL = 2;

export interface IndicatorListRow extends CapacityIndicatorApi.Indicator {
  secondaryCount?: number;
  secondaryWeightTotal?: number;
}

/** 权重显示为两位小数的百分数（表格、合计与重设弹窗共用，配等宽数字） */
export function formatIndicatorWeight(value?: null | number | string) {
  return `${Number(value || 0).toFixed(2)}%`;
}

export function resolveIndicatorLevelLabel(level?: number) {
  if (level === FIRST_LEVEL) {
    return '一级指标';
  }
  if (level === SECOND_LEVEL) {
    return '二级指标';
  }
  return '-';
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
      fieldName: 'indicatorLevel',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'parentIndicatorId',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'indicatorLevelText',
      label: '指标级别',
      component: 'Input',
      componentProps: {
        disabled: true,
      },
    },
    {
      fieldName: 'parentIndicatorName',
      label: '父指标',
      component: 'Input',
      componentProps: {
        disabled: true,
      },
      dependencies: {
        triggerFields: ['indicatorLevel'],
        show: (values) => values.indicatorLevel === SECOND_LEVEL,
      },
    },
    {
      fieldName: 'indicatorName',
      label: '指标名称',
      component: 'Input',
      componentProps: {
        maxLength: 255,
        placeholder: '请输入指标名称',
      },
      rules: 'required',
    },
    {
      fieldName: 'indicatorDetails',
      label: '指标详情',
      component: 'Textarea',
      componentProps: {
        maxLength: 255,
        placeholder: '请输入指标详情',
      },
    },
    {
      fieldName: 'weight',
      label: '权重（%）',
      component: 'InputNumber',
      componentProps: {
        min: 0,
        max: 100,
        precision: 2,
        step: 0.01,
        placeholder: '请输入权重',
      },
      help: '一级指标总权重不超过 100%，每个一级指标下的二级指标总权重也不超过 100%',
      rules: z
        .number({ message: '请输入权重' })
        .min(0, '权重不能小于 0')
        .max(100, '权重不能超过 100'),
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'RadioGroup',
      componentProps: {
        options: getDictOptions(DICT_TYPE.COMMON_STATUS, 'number'),
        buttonStyle: 'solid',
        optionType: 'button',
      },
      rules: z.number().default(CommonStatusEnum.ENABLE),
    },
  ];
}

/** 指标列左对齐；权重与数量是数字，右对齐 */
function useCommonColumns(): VxeTableGridOptions<IndicatorListRow>['columns'] {
  return [
    {
      field: 'indicatorInfo',
      title: '指标',
      minWidth: 200,
      align: 'left',
      showOverflow: false,
      slots: { default: 'indicatorInfo' },
    },
    {
      field: 'weight',
      title: '权重',
      width: 88,
      align: 'right',
      headerAlign: 'right',
      formatter: ({ cellValue }) => formatIndicatorWeight(cellValue),
    },
  ];
}

export function useFirstLevelGridColumns(): VxeTableGridOptions<IndicatorListRow>['columns'] {
  return [
    ...(useCommonColumns() ?? []),
    {
      field: 'secondaryCount',
      title: '二级指标数',
      width: 92,
      align: 'right',
      headerAlign: 'right',
      formatter: ({ cellValue }) => Number(cellValue || 0),
    },
    {
      title: '操作',
      width: 140,
      align: 'left',
      slots: { default: 'actions' },
    },
  ];
}

export function useSecondLevelGridColumns(): VxeTableGridOptions<IndicatorListRow>['columns'] {
  return [
    ...(useCommonColumns() ?? []),
    {
      title: '操作',
      width: 140,
      align: 'left',
      slots: { default: 'actions' },
    },
  ];
}
