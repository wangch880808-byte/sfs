import { requestClient } from '#/api/request';

export namespace StudentHomeApi {
  export interface Overview {
    pendingCount: number;
    submittedCount: number;
    averageScore: number;
    recentScores: RecentScore[];
  }

  export interface RecentScore {
    homeworkName: string;
    score: number;
    correctTime: string;
  }
}

type RawOverview = Partial<StudentHomeApi.Overview> & {
  averageScore?: number;
  submittedCount?: number;
  pendingCount?: number;
  pendingTaskCount?: number;
  recentScores?: Array<Partial<StudentHomeApi.RecentScore>>;
};

function normalizeOverview(payload: RawOverview): StudentHomeApi.Overview {
  return {
    pendingCount: Number(
      payload?.pendingCount ?? payload?.pendingTaskCount ?? 0,
    ),
    submittedCount: Number(payload?.submittedCount ?? 0),
    averageScore: Number(payload?.averageScore ?? 0),
    recentScores: Array.isArray(payload?.recentScores)
      ? payload.recentScores.map((item) => ({
          homeworkName: String(item.homeworkName ?? ''),
          score: Number(item.score ?? 0),
          correctTime: item.correctTime ?? '',
        }))
      : [],
  };
}

/** 获取首页概览 */
export function getOverview() {
  return requestClient
    .get<RawOverview>('/edu/student/home/overview')
    .then(normalizeOverview);
}
