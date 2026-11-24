import { requestClient } from '#/api/request';

export namespace EduClassRequestApi {
  /** 建班申请 */
  export interface ClassRequest {
    id?: number;
    courseId: number;
    classId: number;
    term?: string;
    applicantId?: number;
    /** 0 待审核，1 通过，2 拒绝 */
    status: number;
    reason?: string;
    reviewerId?: number;
    reviewedAt?: string;
    classCourseId?: number;
    createTime?: string;
    /** 冗余字段 */
    courseName?: string;
    className?: string;
    applicantName?: string;
    reviewerName?: string;
  }

  /** 提交建班申请请求 */
  export interface ClassRequestSaveReqVO {
    courseId: number;
    classId: number;
    term?: string;
    reason?: string;
  }

  /** 建班申请分页查询参数 */
  export interface ClassRequestPageReqVO {
    pageNo?: number;
    pageSize?: number;
    status?: number;
    courseId?: number;
    applicantId?: number;
  }
}

/** 建班申请分页查询（按创建时间降序） */
export function getClassRequestPage(params: EduClassRequestApi.ClassRequestPageReqVO) {
  return requestClient.get<{
    list: EduClassRequestApi.ClassRequest[];
    total: number;
  }>('/edu/class-request/page', { params });
}

/** 建班申请详情 */
export function getClassRequest(id: number) {
  return requestClient.get<EduClassRequestApi.ClassRequest>(
    `/edu/class-request/get?id=${id}`,
  );
}

/** 教师提交建班申请 */
export function submitClassRequest(
  data: EduClassRequestApi.ClassRequestSaveReqVO,
) {
  return requestClient.post<number>('/edu/class-request/submit', data);
}

/** 审核通过：创建班课关联 */
export function approveClassRequest(id: number) {
  return requestClient.post<boolean>(`/edu/class-request/${id}/approve`);
}

/** 审核拒绝：需填写拒绝原因 */
export function rejectClassRequest(id: number, reason: string) {
  return requestClient.post<boolean>(`/edu/class-request/${id}/reject`, {
    reason,
  });
}
