import { LONG_REQUEST_TIMEOUT, requestClient } from '#/api/request';

export namespace StudentCapacityApi {
  export interface IndicatorRate {
    indicatorFirstId: number;
    indicatorName: string;
    totalTasks: number;
    qualifiedTasks: number;
    submittedTasks: number;
    pendingReviewTasks: number;
    evaluatedTasks: number;
    qualifiedRate: null | number;
    evaluationStatus: 'EVALUATED' | 'PENDING';
  }

  export interface CapacityAnalysis {
    indicators: IndicatorRate[];
    overallRate: null | number;
    qualified: boolean | null;
    evaluationStatus: 'EVALUATED' | 'PENDING';
    totalTasks: number;
    submittedTasks: number;
    pendingReviewTasks: number;
    evaluatedTasks: number;
    qualifiedTasks: number;
    /** 已评定、但没有关联一级指标的任务数（不计入能力画像） */
    unindicatedEvaluatedCount?: number;
  }

  export interface CapacityDetail {
    indicatorFirstId: number;
    indicatorFirstName: string;
    secondIndicators: CapacityDetailItem[];
    homeworks: CapacityHomeworkItem[];
  }

  export interface CapacityDetailItem {
    indicatorSecondId: number;
    indicatorName: string;
    totalTasks: number;
    qualifiedTasks: number;
    submittedTasks: number;
    pendingReviewTasks: number;
    evaluatedTasks: number;
    qualifiedRate: null | number;
    evaluationStatus: 'EVALUATED' | 'PENDING';
  }

  export interface CapacityHomeworkItem {
    homeworkId: number;
    homeworkName: string;
    indicatorSecondId: number;
    score: null | number;
    qualified: boolean | null;
    recordStatus: null | number;
  }

  /** 实训报告基础数据：任务计数与达标率统计全部任务，获证判断取能力画像（只统计关联了能力指标的任务） */
  export interface ReportBasic {
    certificateEligible: boolean | null;
    totalTasks: number;
    submittedTasks: number;
    pendingReviewTasks: number;
    evaluatedTasks: number;
    qualifiedTasks: number;
    qualifiedRate: null | number;
    evaluationStatus: 'EVALUATED' | 'PENDING';
  }

  export interface ReportSkillItem {
    indicatorName: string;
    qualifiedRate: null | number;
    totalTasks: number;
    submittedTasks: number;
    pendingReviewTasks: number;
    evaluatedTasks: number;
    qualifiedTasks: number;
    evaluationStatus: 'EVALUATED' | 'PENDING';
  }

  export interface ReportSkills {
    skills: ReportSkillItem[];
    strengths: string[];
    weaknesses: string[];
  }

  export interface ReportTrendRecord {
    recordDate: string;
    totalScore: number;
  }

  export interface ReportTrend {
    trends: ReportTrendRecord[];
  }
}

/** 获取能力分析 */
export function getCapacityAnalysis() {
  return requestClient.get<StudentCapacityApi.CapacityAnalysis>(
    '/edu/student/capacity/analysis',
  );
}

/** 获取能力详情 */
export function getCapacityDetail(indicatorFirstId: number) {
  return requestClient.get<StudentCapacityApi.CapacityDetail>(
    `/edu/student/capacity/detail?indicatorFirstId=${indicatorFirstId}`,
  );
}

/** 获取实训报告-基础数据 */
export function getReportBasic() {
  return requestClient.get<StudentCapacityApi.ReportBasic>(
    '/edu/student/capacity/report/basic',
  );
}

/** 获取实训报告-技能分析 */
export function getReportSkills() {
  return requestClient.get<StudentCapacityApi.ReportSkills>(
    '/edu/student/capacity/report/skills',
  );
}

/** 获取实训报告-趋势 */
export function getReportTrend() {
  return requestClient.get<StudentCapacityApi.ReportTrend>(
    '/edu/student/capacity/report/trend',
  );
}

/** 导出当前学生的个人训练报告 PDF */
export function exportTrainingReportPdf() {
  return requestClient.download('/edu/student/capacity/report/export-pdf', {
    timeout: LONG_REQUEST_TIMEOUT,
  });
}
