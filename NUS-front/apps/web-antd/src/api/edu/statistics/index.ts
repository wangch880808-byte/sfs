import { requestClient } from '#/api/request';

export namespace StatisticsApi {
  /** 总体合格率查询参数 */
  export interface TotalPassRateReqVO {
    collegeId?: number;
    majorId?: number;
    classId?: number;
    courseId?: number;
    academicYear?: number;
  }

  /** 合格率分段 */
  export interface PassRateRespVO {
    unqualifiedRate: number;
    qualifiedRate: number;
    goodRate: number;
    excellentRate: number;
    unqualifiedCount: number;
    qualifiedCount: number;
    goodCount: number;
    excellentCount: number;
    totalCount: number;
  }

  /** 成绩趋势项 */
  export interface TrendItem {
    homeworkName: string;
    maxScore: number;
    avgScore: number;
    minScore: number;
  }

  /** 成绩趋势响应 */
  export interface WorkTrendRespVO {
    items: TrendItem[];
  }

  /** 预警统计响应 */
  export interface WorkWarningRespVO {
    unsubmittedRate: number;
    failRate: number;
    unsubmittedCount: number;
    failCount: number;
    totalCount: number;
  }

  /** 课程分析项 */
  export interface CourseAnalysisItem {
    homeworkName: string;
    avgScore: number;
    topStudentName: string;
    topScore: number;
  }

  /** 课程分析响应 */
  export interface CourseAnalysisRespVO {
    items: CourseAnalysisItem[];
  }

  /** 课程选项 */
  export interface CourseOption {
    id: number;
    name: string;
  }

  /** 班级选项 */
  export interface ClassOption {
    id: number;
    name: string;
  }

  /** 管理员看板响应 */
  export interface AdminDashboardRespVO {
    totalStudents: number;
    totalTeachers: number;
    totalCourses: number;
    totalHomeworks: number;
    weekSubmissions: number;
    pendingCorrections: number;
    aiCorrections: number;
    /** 待审批的公共课申请数；当前用户没有公共课审批权限时为 null（不显示这一项） */
    pendingPublicAuditCount: null | number;
    /** 学期总数 */
    termCount: number;
    /** 正式课程目录数 */
    officialCourseCount: number;
    /** 教学班（班课关联）数 */
    classCourseCount: number;
    /** 活跃班级数 */
    activeClassCount: number;
    /** 最近学期列表 */
    recentTerms: TermSummary[];
  }

  /** 学期摘要（管理总览用） */
  export interface TermSummary {
    name: string;
    startsOn: string;
    endsOn: string;
    status: number;
  }

  /** 教师工作台最近实训任务项 */
  export interface TeacherRecentHomework {
    homeworkId: number;
    homeworkName: string;
    courseName: string;
    status: number;
    createTime: number;
  }

  /** 教师工作台响应 */
  export interface TeacherDashboardRespVO {
    myCourses: number;
    pendingCorrections: number;
    unreadMessages: number;
    weekSubmissions: number;
    recentHomeworks: TeacherRecentHomework[];
  }
}

type RawAdminDashboardRespVO = Partial<StatisticsApi.AdminDashboardRespVO> & {
  aiCorrections?: number;
  aiReviewCount?: number;
  pendingCorrections?: number;
  pendingReview?: number;
  totalCourses?: number;
  totalHomeworks?: number;
  totalStudents?: number;
  totalTeachers?: number;
  weekSubmissions?: number;
};

type RawTeacherDashboardRespVO =
  Partial<StatisticsApi.TeacherDashboardRespVO> & {
    myCourseCount?: number;
    myCourses?: number;
    pendingCorrections?: number;
    pendingReview?: number;
    recentHomeworks?: StatisticsApi.TeacherRecentHomework[];
    unreadMessages?: number;
    weekNewSubmissions?: number;
    weekSubmissions?: number;
  };

type RawCourseOption = Partial<StatisticsApi.CourseOption> & {
  courseId?: number | string;
  courseName?: string;
  id?: number | string;
  name?: string;
};

type RawClassOption = Partial<StatisticsApi.ClassOption> & {
  classId?: number | string;
  className?: string;
  id?: number | string;
  name?: string;
};

type RawTrendItem = Partial<StatisticsApi.TrendItem>;

type RawTrendPayload = {
  avgScores?: number[];
  items?: RawTrendItem[];
  maxScores?: number[];
  minScores?: number[];
  workNames?: string[];
};

type RawCourseHomework = {
  avgScore?: number;
  homeworkId?: number;
  homeworkName?: string;
  top5?: Array<{ score?: number; studentName?: string }>;
};

type RawCourseAnalysisItem = Partial<StatisticsApi.CourseAnalysisItem>;

type RawCourseAnalysisPayload = {
  homeworks?: RawCourseHomework[];
  items?: RawCourseAnalysisItem[];
};

function toNumber(value: number | string | undefined) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function normalizeCourseOptions(items: RawCourseOption[] = []) {
  return items
    .map((item) => ({
      id: toNumber(item.id ?? item.courseId),
      name: String(item.name ?? item.courseName ?? ''),
    }))
    .filter((item) => item.id > 0 && item.name);
}

function normalizeClassOptions(items: RawClassOption[] = []) {
  return items
    .map((item) => ({
      id: toNumber(item.id ?? item.classId),
      name: String(item.name ?? item.className ?? ''),
    }))
    .filter((item) => item.id > 0 && item.name);
}

function normalizeTrendPayload(
  payload: RawTrendPayload,
): StatisticsApi.WorkTrendRespVO {
  if (Array.isArray(payload?.items)) {
    return {
      items: payload.items.map((item: RawTrendItem) => ({
        homeworkName: String(item.homeworkName ?? ''),
        avgScore: Number(item.avgScore ?? 0),
        maxScore: Number(item.maxScore ?? 0),
        minScore: Number(item.minScore ?? 0),
      })),
    };
  }
  const names = Array.isArray(payload?.workNames) ? payload.workNames : [];
  const avgScores = Array.isArray(payload?.avgScores) ? payload.avgScores : [];
  const maxScores = Array.isArray(payload?.maxScores) ? payload.maxScores : [];
  const minScores = Array.isArray(payload?.minScores) ? payload.minScores : [];
  return {
    items: names.map((homeworkName: string, index: number) => ({
      homeworkName: String(homeworkName),
      avgScore: Number(avgScores[index] ?? 0),
      maxScore: Number(maxScores[index] ?? 0),
      minScore: Number(minScores[index] ?? 0),
    })),
  };
}

function normalizeCourseAnalysis(
  payload: RawCourseAnalysisPayload,
): StatisticsApi.CourseAnalysisRespVO & {
  homeworks?: RawCourseHomework[];
} {
  if (Array.isArray(payload?.items)) {
    return {
      items: payload.items.map((item: RawCourseAnalysisItem) => ({
        homeworkName: String(item.homeworkName ?? ''),
        avgScore: Number(item.avgScore ?? 0),
        topStudentName: String(item.topStudentName ?? '-'),
        topScore: Number(item.topScore ?? 0),
      })),
    };
  }
  const homeworks = Array.isArray(payload?.homeworks) ? payload.homeworks : [];
  return {
    homeworks,
    items: homeworks.map((item: RawCourseHomework) => ({
      homeworkName: String(item.homeworkName ?? ''),
      avgScore: Number(item.avgScore ?? 0),
      topStudentName: String(item.top5?.[0]?.studentName ?? '-'),
      topScore: Number(item.top5?.[0]?.score ?? 0),
    })),
  };
}

function normalizeAdminDashboard(
  payload: RawAdminDashboardRespVO,
): StatisticsApi.AdminDashboardRespVO {
  return {
    totalStudents: Number(payload?.totalStudents ?? 0),
    totalTeachers: Number(payload?.totalTeachers ?? 0),
    totalCourses: Number(payload?.totalCourses ?? 0),
    totalHomeworks: Number(payload?.totalHomeworks ?? 0),
    weekSubmissions: Number(payload?.weekSubmissions ?? 0),
    pendingCorrections: Number(
      payload?.pendingCorrections ?? payload?.pendingReview ?? 0,
    ),
    aiCorrections: Number(
      payload?.aiCorrections ?? payload?.aiReviewCount ?? 0,
    ),
    pendingPublicAuditCount:
      payload?.pendingPublicAuditCount === null ||
      payload?.pendingPublicAuditCount === undefined
        ? null
        : Number(payload.pendingPublicAuditCount) || 0,
    termCount: Number(payload?.termCount ?? 0),
    officialCourseCount: Number(payload?.officialCourseCount ?? 0),
    classCourseCount: Number(payload?.classCourseCount ?? 0),
    activeClassCount: Number(payload?.activeClassCount ?? 0),
    recentTerms: Array.isArray(payload?.recentTerms)
      ? payload.recentTerms.map((t: any) => ({
          name: String(t?.name ?? ''),
          startsOn: String(t?.startsOn ?? ''),
          endsOn: String(t?.endsOn ?? ''),
          status: Number(t?.status ?? 0),
        }))
      : [],
  };
}

function normalizeTeacherDashboard(
  payload: RawTeacherDashboardRespVO,
): StatisticsApi.TeacherDashboardRespVO {
  return {
    myCourses: Number(payload?.myCourses ?? payload?.myCourseCount ?? 0),
    pendingCorrections: Number(
      payload?.pendingCorrections ?? payload?.pendingReview ?? 0,
    ),
    unreadMessages: Number(payload?.unreadMessages ?? 0),
    weekSubmissions: Number(
      payload?.weekSubmissions ?? payload?.weekNewSubmissions ?? 0,
    ),
    recentHomeworks: Array.isArray(payload?.recentHomeworks)
      ? payload.recentHomeworks
      : [],
  };
}

/** 总体合格率 */
export function getTotalPassRate(params: StatisticsApi.TotalPassRateReqVO) {
  return requestClient.get<StatisticsApi.PassRateRespVO>(
    '/edu/statistics/total-pass-rate',
    { params },
  );
}

/** 成绩趋势 */
export function getWorkTrend(courseId: number, classId?: number) {
  return requestClient
    .get<RawTrendPayload>('/edu/statistics/work-trend', {
      params: { courseId, classId },
    })
    .then(normalizeTrendPayload);
}

/** 课程分析 */
export function getCourseAnalysis(courseId: number, classId?: number) {
  return requestClient
    .get<RawCourseAnalysisPayload>('/edu/statistics/course-analysis', {
      params: { courseId, classId },
    })
    .then(normalizeCourseAnalysis);
}

/** 教师课程选项 */
export function getCourseOptions() {
  return requestClient
    .get<RawCourseOption[]>('/edu/statistics/course-options')
    .then(normalizeCourseOptions);
}

/** 班级选项（通过课程ID） */
export function getClassOptions(courseId: number) {
  return requestClient
    .get<RawClassOption[]>(`/edu/statistics/class-options?courseId=${courseId}`)
    .then(normalizeClassOptions);
}

/** 管理员看板 */
export function getAdminDashboard() {
  return requestClient
    .get<RawAdminDashboardRespVO>('/edu/statistics/admin/dashboard')
    .then(normalizeAdminDashboard);
}

/** 教师工作台 */
export function getTeacherDashboard() {
  return requestClient
    .get<RawTeacherDashboardRespVO>('/edu/statistics/teacher/dashboard')
    .then(normalizeTeacherDashboard);
}
