import { LONG_REQUEST_TIMEOUT, requestClient } from '#/api/request';

export namespace EduSemesterApi {
  export interface CurrentSemester {
    semesterName: string;
    semesterStartDate?: string;
    semesterEndDate?: string;
    autoEnabled?: boolean;
  }

  /** 可选学期：当前学期和班级课程里用过的学期 */
  export interface SemesterOption {
    activeClassCourseCount?: number;
    classCourseCount?: number;
    current?: boolean;
    semesterEndDate?: string;
    semesterName: string;
    semesterStartDate?: string;
  }

  export interface WrapUpClassCourse {
    autoCreated?: boolean;
    classCourseId: number;
    classId: number;
    className?: string;
    courseId: number;
    courseName?: string;
    openTaskCount?: number;
    pendingReviewCount?: number;
  }

  export interface WrapUpClass {
    activeClassCourseCount?: number;
    classId: number;
    className?: string;
  }

  export interface WrapUpPreview {
    activeClassCourseCount: number;
    activePublicCourseLinkCount: number;
    classCourses: WrapUpClassCourse[];
    classes: WrapUpClass[];
    current?: boolean;
    openTaskCount: number;
    pendingReviewCount: number;
    publicCourseLinkCount: number;
    semesterName: string;
  }

  export interface WrapUpArchiveReq {
    classCourseIds: number[];
    classIds: number[];
    semesterName: string;
  }

  export interface WrapUpArchiveResp {
    archivedClassCount: number;
    archivedClassCourseCount: number;
    semesterName: string;
  }
}

export function getCurrentSemester() {
  return requestClient.get<EduSemesterApi.CurrentSemester>(
    '/edu/semester/current',
  );
}

export function saveCurrentSemester(data: EduSemesterApi.CurrentSemester) {
  return requestClient.put('/edu/semester/current', data);
}

export function setSemesterAutoMode(autoEnabled: boolean) {
  return requestClient.put('/edu/semester/auto-mode', { autoEnabled });
}

/** 可选学期：当前学期在最前，其余按名称倒序 */
export function getSemesterOptions() {
  return requestClient.get<EduSemesterApi.SemesterOption[]>(
    '/edu/semester/options',
  );
}

/** 学期收尾预览 */
export function getSemesterWrapUpPreview(semesterName: string) {
  return requestClient.get<EduSemesterApi.WrapUpPreview>(
    '/edu/semester/wrap-up/preview',
    { params: { semesterName } },
  );
}

/** 学期收尾：封存选中的班级课程和班级 */
export function archiveSemesterWrapUp(data: EduSemesterApi.WrapUpArchiveReq) {
  return requestClient.post<EduSemesterApi.WrapUpArchiveResp>(
    '/edu/semester/wrap-up/archive',
    data,
    { timeout: LONG_REQUEST_TIMEOUT },
  );
}
