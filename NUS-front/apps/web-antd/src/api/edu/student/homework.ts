import type { PageParam, PageResult } from '@vben/request';

import type { EduHomeworkRecordApi } from '#/api/edu/homework-record';
import type { AxiosProgressEvent } from '#/api/infra/file';

import { requestClient } from '#/api/request';

/** 作业附件可能是较大的音视频，单独放宽上传超时（默认 30 秒） */
const HOMEWORK_ATTACHMENT_UPLOAD_TIMEOUT = 10 * 60 * 1000;

export namespace StudentHomeworkApi {
  /** 当前学生的提交与评分轨迹 */
  export type HistoryItem = EduHomeworkRecordApi.HistoryItem;

  export interface HomeworkItem {
    id?: number;
    homeworkId: number;
    homeworkName: string;
    homeworkType: number;
    homeworkLevel: number;
    courseId: number;
    courseName?: string;
    deadline: number | string;
    status: number;
    score: number;
    passScore: number;
    myScore?: number;
    recordId?: number;
    submitTime?: string;
    /** 打回重做期限 */
    redoDeadline?: number | string;
    /** 教师为你设置的个别延期（晚于任务截止时间时才有） */
    personalDeadline?: null | number | string;
    /** 实际截止时间（后端计算：重做期限、个别延期与任务截止时间） */
    effectiveDeadline?: null | number | string;
    /** 往期任务（只读）：班级、课程或班课已封存，或任务已封存 */
    historyOnly?: boolean;
    /** 任务所在等级还没解锁（前置等级未达标）；旧版后端没有，按已解锁处理 */
    locked?: boolean;
    /** 没解锁的原因，与课程等级解锁进度里的说明一致 */
    lockReason?: string;
  }

  /** 任务范围：当前任务（默认）、往期任务（只读） */
  export type HomeworkScope = 'current' | 'history';

  export interface HomeworkPageReqVO extends PageParam {
    courseId?: number;
    status?: number;
    scope?: HomeworkScope;
  }

  export interface HomeworkDetail {
    homeworkId: number;
    homeworkName: string;
    homeworkType: number;
    homeworkLevel: number;
    courseId?: number;
    courseName?: string;
    requirement: string;
    submissionMode?: string;
    aiReviewMode?: number;
    requiredAttachmentTypesJson?: string;
    deadline: number | string;
    /** 打回重做期限 */
    redoDeadline?: number | string;
    /** 有效截止时间（已打回时为重做期限，有个人延期时为延期后的时间，否则为任务截止时间） */
    effectiveDeadline?: number | string;
    /** 教师给你单独延期后的截止时间（可空；旧版后端没有） */
    personalDeadline?: null | number | string;
    /** 附件上限与允许的类型（旧版后端没有，前端用默认值） */
    attachmentLimits?: {
      allowedTypes?: string[];
      maxCount?: number;
      maxFileSizeMb?: number;
      maxTotalSizeMb?: number;
    };
    /** 当前能否提交或重新提交 */
    canSubmit?: boolean;
    /** 往期任务（只读）：班级、课程或班课已封存，或任务已封存 */
    historyOnly?: boolean;
    passScore: number;
    score: number;
    status: number;
    recordId?: number;
    content?: string;
    attachmentUrls?: string;
    submitTime?: string;
    myScore?: number;
    comment?: string;
    correctType?: number;
    correctTime?: string;
  }

  export interface SubmitReqVO {
    homeworkId: number;
    content: string;
    attachmentUrls?: string;
  }

  export interface UpdateSubmitReqVO {
    id: number;
    content: string;
    attachmentUrls?: string;
  }
}

/** 获取实训任务分页列表 */
export function getStudentHomeworkPage(
  params: StudentHomeworkApi.HomeworkPageReqVO,
) {
  return requestClient.get<PageResult<StudentHomeworkApi.HomeworkItem>>(
    '/edu/student/homework/list',
    { params },
  );
}

/** 获取实训任务详情（页面自己显示读取失败、等级未解锁的原因时传 silentError: true） */
export function getStudentHomeworkDetail(
  homeworkId: number,
  options?: { silentError?: boolean },
) {
  return requestClient.get<StudentHomeworkApi.HomeworkDetail>(
    `/edu/student/homework/get?homeworkId=${homeworkId}`,
    options,
  );
}

/** 获取当前学生在指定任务中的提交与评分轨迹 */
export function getStudentHomeworkHistory(
  homeworkId: number,
  options?: { silentError?: boolean },
) {
  return requestClient.get<StudentHomeworkApi.HistoryItem[]>(
    '/edu/student/homework/history',
    { params: { homeworkId }, ...options },
  );
}

/** 提交实训任务 */
export function submitHomework(data: StudentHomeworkApi.SubmitReqVO) {
  return requestClient.post('/edu/student/homework/submit', data);
}

/** 更新实训任务提交 */
export function updateHomeworkSubmit(
  data: StudentHomeworkApi.UpdateSubmitReqVO,
) {
  return requestClient.put('/edu/student/homework/update', data);
}

/**
 * 上传作业附件（学生专用接口：按任务目录保存，并校验任务分配与截止时间）
 * @returns 平台文件地址
 */
export function uploadHomeworkAttachment(
  homeworkId: number,
  file: File,
  onUploadProgress?: AxiosProgressEvent,
  options?: { silentError?: boolean },
) {
  return requestClient.upload<string>(
    '/edu/student/homework/attachment/upload',
    { file, homeworkId },
    {
      onUploadProgress,
      timeout: HOMEWORK_ATTACHMENT_UPLOAD_TIMEOUT,
      ...options,
    },
  );
}

/** 通过鉴权接口读取作业附件 */
export function downloadHomeworkAttachment(url: string) {
  return requestClient.download<Blob>(
    '/edu/student/homework/attachment/download',
    { params: { url } },
  );
}
