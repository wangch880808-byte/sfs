import { describe, expect, it } from 'vitest';

import { getCourseTypeLabel, isPublicCourse } from './course-type';

describe('课程类型展示', () => {
  it('以后端派生的 publicCourse 为准', () => {
    expect(isPublicCourse({ isPublicCourse: 1, publicCourse: true })).toBe(
      true,
    );
    expect(isPublicCourse({ isPublicCourse: 0, publicCourse: false })).toBe(
      false,
    );
    expect(getCourseTypeLabel({ isPublicCourse: 1, publicCourse: true })).toBe(
      '公共实训',
    );
    expect(getCourseTypeLabel({ isPublicCourse: 0, publicCourse: false })).toBe(
      '专属实训',
    );
  });

  it('旧版后端没有 publicCourse 时按 isPublicCourse 判断（0 是公共课）', () => {
    expect(getCourseTypeLabel({ isPublicCourse: 0 })).toBe('公共实训');
    expect(getCourseTypeLabel({ isPublicCourse: 1 })).toBe('专属实训');
    expect(isPublicCourse({ isPublicCourse: '0' as unknown as number })).toBe(
      true,
    );
  });
});
