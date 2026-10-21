import { requestClient } from '#/api/request';

/** 实训任务的个别延期与催交（只有任务的发布教师可以操作） */
export namespace EduHomeworkDeadlineApi {
  /** 一条个别延期 */
  export interface Extension {
    id: number;
    homeworkId: number;
    studentId: number;
    studentName?: string;
    studentNo?: string;
    /** 个人截止时间 */
    deadline: number | string;
    reason?: string;
    /** 提交状态：0 未提交、1 待批改、2 已批改、3 已打回、4 已重新提交 */
    recordStatus?: number;
    updateTime?: number | string;
  }

  /** 任务名单里的一名学生 */
  export interface Student {
    studentId: number;
    studentName?: string;
    studentNo?: string;
    /** 提交状态：0 未提交、1 待批改、2 已批改、3 已打回、4 已重新提交 */
    recordStatus: number;
    /** 已设置的个人截止时间 */
    personalDeadline?: null | number | string;
  }

  export interface SetExtensionReq {
    homeworkId: number;
    studentIds: number[];
    /** YYYY-MM-DD HH:mm:ss */
    deadline: string;
    reason?: string;
  }

  /** 催交结果 */
  export interface RemindResult {
    /** 需要催交的学生数（未提交且还没过实际截止时间） */
    targetCount: number;
    /** 实际发出通知的学生数 */
    notifiedCount: number;
    /** 下次可以催交的时间 */
    nextAvailableTime?: null | number | string;
  }
}

/** 任务的全部个别延期；silentError 为 true 时出错不弹全局提示，由页面自己显示 */
export function getHomeworkExtensionList(
  homeworkId: number,
  silentError = false,
) {
  return requestClient.get<EduHomeworkDeadlineApi.Extension[]>(
    '/edu/homework/extension/list',
    { params: { homeworkId }, silentError },
  );
}

/** 任务的学生名单（含提交状态与个人截止时间） */
export function getHomeworkExtensionStudents(
  homeworkId: number,
  silentError = false,
) {
  return requestClient.get<EduHomeworkDeadlineApi.Student[]>(
    '/edu/homework/extension/students',
    { params: { homeworkId }, silentError },
  );
}

/** 为学生设置个别延期，返回设置的人数 */
export function setHomeworkExtensions(
  data: EduHomeworkDeadlineApi.SetExtensionReq,
) {
  return requestClient.put<number>('/edu/homework/extension/set', data);
}

/** 移除一条个别延期 */
export function deleteHomeworkExtension(id: number) {
  return requestClient.delete<boolean>('/edu/homework/extension/delete', {
    params: { id },
  });
}

/** 催交未提交的学生 */
export function remindHomeworkUnsubmitted(homeworkId: number) {
  return requestClient.post<EduHomeworkDeadlineApi.RemindResult>(
    '/edu/homework/remind',
    undefined,
    { params: { homeworkId } },
  );
}
