import type { CapacityCourseApi } from '#/api/capacity/course';

/**
 * 是否公共课（展示用）：以后端派生的 publicCourse 为准；旧版后端没有这个字段时，
 * 按 isPublicCourse 判断（0 是公共课，名字与含义相反）。写入仍用 isPublicCourse。
 */
export function isPublicCourse(
  course: Pick<CapacityCourseApi.Course, 'isPublicCourse' | 'publicCourse'>,
): boolean {
  if (typeof course.publicCourse === 'boolean') {
    return course.publicCourse;
  }
  return Number(course.isPublicCourse) === 0;
}

/** 课程卡片上的课程类型：公共实训、专属实训 */
export function getCourseTypeLabel(
  course: Pick<CapacityCourseApi.Course, 'isPublicCourse' | 'publicCourse'>,
): string {
  return isPublicCourse(course) ? '公共实训' : '专属实训';
}
