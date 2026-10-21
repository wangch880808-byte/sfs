import { requestClient } from '#/api/request';

export namespace StudentCourseApi {
  export interface CourseItem {
    courseId: number;
    courseName: string;
    coverUrl?: string;
    teacherId?: number;
    teacherName?: string;
    indicatorId?: number;
    indicatorName?: string;
    totalHomework: number;
    completedHomework: number;
    progress: number;
    resources: CourseResource[];
    homeworkList: CourseHomework[];
  }

  export interface CourseDetail {
    courseId: number;
    courseName: string;
    coverUrl?: string;
    description?: string;
    teacherId?: number;
    teacherName?: string;
    indicatorId?: number;
    indicatorName?: string;
    resources: CourseResource[];
    homeworkList: CourseHomework[];
    totalHomework?: number;
    completedHomework?: number;
    /** 课程、班级或班课已封存，只能查看（往期课程） */
    archived?: boolean;
  }

  export interface CourseResource {
    id: number;
    resourceName: string;
    fileType?: string;
    fileSize?: number;
    createTime?: string;
  }

  export interface CourseHomework {
    id?: number;
    homeworkId?: number;
    homeworkName: string;
    homeworkType: number;
    homeworkLevel: number;
    deadline: number | string;
    status: number;
    score: number;
  }

  /**
   * 单个等级的闯关进度。计数描述该等级自己的任务；等级 L 是否解锁看上一等级的达标比例是否达到 threshold。
   * 新字段由 V1.0.1.7 起返回，旧接口没有时按可选处理。
   */
  export interface UnlockLevel {
    level: number;
    levelName: string;
    /** 分配给学生的该等级任务数 */
    totalTasks: number;
    qualifiedTasks: number;
    /** 达标率（百分数） */
    qualificationRate: number;
    unlocked: boolean;
    /** 解锁时间；初级、或上一等级没有任务直接放行时为空 */
    unlockedAt?: null | number | string;
    /** 达标任务数：最近一次批改的分数达到达标分数线 */
    passedCount?: number;
    /** 计入的任务数：已过截止时间或已提交 */
    requiredCount?: number;
    /** 在等教师批改的任务数 */
    pendingReviewCount?: number;
    /** 达标比例（0~1），计入的任务数为 0 时为空 */
    ratio?: null | number;
    /** 解锁下一等级需要的达标比例（0~1） */
    threshold?: number;
    /** 没有解锁的原因（一句话）；已解锁时为空 */
    reasonText?: null | string;
  }

  export interface UnlockStatus {
    levels: UnlockLevel[];
    /** 课程已封存，只能查看 */
    archived?: boolean;
  }
}

function requireFiniteNumber(value: unknown, field: string) {
  if (value === null || value === undefined || value === '') {
    throw new Error(`学生课程接口缺少字段：${field}`);
  }
  const normalized = Number(value);
  if (!Number.isFinite(normalized)) {
    throw new TypeError(`学生课程接口字段格式错误：${field}`);
  }
  return normalized;
}

function requireArray<T>(value: unknown, field: string) {
  if (!Array.isArray(value)) {
    throw new TypeError(`学生课程接口缺少字段：${field}`);
  }
  return value as T[];
}

function validateCourseItem(item: StudentCourseApi.CourseItem) {
  return {
    ...item,
    courseId: requireFiniteNumber(item.courseId, 'courseId'),
    totalHomework: requireFiniteNumber(item.totalHomework, 'totalHomework'),
    completedHomework: requireFiniteNumber(
      item.completedHomework,
      'completedHomework',
    ),
    resources: requireArray<StudentCourseApi.CourseResource>(
      item.resources,
      'resources',
    ),
    homeworkList: requireArray<StudentCourseApi.CourseHomework>(
      item.homeworkList,
      'homeworkList',
    ),
  };
}

/** 获取我的课程列表 */
export async function getStudentCourseList(options?: {
  silentError?: boolean;
}) {
  const result = await requestClient.get<StudentCourseApi.CourseItem[]>(
    '/edu/student/course/list',
    options,
  );
  return requireArray<StudentCourseApi.CourseItem>(result, 'courseList').map(
    (item) => validateCourseItem(item),
  );
}

/** 获取课程详情 */
export async function getStudentCourseDetail(
  courseId: number,
  options?: { silentError?: boolean },
) {
  const result = await requestClient.get<StudentCourseApi.CourseDetail>(
    `/edu/student/course/get?courseId=${courseId}`,
    options,
  );
  return {
    ...result,
    courseId: requireFiniteNumber(result.courseId, 'courseId'),
    resources: requireArray<StudentCourseApi.CourseResource>(
      result.resources,
      'resources',
    ),
    homeworkList: requireArray<StudentCourseApi.CourseHomework>(
      result.homeworkList,
      'homeworkList',
    ),
  };
}

/** 获取等级解锁状态 */
export function getUnlockStatus(
  courseId: number,
  options?: { silentError?: boolean },
) {
  return requestClient.get<StudentCourseApi.UnlockStatus>(
    `/edu/student/course/unlock-status?courseId=${courseId}`,
    options,
  );
}

/** 通过学生课程权限校验后下载资源 */
export function downloadStudentCourseResource(resourceId: number) {
  return requestClient.download<Blob>(
    `/edu/student/course/resource/download?id=${resourceId}`,
  );
}
