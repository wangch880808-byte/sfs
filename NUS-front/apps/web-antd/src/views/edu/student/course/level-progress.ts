/**
 * 课程详情“等级闯关”一栏的显示文字：每个等级自己的达标进度。
 *
 * 口径（与后端一致）：只计已截止或已提交的任务；初级、中级、高级始终开放，达标数据只用于展示进度。
 * 旧接口没有 passedCount、requiredCount 等字段时，退回旧的 qualifiedTasks、totalTasks。
 */
import type { StudentCourseApi } from '#/api/edu/student/course';

import dayjs from 'dayjs';

export interface LevelProgressView {
  level: number;
  name: string;
  unlocked: boolean;
  /** 进度条百分比（0~100） */
  percent: number;
  /** 计数说明，例如“3/4 个达标 · 75%” */
  countText: string;
  /** 第二行说明：未解锁写原因，已解锁写解锁时间或待批改数；没有时为空 */
  detailText: string;
}

function toPercent(value: null | number | undefined) {
  return value === null || value === undefined || !Number.isFinite(value)
    ? undefined
    : Math.round(value * 100);
}

/** 阈值百分比，例如 80；接口没有给出时为空 */
export function getThresholdPercent(
  levels: StudentCourseApi.UnlockLevel[],
): number | undefined {
  const withThreshold = levels.find(
    (item) => item.threshold !== undefined && item.threshold !== null,
  );
  return toPercent(withThreshold?.threshold);
}

/** 等级闯关一栏顶部的规则说明（不超过 60 字） */
export function buildLevelRuleHint(levels: StudentCourseApi.UnlockLevel[]) {
  void levels; // 等级始终开放，规则说明不再依赖阈值；保留入参兼容现有调用
  return '只计已截止或已提交的任务；初级、中级、高级均可直接开始，达标数据只作进度参考。';
}

export function describeLevel(
  level: StudentCourseApi.UnlockLevel,
): LevelProgressView {
  const required = level.requiredCount ?? level.totalTasks ?? 0;
  const passed = level.passedCount ?? level.qualifiedTasks ?? 0;
  const ratioPercent =
    toPercent(level.ratio) ?? Math.round(Number(level.qualificationRate) || 0);
  const pending = level.pendingReviewCount ?? 0;

  let countText: string;
  if (required > 0) {
    countText = `${passed}/${required} 个达标 · ${ratioPercent}%`;
  } else if ((level.totalTasks ?? 0) > 0) {
    countText = '任务还没有截止或提交';
  } else {
    countText = '暂无任务';
  }

  let detailText = '';
  if (!level.unlocked) {
    detailText = level.reasonText?.trim() || '上一等级达标后解锁';
  } else if (pending > 0) {
    detailText = `${pending} 个任务在等教师批改`;
  } else if (level.unlockedAt) {
    detailText = `${dayjs(level.unlockedAt).format('YYYY-MM-DD')} 解锁`;
  }

  return {
    level: level.level,
    name: level.levelName,
    unlocked: !!level.unlocked,
    percent: required > 0 ? ratioPercent : 0,
    countText,
    detailText,
  };
}
