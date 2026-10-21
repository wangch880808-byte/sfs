import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiPromptTemplateApi } from '#/api/ai/prompt';
import type { PopConfirmCopy } from '#/utils/confirm-copy';

import { formatDateTime } from '@vben/utils';

import { CONFIRM_CANCEL_TEXT } from '#/utils/confirm-copy';

/** 打开对比框时传入：提示词名称、内置默认内容、要对比的内容与它的叫法（「当前生效」「第 3 版」） */
export interface PromptDiffData {
  name: string;
  defaultContent: string;
  content: string;
  label: string;
}

/** 新版本对新的 AI 调用生效要多久（后端缓存的刷新间隔） */
export const PROMPT_EFFECT_HINT = '约 30 秒内对新的 AI 调用生效';

/** 提示词列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'name',
      title: '提示词',
      minWidth: 160,
    },
    {
      field: 'businessName',
      title: '所属业务',
      minWidth: 140,
    },
    {
      field: 'activeVersion',
      title: '生效版本',
      minWidth: 140,
      slots: { default: 'activeVersion' },
    },
    {
      field: 'sameAsDefault',
      title: '与内置默认',
      minWidth: 120,
      slots: { default: 'sameAsDefault' },
    },
    {
      field: 'activeTime',
      title: '启用时间',
      minWidth: 170,
      formatter: ({ cellValue }) =>
        cellValue ? formatDateTime(cellValue) : '—',
    },
    {
      title: '操作',
      width: 280,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}

/** 启用某个版本的确认：标题写对象和版本，正文写生效时间和怎么换回去 */
export function activateVersionConfirm(
  summary: Pick<AiPromptTemplateApi.Summary, 'name'>,
  version: Pick<AiPromptTemplateApi.Version, 'version'>,
): PopConfirmCopy {
  return {
    cancelText: CONFIRM_CANCEL_TEXT,
    description: `启用后${PROMPT_EFFECT_HINT}，正在进行的调用不受影响。之后可以随时启用其他版本。`,
    okText: '启用',
    title: `启用「${summary.name}」第 ${version.version} 版？`,
  };
}
