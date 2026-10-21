import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace CapacityCourseApi {
  export interface Course {
    id?: number;
    indicatorId: number;
    indicatorName?: string;
    /** 是否申请公共课：0 是公共课，1 是专属课（名字与含义相反，写入仍用这个字段） */
    isPublicCourse: number;
    /** 是否公共课（后端由 isPublicCourse 派生，只读）；展示判断用它 */
    publicCourse?: boolean;
    publicAuditStatus?: number;
    publicAuditRemark?: string;
    publicAuditTime?: Date | string;
    publicAuditUserId?: number;
    courseName: string;
    courseImageUrl?: string;
    courseIntroduction?: string;
    teacherId: number;
    teachAssistantIds?: string;
    status: number;
    createTime?: Date | string;
  }

  export interface CoursePageReqVO extends PageParam {
    includeArchived?: boolean;
    courseName?: string;
    indicatorId?: number;
    isPublicCourse?: number;
    publicAuditStatus?: number;
    teacherId?: number;
    status?: number;
    createTime?: string[];
  }

  export interface CourseAuditReqVO {
    id: number;
    approved: boolean;
    remark?: string;
  }
}

/** 查询实训课程分页 */
export function getCoursePage(params: CapacityCourseApi.CoursePageReqVO) {
  return requestClient.get<PageResult<CapacityCourseApi.Course>>(
    '/capacity/course/page',
    { params },
  );
}

/** 查询实训课程详情 */
export function getCourse(id: number) {
  return requestClient.get<CapacityCourseApi.Course>(
    `/capacity/course/get?id=${id}`,
  );
}

/** 新增实训课程 */
export function createCourse(
  data: CapacityCourseApi.Course,
  courseImageFile?: File,
) {
  const formData = new FormData();
  formData.append(
    'course',
    new Blob([JSON.stringify(data)], { type: 'application/json' }),
  );
  if (courseImageFile) {
    formData.append('courseImageFile', courseImageFile);
  }
  return requestClient.post('/capacity/course/create', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
}

/** 修改实训课程 */
export function updateCourse(
  data: CapacityCourseApi.Course,
  courseImageFile?: File,
) {
  const formData = new FormData();
  formData.append(
    'course',
    new Blob([JSON.stringify(data)], { type: 'application/json' }),
  );
  if (courseImageFile) {
    formData.append('courseImageFile', courseImageFile);
  }
  return requestClient.put('/capacity/course/update', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
}

/**
 * 公共课审批的权限码（菜单管理中「实训课程管理」下的按钮）。
 * 持有者可以审批公共课，也可以查看、管理全部课程；后端审批接口、课程可见范围和待审批通知都按它判断。
 */
export const CAPACITY_COURSE_AUDIT_PERMISSION = 'capacity:course:audit';

/** 审批公共实训课程 */
export function auditPublicCourse(data: CapacityCourseApi.CourseAuditReqVO) {
  return requestClient.post('/capacity/course/audit-public', data);
}

/** 封存实训课程 */
export function deleteCourse(id: number) {
  return requestClient.delete(`/capacity/course/delete?id=${id}`);
}

/** 批量封存实训课程 */
export function deleteCourseList(ids: number[]) {
  return requestClient.delete(
    `/capacity/course/delete-list?ids=${ids.join(',')}`,
  );
}

/** 恢复实训课程 */
export function restoreCourse(id: number) {
  return requestClient.put(`/capacity/course/restore?id=${id}`);
}

/** 批量恢复实训课程 */
export function restoreCourseList(ids: number[]) {
  return requestClient.put(
    `/capacity/course/restore-list?ids=${ids.join(',')}`,
  );
}
