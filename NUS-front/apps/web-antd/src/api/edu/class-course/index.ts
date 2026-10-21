import { LONG_REQUEST_TIMEOUT, requestClient } from '#/api/request';

export namespace EduClassCourseApi {
  /** 班课关联信息 */
  export interface ClassCourse {
    id?: number;
    classId: number;
    courseId: number;
    className?: string;
    courseName?: string;
    semesterName?: string;
    semesterStartDate?: string;
    semesterEndDate?: string;
    assistantTeacherIds?: string;
    status: number;
    createTime?: Date | string;
    /** 公共课认领人（V1.0.1.10）；专属课程或未认领时为空 */
    claimTeacherId?: null | number;
    claimTeacherName?: null | string;
    /**
     * 当前用户能否给这个班布置任务、放弃认领、封存（V1.0.1.10，公共课按认领人判断，管理员也为 true）。
     * 旧版后端没有这个字段，按能操作处理。
     */
    canOperate?: boolean;
    /** 该行已被认领，且当前用户能放弃或清除认领（V1.0.1.10）；旧版后端没有这个字段 */
    canRelease?: boolean;
  }

  /** 认领弹窗里班级的状态：可认领、自己已认领、已被别人认领 */
  export type ClaimState = 'AVAILABLE' | 'MINE' | 'TAKEN';

  /** 公共课认领弹窗的班级（GET /edu/class-course/claim-options） */
  export interface ClaimOption {
    classId: number;
    className: string;
    gradeYear?: null | number | string;
    deptName?: null | string;
    /** 这门课在所选学期里已有的班课；没有时为空 */
    classCourseId?: null | number;
    claimTeacherId?: null | number;
    claimTeacherName?: null | string;
    state: ClaimState;
  }

  /** 认领请求 */
  export interface ClaimReqVO {
    courseId: number;
    classIds: number[];
    /** 为空时后端取当前学期 */
    semesterName?: string;
  }

  /** 绑定请求 */
  export interface BindReqVO {
    courseId: number;
    classIds: number[];
    semesterName?: string;
    semesterStartDate?: string;
    semesterEndDate?: string;
    assistantTeacherIds?: string;
  }

  /** 解绑请求 */
  export interface UnbindReqVO {
    id: number;
  }
}

/** 班级绑定课程 */
export function bindClassCourse(data: EduClassCourseApi.BindReqVO) {
  return requestClient.post('/edu/class-course/bind', data, {
    timeout: LONG_REQUEST_TIMEOUT,
  });
}

/** 封存班课绑定 */
export function unbindClassCourse(data: EduClassCourseApi.UnbindReqVO) {
  return requestClient.delete('/edu/class-course/unbind', { params: data });
}

/** 公共课认领弹窗的班级列表；课程不是已上线的公共课时后端返回错误 */
export function getClassCourseClaimOptions(
  courseId: number,
  semesterName?: string,
) {
  return requestClient.get<EduClassCourseApi.ClaimOption[]>(
    '/edu/class-course/claim-options',
    { params: { courseId, semesterName } },
  );
}

/** 认领公共课的班级；任一班级已被别人认领时整体失败 */
export function claimClassCourse(data: EduClassCourseApi.ClaimReqVO) {
  return requestClient.post<boolean>('/edu/class-course/claim', data, {
    timeout: LONG_REQUEST_TIMEOUT,
  });
}

/** 放弃认领（认领人本人），或清除认领（管理员、审批人）；已布置的任务不动 */
export function releaseClassCourse(id: number) {
  return requestClient.put<boolean>(`/edu/class-course/release?id=${id}`);
}

/** 恢复班课绑定 */
export function restoreClassCourse(id: number) {
  return requestClient.put(`/edu/class-course/restore?id=${id}`);
}

/** 查询班级的课程列表 */
export function getClassCourseListByClass(
  classId: number,
  includeArchived = false,
) {
  return requestClient.get<EduClassCourseApi.ClassCourse[]>(
    '/edu/class-course/list-by-class',
    { params: { classId, includeArchived } },
  );
}

/** 查询课程的班级列表 */
export function getClassCourseListByCourse(
  courseId: number,
  includeArchived = false,
) {
  return requestClient.get<EduClassCourseApi.ClassCourse[]>(
    '/edu/class-course/list-by-course',
    { params: { courseId, includeArchived } },
  );
}
