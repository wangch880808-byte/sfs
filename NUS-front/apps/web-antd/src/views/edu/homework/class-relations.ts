import type { EduHomeworkApi } from '#/api/edu/homework';
import type { EduHomeworkCopyApi } from '#/api/edu/homework/copy';

import {
  buildRequiredAttachmentTypesJson,
  resolveGradingStrategy,
  resolveGradingStrategyConfig,
} from './data';

/**
 * 课程固定、班级多选的任务表单（课程详情快捷新建、复制任务）里的班课处理（V1.0.1.10）。
 *
 * 「指定班级」下拉的值一律是班课编号（classCourseId），不是班级编号（classId）：
 * 两者是不同表的编号，数值可能撞上。以前按班课编号查不到时再按班级编号兜底，
 * 某班的 classId 恰好等于另一条班课的 classCourseId 时，任务就建到了别的班。
 * 现在只按班课编号查，查不到的直接丢掉，并在新建请求里带上 classCourseId 让后端再核对一次。
 */
export interface ClassCourseRelation {
  classCourseId: number;
  classId: number;
  className?: string;
  semesterName?: string;
  /** 班课状态文字（课程详情传入），下拉里放在班级名称后面 */
  status?: string;
}

/** 复制任务：本学期这门课没有可布置的班级时的提示 */
export const COPY_NO_TARGET_HINT = '本学期这门课没有你可以布置任务的班级。';

/** 复制任务：来源任务读不到时的提示 */
export const COPY_SOURCE_MISSING_HINT =
  '没有读到要复制的任务，可能已被删除。刷新列表后重试。';

function toPositiveId(value: unknown): number {
  const n = Number(value);
  return Number.isInteger(n) && n > 0 ? n : 0;
}

/** 下拉的值（单个或数组）整理成去重后的正整数编号 */
export function normalizeSelectedIds(value: unknown): number[] {
  const list = Array.isArray(value) ? value : [value];
  return [...new Set(list.map((item) => toPositiveId(item)).filter(Boolean))];
}

/** 按班课编号建索引 */
export function indexRelations(
  relations: ClassCourseRelation[],
): Map<number, ClassCourseRelation> {
  return new Map(relations.map((item) => [item.classCourseId, item]));
}

/**
 * 选中的值换成班课关系：只按班课编号查，查不到的丢掉（不再按班级编号兜底）。
 * 保持选择的顺序。
 */
export function resolveSelectedRelations(
  value: unknown,
  relationMap: ReadonlyMap<number, ClassCourseRelation>,
): ClassCourseRelation[] {
  return normalizeSelectedIds(value)
    .map((id) => relationMap.get(id))
    .filter((item): item is ClassCourseRelation => !!item);
}

/** 「指定班级」的选项：值是班课编号，有状态时写在名称后面 */
export function buildRelationOptions(
  relations: ClassCourseRelation[],
): Array<{ label: string; value: number }> {
  return relations.map((item) => {
    const name = item.className?.trim() || `班级 ${item.classCourseId}`;
    return {
      label: item.status ? `${name}（${item.status}）` : name,
      value: item.classCourseId,
    };
  });
}

/**
 * 复制任务的可选班级：从「本学期我能布置任务的班课」里取来源任务那门课的，
 * 编号不全的丢掉，同一班课只留一条。
 */
export function relationsFromCopyTargets(
  targets: EduHomeworkCopyApi.Target[] | null | undefined,
  courseId: unknown,
): ClassCourseRelation[] {
  const course = toPositiveId(courseId);
  if (!course) {
    return [];
  }
  const seen = new Set<number>();
  const relations: ClassCourseRelation[] = [];
  for (const target of targets ?? []) {
    const classCourseId = toPositiveId(target.classCourseId);
    const classId = toPositiveId(target.classId);
    if (
      toPositiveId(target.courseId) !== course ||
      !classCourseId ||
      !classId ||
      seen.has(classCourseId)
    ) {
      continue;
    }
    seen.add(classCourseId);
    relations.push({
      classCourseId,
      classId,
      className: target.className,
      semesterName: target.semesterName,
    });
  }
  return relations;
}

/**
 * 每个选中的班课一条新建请求：带上班级、学期、班课编号；复制时再带上来源任务编号。
 * 不带任务编号与状态（新建一律为已发布，由后端决定）。
 */
export function buildBatchCreatePayloads(
  payload: EduHomeworkApi.Homework,
  relations: ClassCourseRelation[],
  sourceHomeworkId?: number,
): EduHomeworkApi.Homework[] {
  const { id: _id, status: _status, ...rest } = payload;
  const sourceId = toPositiveId(sourceHomeworkId);
  return relations.map((relation) => {
    const item: EduHomeworkApi.Homework = {
      ...rest,
      classCourseId: relation.classCourseId,
      classId: relation.classId,
      semesterName: relation.semesterName,
    };
    if (sourceId) {
      item.sourceHomeworkId = sourceId;
    } else {
      delete item.sourceHomeworkId;
    }
    return item;
  });
}

/**
 * 复制任务时表单的初始值：除班级与截止时间外都取来源任务的值；
 * 班级要重新选，截止时间清空要求重新选。批改方式按来源任务换算，与新建表单一致。
 */
export function buildCopyFormValues(
  source: EduHomeworkApi.Homework,
): Record<string, any> {
  const gradingStrategy = resolveGradingStrategy(
    source.autoGrade,
    source.aiReviewMode,
  );
  const submissionMode = source.submissionMode || 'text';
  return {
    ...resolveGradingStrategyConfig(gradingStrategy),
    aiConfidenceThreshold: source.aiConfidenceThreshold ?? 0.7,
    aiReferenceJson: source.aiReferenceJson ?? '',
    aiRubricJson: source.aiRubricJson ?? '',
    classId: undefined,
    courseId: toPositiveId(source.courseId) || undefined,
    deadline: undefined,
    gradingStrategy,
    homeworkLevel: source.homeworkLevel,
    homeworkName: source.homeworkName,
    homeworkType: source.homeworkType,
    indicatorFirstId: toPositiveId(source.indicatorFirstId) || undefined,
    indicatorSecondId: toPositiveId(source.indicatorSecondId) || undefined,
    passScore: source.passScore,
    requiredAttachmentTypesJson:
      buildRequiredAttachmentTypesJson(submissionMode),
    requirement: source.requirement ?? '',
    score: source.score,
    submissionMode,
  };
}
