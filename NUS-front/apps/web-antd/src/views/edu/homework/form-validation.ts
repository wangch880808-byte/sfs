/**
 * 新建、编辑实训任务时「课程与指标」一节的检查（V1.0.1.8 C2、D4）。
 * 这一节是表单外自己写的两项，不走表单校验：
 * - 指定课程必填：没选时下拉框标红，下方写明「请选择课程」；
 * - 一级指标不强制：没选也能保存，但预览里提醒成绩不会计入能力分析；选了一级指标时二级指标必填。
 */

export const HOMEWORK_COURSE_REQUIRED = '请选择课程';

export const HOMEWORK_INDICATOR_SECOND_REQUIRED = '请选择二级指标';

/** 没选一级指标时预览里的提醒（不阻止保存） */
export const HOMEWORK_INDICATOR_UNSET_NOTICE =
  '未选择能力指标，这次成绩不会计入学生的能力分析。';

export interface HomeworkContextValues {
  courseId?: unknown;
  indicatorFirstId?: unknown;
  indicatorSecondId?: unknown;
}

export interface HomeworkContextCheck {
  /** 课程的行内错误；选了课程时为空字符串 */
  courseError: string;
  /** 选了一级指标但没选二级指标时的提示；否则为空字符串（阻止保存） */
  indicatorSecondError: string;
  /** 没选一级指标：可以保存，预览里提醒 */
  indicatorUnset: boolean;
}

function hasId(value: unknown): boolean {
  if (value === undefined || value === null || value === '') {
    return false;
  }
  const n = Number(value);
  return Number.isFinite(n) && n > 0;
}

export function checkHomeworkContext(
  values: HomeworkContextValues,
): HomeworkContextCheck {
  const indicatorUnset = !hasId(values.indicatorFirstId);
  return {
    courseError: hasId(values.courseId) ? '' : HOMEWORK_COURSE_REQUIRED,
    indicatorSecondError:
      !indicatorUnset && !hasId(values.indicatorSecondId)
        ? HOMEWORK_INDICATOR_SECOND_REQUIRED
        : '',
    indicatorUnset,
  };
}

/** 课程与指标这一节能否进入预览：课程已选；选了一级指标时二级指标也已选 */
export function isHomeworkContextValid(check: HomeworkContextCheck): boolean {
  return !check.courseError && !check.indicatorSecondError;
}
