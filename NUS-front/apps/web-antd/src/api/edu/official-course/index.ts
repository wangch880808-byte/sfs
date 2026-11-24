import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace EduOfficialCourseApi {
  /** 正式课程信息 */
  export interface OfficialCourse {
    id?: number;
    deptId: number;
    code: string;
    name: string;
    credit?: number;
    status?: number;
    remark?: string;
    createTime?: Date;
  }

  /** 正式课程分页查询参数 */
  export interface OfficialCoursePageReq extends PageParam {
    name?: string;
    code?: string;
    deptId?: number;
    status?: number;
  }
}

/** 查询正式课程分页 */
export function getOfficialCoursePage(
  params: EduOfficialCourseApi.OfficialCoursePageReq,
) {
  return requestClient.get<PageResult<EduOfficialCourseApi.OfficialCourse>>(
    '/edu/official-course/page',
    { params },
  );
}

/** 查询正式课程详情 */
export function getOfficialCourse(id: number) {
  return requestClient.get<EduOfficialCourseApi.OfficialCourse>(
    `/edu/official-course/get?id=${id}`,
  );
}

/** 新增正式课程 */
export function createOfficialCourse(
  data: EduOfficialCourseApi.OfficialCourse,
) {
  return requestClient.post('/edu/official-course/create', data);
}

/** 修改正式课程 */
export function updateOfficialCourse(
  data: EduOfficialCourseApi.OfficialCourse,
) {
  return requestClient.put('/edu/official-course/update', data);
}

/** 归档正式课程 */
export function archiveOfficialCourse(id: number) {
  return requestClient.put(`/edu/official-course/archive?id=${id}`);
}
