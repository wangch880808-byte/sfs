import type { PageParam, PageResult } from '@vben/request';

import { parseAiTaskStartResponse } from '#/api/edu/ai-review/contracts';
import { LONG_REQUEST_TIMEOUT, requestClient } from '#/api/request';

export namespace EduHomeworkApi {
  /** 实训任务信息 */
  export interface Homework {
    id?: number;
    homeworkName: string;
    homeworkType: number;
    homeworkLevel: number;
    courseId: number;
    classId: number;
    semesterName?: string;
    indicatorFirstId?: number;
    indicatorSecondId?: number;
    requirement: string;
    deadline: string;
    autoGrade: number;
    submissionMode?: string;
    aiReviewMode?: number;
    requiredAttachmentTypesJson?: string;
    aiRubricJson?: string;
    aiReferenceJson?: string;
    aiConfidenceThreshold?: number;
    passScore: number;
    score: number;
    status?: number;
    createTime?: Date | string;
    /** 当前用户能否批改、修改这个任务（只有布置者能）；旧版后端没有，按能处理 */
    canGrade?: boolean;
    /** 新建时指定的班课（班级与课程的关联）；后端校验它与 courseId、classId、学期一致 */
    classCourseId?: number;
    /** 复制来源任务 */
    sourceHomeworkId?: number;
  }

  /** 实训任务分页查询参数 */
  export interface HomeworkPageReqVO extends PageParam {
    includeArchived?: boolean;
    homeworkName?: string;
    homeworkType?: number;
    homeworkLevel?: number;
    courseId?: number;
    classId?: number;
    semesterName?: string;
    status?: number;
    createTime?: string[];
  }

  /** 实训任务统计 */
  export interface HomeworkStatistics {
    totalCount: number;
    submittedCount: number;
    correctedCount: number;
    passCount: number;
    /** 只读统计：任务已封存，或它所在的班级、课程、班课已封存（旧版后端没有） */
    readOnly?: boolean;
  }

  /** 删除任务的影响范围 */
  export interface HomeworkRemoveImpact {
    /** 要删除的任务数 */
    homeworkCount: number;
    /** 这些任务里已提交的记录数 */
    submittedCount: number;
    /** 分配到的学生数 */
    studentCount: number;
  }

  export interface HomeworkAiCreatePreviewReqVO {
    clientRequestId: string;
    courseId: number;
    classIds: number[];
    indicatorSecondId: number;
    questionCount: number;
    homeworkType: number;
    homeworkLevel: number;
    deadline: string;
    autoGrade: number;
    submissionMode: string;
    aiReviewMode: number;
    passScore: number;
    score: number;
  }

  export interface HomeworkAiCreateTaskStatusRespVO {
    taskId: number;
    status: 'FAILED' | 'PENDING' | 'RUNNING' | 'SUCCEEDED';
    progress: number;
    errorMessage?: string;
    preview?: HomeworkAiCreatePreviewRespVO;
  }

  export interface HomeworkAiCreatePreviewItem {
    previewItemId: string;
    sourceQuestionId: number;
    sourceTitle?: string;
    sourceRequirement?: string;
    homeworkName?: string;
    requirement?: string;
    success?: boolean;
    errorMessage?: string;
    /** 正在后台重新生成；为 true 时轮询任务状态取结果 */
    regenerating?: boolean;
  }

  export interface HomeworkAiCreatePreviewRespVO {
    previewId: string;
    questionCount: number;
    classCount: number;
    items: HomeworkAiCreatePreviewItem[];
  }

  export interface HomeworkAiCreateRegenerateReqVO {
    previewId: string;
    previewItemId: string;
  }

  export interface HomeworkAiCreateConfirmReqVO {
    previewId: string;
    items: Array<{
      homeworkName: string;
      previewItemId: string;
      requirement: string;
    }>;
  }

  export interface HomeworkAiCreateConfirmRespVO {
    questionCount: number;
    classCount: number;
    createdCount: number;
  }
}

/** 创建实训任务 */
export function createHomework(data: EduHomeworkApi.Homework) {
  return requestClient.post('/edu/homework/create', data);
}

/** 修改实训任务 */
export function updateHomework(data: EduHomeworkApi.Homework) {
  return requestClient.put('/edu/homework/update', data);
}

/** 封存实训任务（接口名沿用 delete，只改状态，可以恢复） */
export function deleteHomework(id: number) {
  return requestClient.delete(`/edu/homework/delete?id=${id}`);
}

/** 恢复实训任务原状态 */
export function restoreHomework(id: number) {
  return requestClient.put(`/edu/homework/restore?id=${id}`);
}

/** 删除前查询影响范围：任务数、已提交的记录数、分配到的学生数 */
export function getHomeworkRemoveImpact(ids: number[]) {
  return requestClient.get<EduHomeworkApi.HomeworkRemoveImpact>(
    '/edu/homework/remove-impact',
    { params: { ids: ids.join(',') } },
  );
}

/** 删除实训任务及其提交、成绩，不能恢复 */
export function removeHomework(id: number) {
  return requestClient.delete<boolean>(`/edu/homework/remove?id=${id}`);
}

/** 批量删除实训任务及其提交、成绩，不能恢复（最多 100 个） */
export function removeHomeworkList(ids: number[]) {
  return requestClient.delete<boolean>(
    `/edu/homework/remove-list?ids=${ids.join(',')}`,
  );
}

/** 查询实训任务详情 */
export function getHomework(id: number) {
  return requestClient.get<EduHomeworkApi.Homework>(
    `/edu/homework/get?id=${id}`,
  );
}

/** 查询实训任务分页 */
export function getHomeworkPage(params: EduHomeworkApi.HomeworkPageReqVO) {
  return requestClient.get<PageResult<EduHomeworkApi.Homework>>(
    '/edu/homework/page',
    { params },
  );
}

/** 查询实训任务统计 */
export function getHomeworkStatistics(homeworkId: number) {
  return requestClient.get<EduHomeworkApi.HomeworkStatistics>(
    `/edu/homework/statistics?homeworkId=${homeworkId}`,
  );
}

/** AI新建任务 - 生成题目预览 */
export function startAiHomeworkPreviewTask(
  data: EduHomeworkApi.HomeworkAiCreatePreviewReqVO,
) {
  return requestClient
    .post<{ taskId: number }>('/edu/homework/ai-create/preview-task', data)
    .then(parseAiTaskStartResponse);
}

export function getAiHomeworkPreviewTaskStatus(taskId: number) {
  return requestClient.get<EduHomeworkApi.HomeworkAiCreateTaskStatusRespVO>(
    '/edu/homework/ai-create/preview-task/status',
    { params: { taskId } },
  );
}

/** AI新建任务 - 轮询单题重新生成结果；出错不弹全局提示，由调用方处理 */
export async function getAiHomeworkPreviewItem(
  taskId: number,
  previewItemId: string,
) {
  const status =
    await requestClient.get<EduHomeworkApi.HomeworkAiCreateTaskStatusRespVO>(
      '/edu/homework/ai-create/preview-task/status',
      { params: { taskId }, silentError: true },
    );
  return status.preview?.items.find(
    (item) => item.previewItemId === previewItemId,
  );
}

/** AI新建任务 - 单题重新生成（后台执行，返回的题目带 regenerating 标记） */
export function regenerateAiHomeworkTaskQuestion(
  data: EduHomeworkApi.HomeworkAiCreateRegenerateReqVO,
) {
  return requestClient.post<EduHomeworkApi.HomeworkAiCreatePreviewItem>(
    '/edu/homework/ai-create/regenerate',
    data,
  );
}

/** AI新建任务 - 确认创建实训任务 */
export function confirmAiHomeworkTasks(
  data: EduHomeworkApi.HomeworkAiCreateConfirmReqVO,
) {
  return requestClient.post<EduHomeworkApi.HomeworkAiCreateConfirmRespVO>(
    '/edu/homework/ai-create/confirm',
    data,
    { timeout: LONG_REQUEST_TIMEOUT },
  );
}
