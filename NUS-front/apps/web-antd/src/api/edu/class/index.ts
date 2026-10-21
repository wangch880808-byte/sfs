import type { PageParam, PageResult } from '@vben/request';

import { LONG_REQUEST_TIMEOUT, requestClient } from '#/api/request';

export namespace EduClassApi {
  export interface ClassPageReqVO extends PageParam {
    includeArchived?: boolean;
  }
  /** 班级精简信息 */
  export interface ClazzSimple {
    id: number;
    className: string;
  }

  /** 班级信息 */
  export interface Clazz {
    id?: number;
    className: string;
    gradeYear: number;
    deptId: number;
    educationLevel: number;
    schoolingYear: number;
    status: number;
    remark?: string;
    createTime?: Date;
  }

  /** 班级学生信息 */
  export interface ClassStudent {
    id: number;
    username: string;
    nickname: string;
    mobile: string;
    deptId: number;
    gradeYear?: number;
    status: number;
    remark?: string;
  }

  /** 班级学生分页查询参数 */
  export interface ClassStudentPageParams extends PageParam {
    classId: number;
    keyword?: string;
    deptId?: number;
    gradeYear?: number;
  }

  /** 班级学生列表查询参数 */
  export interface ClassStudentListParams {
    classId: number;
    keyword?: string;
    deptId?: number;
  }

  /** 班级学生批量操作参数 */
  export interface ClassStudentBatchReq {
    classId: number;
    studentIds: number[];
    remark?: string;
  }
}

/** 查询班级分页 */
export function getClassPage(params: EduClassApi.ClassPageReqVO) {
  return requestClient.get<PageResult<EduClassApi.Clazz>>('/edu/class/page', {
    params,
  });
}

/** 查询班级详情 */
export function getClass(id: number) {
  return requestClient.get<EduClassApi.Clazz>(`/edu/class/get?id=${id}`);
}

/** 新增班级 */
export function createClass(data: EduClassApi.Clazz) {
  return requestClient.post('/edu/class/create', data);
}

/** 修改班级 */
export function updateClass(data: EduClassApi.Clazz) {
  return requestClient.put('/edu/class/update', data);
}

/** 封存班级 */
export function deleteClass(id: number) {
  return requestClient.delete(`/edu/class/delete?id=${id}`);
}

/** 批量封存班级 */
export function deleteClassList(ids: number[]) {
  return requestClient.delete(`/edu/class/delete-list?ids=${ids.join(',')}`);
}

/** 恢复班级 */
export function restoreClass(id: number) {
  return requestClient.put(`/edu/class/restore?id=${id}`);
}

/** 批量恢复班级 */
export function restoreClassList(ids: number[]) {
  return requestClient.put(`/edu/class/restore-list?ids=${ids.join(',')}`);
}

/** 获取班级精简信息列表 */
export function getSimpleClassList(params?: {
  deptId?: number;
  gradeYear?: number;
}) {
  return requestClient.get<EduClassApi.ClazzSimple[]>(
    '/edu/class/simple-list',
    {
      params,
    },
  );
}

/** 查询可加入学生分页 */
export function getAvailableClassStudentPage(
  params: EduClassApi.ClassStudentPageParams,
) {
  return requestClient.get<PageResult<EduClassApi.ClassStudent>>(
    '/edu/class/student/page-available',
    {
      params,
    },
  );
}

/** 查询班级已加入学生 */
export function getAssignedClassStudentList(
  params: EduClassApi.ClassStudentListParams,
) {
  return requestClient.get<EduClassApi.ClassStudent[]>(
    '/edu/class/student/list-assigned',
    {
      params,
    },
  );
}

/** 批量加入学生 */
export function addClassStudents(data: EduClassApi.ClassStudentBatchReq) {
  return requestClient.post('/edu/class/student/add', data, {
    timeout: LONG_REQUEST_TIMEOUT,
  });
}

/** 检查批量加入班级是否需要备注 */
export function checkAddClassStudentRemark(
  data: EduClassApi.ClassStudentBatchReq,
) {
  return requestClient.post<boolean>(
    '/edu/class/student/check-add-remark',
    data,
  );
}

/** 批量移出学生 */
export function removeClassStudents(data: EduClassApi.ClassStudentBatchReq) {
  return requestClient.post('/edu/class/student/remove', data);
}

/** 导出班级花名册 */
export function exportClassRoster(classId: number) {
  return requestClient.download('/edu/class/student/export', {
    params: { classId },
    timeout: LONG_REQUEST_TIMEOUT,
  });
}
