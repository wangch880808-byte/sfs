import { getDictLabel } from '@vben/hooks';

/**
 * 学生端任务的类型、等级与页头说明（V1.0.1.6 界面规范：类型和等级是中性分类，显示为普通文字，不用彩色标签）。
 * 文字以字典为准；字典还没加载时按原来的写法兜底。
 */

/** 属性列表的一项（components/fact-list.vue） */
export interface FactItem {
  label: string;
  value?: number | string;
  /** 用同名插槽渲染取值，比如状态 */
  slot?: string;
}

const LEVEL_FALLBACK: Record<number, string> = {
  1: '初级',
  2: '中级',
  3: '高级',
};

/** 任务类型：平时任务、考核任务 */
export function getTaskTypeLabel(type?: null | number | string): string {
  return (
    getDictLabel('edu_homework_type', type) ||
    (Number(type) === 2 ? '考核任务' : '平时任务')
  );
}

/** 任务等级：初级、中级、高级 */
export function getTaskLevelLabel(level?: null | number | string): string {
  return (
    getDictLabel('edu_homework_level', level) ||
    LEVEL_FALLBACK[Number(level)] ||
    '未设置等级'
  );
}

/** 页头说明：所属课程 · 等级 · 类型 */
export function buildTaskMeta(task: {
  courseName?: string;
  homeworkLevel?: null | number;
  homeworkType?: null | number;
}): string {
  return [
    task.courseName,
    getTaskLevelLabel(task.homeworkLevel),
    getTaskTypeLabel(task.homeworkType),
  ]
    .filter(Boolean)
    .join(' · ');
}

/**
 * 进入任务提交页时带上课程和等级（?courseId=&level=）：
 * 任务所在等级还没解锁时，提交页据此读出该等级的解锁进度和原因。
 */
export function buildTaskRouteQuery(task: {
  courseId?: null | number;
  homeworkLevel?: null | number;
}): Record<string, string> {
  const query: Record<string, string> = {};
  if (task.courseId) {
    query.courseId = String(task.courseId);
  }
  if (task.homeworkLevel !== undefined && task.homeworkLevel !== null) {
    query.level = String(task.homeworkLevel);
  }
  return query;
}

/** 读出 buildTaskRouteQuery 带的课程和等级；缺少或不是数字时为 undefined */
export function readTaskRouteQuery(query: Record<string, unknown>): {
  courseId?: number;
  level?: number;
} {
  const toNumber = (value: unknown) => {
    const raw = Array.isArray(value) ? value[0] : value;
    if (raw === undefined || raw === null || raw === '') {
      return undefined;
    }
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : undefined;
  };
  const courseId = toNumber(query.courseId);
  return {
    courseId: courseId && courseId > 0 ? courseId : undefined,
    level: toNumber(query.level),
  };
}
