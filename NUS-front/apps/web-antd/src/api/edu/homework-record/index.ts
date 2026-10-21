import type { PageParam, PageResult, RequestClientConfig } from '@vben/request';

import { LONG_REQUEST_TIMEOUT, requestClient } from '#/api/request';

export namespace EduHomeworkRecordApi {
  /** 提交与评分轨迹，按发生时间正序返回 */
  export interface HistoryItem {
    createTime: number | string;
    eventType: string;
    fromStatus?: null | number;
    toStatus?: null | number;
    score?: null | number;
    comment?: null | string;
    redoDeadline?: null | number | string;
    operatorName?: null | string;
  }

  /** 实训任务记录 */
  export interface HomeworkRecord {
    id?: number;
    homeworkId: number;
    studentId: number;
    studentName?: string;
    studentNo?: string;
    classRank?: number;
    rankTotalStudents?: number;
    content?: string;
    attachmentUrls?: string;
    score?: number;
    comment?: string;
    correctType?: number;
    status?: number;
    aiReviewStatus?: number;
    aiReviewScore?: number;
    aiReviewComment?: string;
    aiReviewConfidence?: number;
    submitTime?: string;
    correctTime?: string;
    /** 打回重做期限（仅已打回的记录有值） */
    redoDeadline?: number | string;
    redoCount?: number;
    createTime?: Date | string;
    /** 更新时间；批改、打回时作为 expectedUpdateTime 传回 */
    updateTime?: number | string;
  }

  /** 批改队列中的一份提交：只有列表需要的字段，详情另取 */
  export interface QueueItem {
    id: number;
    homeworkId: number;
    studentId: number;
    studentName?: string;
    studentNo?: string;
    status: number;
    score?: null | number;
    submitTime?: number | string;
    correctTime?: null | number | string;
    redoDeadline?: null | number | string;
    redoCount?: number;
    updateTime?: number | string;
    aiReviewStatus?: null | number;
  }

  /** 批改队列：任务下全部可批改的提交，不分页；total 与 list 长度一致 */
  export interface QueueResp {
    total: number;
    list: QueueItem[];
  }

  /** 批改、打回、撤销后的记录；undoToken 为空表示不能撤销 */
  export interface GradeResp {
    id: number;
    status: number;
    score?: null | number;
    comment?: null | string;
    correctType?: null | number;
    correctTime?: null | number | string;
    redoCount?: number;
    redoDeadline?: null | number | string;
    updateTime?: number | string;
    aiReviewStatus?: null | number;
    undoToken?: null | number;
    undoDeadline?: null | number | string;
    /** 还能撤销的秒数，按它倒计时，不依赖本机时钟 */
    undoSeconds?: null | number;
  }

  /** 实训任务记录分页查询参数 */
  export interface HomeworkRecordPageReqVO extends PageParam {
    homeworkId?: number;
    studentId?: number;
    status?: number;
  }

  /** 批改请求 */
  export interface CorrectReqVO {
    id: number;
    score: number;
    comment: string;
    correctType?: number;
    /** 读取时的更新时间；记录已被他人更新时后端拒绝 */
    expectedUpdateTime?: number | string;
  }

  /** 打回重做请求；任务已截止时必须提供晚于当前时间的重做期限 */
  export interface RedoReqVO {
    id: number;
    /** 重做期限，格式 YYYY-MM-DD HH:mm:ss */
    redoDeadline?: string;
    /** 读取时的更新时间；记录已被他人更新时后端拒绝 */
    expectedUpdateTime?: number | string;
  }

  /** 批量打回重做请求 */
  export interface RedoBatchReqVO {
    ids: number[];
    /** 重做期限，格式 YYYY-MM-DD HH:mm:ss */
    redoDeadline?: string;
  }
}

/** 提交实训任务 */
export function submitHomeworkRecord(
  data: EduHomeworkRecordApi.HomeworkRecord,
) {
  return requestClient.post('/edu/homework-record/submit', data);
}

/** 更新实训任务记录 */
export function updateHomeworkRecord(
  data: EduHomeworkRecordApi.HomeworkRecord,
) {
  return requestClient.put('/edu/homework-record/update', data);
}

/** 查询实训任务记录详情；config 可传 silentError，由页面就地提示 */
export function getHomeworkRecord(id: number, config?: RequestClientConfig) {
  return requestClient.get<EduHomeworkRecordApi.HomeworkRecord>(
    `/edu/homework-record/get?id=${id}`,
    config,
  );
}

/** 查询实训任务记录分页 */
export function getHomeworkRecordPage(
  params: EduHomeworkRecordApi.HomeworkRecordPageReqVO,
) {
  return requestClient.get<PageResult<EduHomeworkRecordApi.HomeworkRecord>>(
    '/edu/homework-record/page',
    { params },
  );
}

/** 待批改列表 */
export function getHomeworkRecordCorrectPage(
  params: EduHomeworkRecordApi.HomeworkRecordPageReqVO,
) {
  return requestClient.get<PageResult<EduHomeworkRecordApi.HomeworkRecord>>(
    '/edu/homework-record/correct-page',
    { params },
  );
}

/** 查询指定学生记录的提交与评分轨迹；需要 edu:homework-record:correct 权限 */
export function getHomeworkRecordHistory(
  recordId: number,
  config?: RequestClientConfig,
) {
  return requestClient.get<EduHomeworkRecordApi.HistoryItem[]>(
    '/edu/homework-record/history',
    { ...config, params: { recordId } },
  );
}

/** 批改队列：任务下全部可批改的提交（不含未提交），不分页、不截断 */
export function getHomeworkRecordGradingQueue(homeworkId: number) {
  return requestClient.get<EduHomeworkRecordApi.QueueResp>(
    '/edu/homework-record/grading-queue',
    { params: { homeworkId } },
  );
}

/** 批改实训任务；返回批改后的记录与撤销凭据 */
export function correctHomeworkRecord(
  data: EduHomeworkRecordApi.CorrectReqVO,
  config?: RequestClientConfig,
) {
  return requestClient.put<EduHomeworkRecordApi.GradeResp>(
    '/edu/homework-record/correct',
    data,
    config,
  );
}

/** 打回重做（单个）；返回打回后的记录与撤销凭据 */
export function redoHomeworkRecord(
  data: EduHomeworkRecordApi.RedoReqVO,
  config?: RequestClientConfig,
) {
  return requestClient.put<EduHomeworkRecordApi.GradeResp>(
    '/edu/homework-record/redo',
    data,
    config,
  );
}

/** 撤销期内撤销自己的批改或打回；返回恢复后的记录 */
export function undoHomeworkGrading(
  undoToken: number,
  config?: RequestClientConfig,
) {
  return requestClient.put<EduHomeworkRecordApi.GradeResp>(
    '/edu/homework-record/undo',
    { undoToken },
    config,
  );
}

/** 批量打回重做 */
export function redoHomeworkRecordBatch(
  data: EduHomeworkRecordApi.RedoBatchReqVO,
) {
  return requestClient.put('/edu/homework-record/redo-batch', data);
}

/** 导出任务成绩册 */
export function exportHomeworkGradebook(homeworkId: number) {
  return requestClient.download('/edu/homework-record/gradebook/export', {
    params: { homeworkId },
    timeout: LONG_REQUEST_TIMEOUT,
  });
}
