import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace AiCallLogApi {
  /** 一次 AI 调用 */
  export interface CallLog {
    id: number;
    businessCode: string; // 业务编码
    businessName: string; // 业务名称
    modelId?: number;
    modelName?: string;
    platform?: string;
    bizId?: number | string; // 业务对象编号
    operatorId?: number;
    operatorName?: string;
    background?: boolean; // 后台任务发起
    promptTokens?: null | number; // 输入令牌；为空表示服务商没有返回用量
    completionTokens?: null | number; // 输出令牌
    totalTokens?: null | number; // 令牌合计
    durationMs: number; // 耗时（毫秒）
    success: boolean;
    errorType?: string; // 失败类型
    errorMessage?: string; // 错误摘要
    createTime: string;
  }

  /** 分页查询条件 */
  export interface PageReq extends PageParam {
    businessCode?: string;
    modelId?: number;
    modelName?: string;
    success?: boolean;
    createTime?: string[];
  }

  /** 按天、按业务的汇总 */
  export interface DailySummary {
    date: string; // YYYY-MM-DD
    businessCode: string;
    businessName: string;
    callCount: number;
    failedCount: number;
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
  }

  /** 汇总查询条件：不给时间时默认最近 7 天，最长 93 天 */
  export interface DailySummaryReq {
    businessCode?: string;
    createTime?: string[];
  }
}

/** 调用日志分页 */
export function getCallLogPage(params: AiCallLogApi.PageReq) {
  return requestClient.get<PageResult<AiCallLogApi.CallLog>>(
    '/ai/call-log/page',
    { params },
  );
}

/** 按天汇总；页面自己显示错误时传 silentError */
export function getCallLogDailySummary(
  params: AiCallLogApi.DailySummaryReq,
  options: { silentError?: boolean } = {},
) {
  return requestClient.get<AiCallLogApi.DailySummary[]>(
    '/ai/call-log/daily-summary',
    { params, ...options },
  );
}
