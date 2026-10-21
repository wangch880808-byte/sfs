import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiCallLogApi } from '#/api/ai/call-log';

import { getModelSimpleList } from '#/api/ai/model/model';
import { getRangePickerDefaultProps } from '#/utils';
import { formatAiDuration, formatAiTokens } from '#/utils/status-ai-governance';

/** 业务筛选：只显示业务名称，值是业务编码 */
export const AI_CALL_BUSINESS_OPTIONS = [
  { label: '任务 AI 初评', value: 'HOMEWORK_REVIEW' },
  { label: '任务题目改写', value: 'HOMEWORK_REWRITE' },
  { label: '班级学情分析', value: 'CLASS_ANALYSIS' },
  { label: '学生分析与评语', value: 'STUDENT_ANALYSIS' },
  { label: '教育业务对话', value: 'EDU_CHAT' },
  { label: 'AI 对话', value: 'AI_CHAT' },
  { label: '模型连接测试', value: 'MODEL_TEST' },
  { label: '其他', value: 'UNKNOWN' },
];

/** 结果筛选：下拉框的值用字符串，查询时换成布尔值 */
const RESULT_OPTIONS = [
  { label: '成功', value: 'true' },
  { label: '失败', value: 'false' },
];

/** 搜索表单的值 */
export interface CallLogFilter {
  businessCode?: string;
  createTime?: string[];
  modelId?: number;
  success?: string;
}

/** 搜索表单的值换成接口参数 */
export function toCallLogQuery(
  values: CallLogFilter | null | undefined,
): Omit<AiCallLogApi.PageReq, 'pageNo' | 'pageSize'> {
  const { businessCode, createTime, modelId, success } = values ?? {};
  return {
    businessCode: businessCode || undefined,
    createTime: createTime?.length === 2 ? createTime : undefined,
    modelId: modelId ?? undefined,
    success:
      success === undefined || success === '' ? undefined : success === 'true',
  };
}

/** 汇总的时间范围文字：没选时间时后端按最近 7 天汇总 */
export function summaryRangeText(createTime?: string[]): string {
  if (createTime?.length !== 2) {
    return '最近 7 天';
  }
  const [start = '', end = ''] = createTime.map((value) =>
    String(value).slice(0, 10),
  );
  return start === end ? start : `${start} 至 ${end}`;
}

/** 业务编码对应的名称；不认识的编码不显示 */
export function businessLabelOf(code?: string): string {
  return (
    AI_CALL_BUSINESS_OPTIONS.find((option) => option.value === code)?.label ??
    ''
  );
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'createTime',
      label: '调用时间',
      component: 'RangePicker',
      componentProps: {
        ...getRangePickerDefaultProps(),
        allowClear: true,
      },
    },
    {
      fieldName: 'businessCode',
      label: '业务',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: AI_CALL_BUSINESS_OPTIONS,
        placeholder: '全部业务',
      },
    },
    {
      fieldName: 'modelId',
      label: '模型',
      component: 'ApiSelect',
      componentProps: {
        allowClear: true,
        api: () => getModelSimpleList(),
        labelField: 'name',
        valueField: 'id',
        placeholder: '全部模型',
      },
    },
    {
      fieldName: 'success',
      label: '结果',
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: RESULT_OPTIONS,
        placeholder: '全部结果',
      },
    },
  ];
}

const tokenColumn = (field: string, title: string) => ({
  field,
  title,
  minWidth: 100,
  align: 'right' as const,
  formatter: ({ cellValue }: { cellValue: null | number | undefined }) =>
    formatAiTokens(cellValue),
});

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions<AiCallLogApi.CallLog>['columns'] {
  return [
    {
      field: 'createTime',
      title: '调用时间',
      minWidth: 170,
      formatter: 'formatDateTime',
    },
    {
      field: 'businessName',
      title: '业务',
      minWidth: 130,
      formatter: ({ cellValue, row }) =>
        cellValue || businessLabelOf(row.businessCode) || '—',
    },
    {
      field: 'modelName',
      title: '模型',
      minWidth: 140,
      showOverflow: 'tooltip',
      formatter: ({ cellValue }) => cellValue || '—',
    },
    {
      field: 'operatorName',
      title: '发起人',
      minWidth: 120,
      slots: { default: 'operator' },
    },
    tokenColumn('promptTokens', '输入令牌'),
    tokenColumn('completionTokens', '输出令牌'),
    tokenColumn('totalTokens', '令牌合计'),
    {
      field: 'durationMs',
      title: '耗时',
      minWidth: 90,
      align: 'right',
      formatter: ({ cellValue }) => formatAiDuration(cellValue),
    },
    {
      field: 'success',
      title: '结果',
      minWidth: 150,
      slots: { default: 'result' },
    },
    {
      field: 'errorMessage',
      title: '错误摘要',
      minWidth: 220,
      showOverflow: 'tooltip',
      formatter: ({ cellValue }) => cellValue || '—',
    },
  ];
}
