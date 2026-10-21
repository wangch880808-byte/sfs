import { describe, expect, it } from 'vitest';

import {
  CLASS_COURSE_STATUS,
  getClassCourseStatusMeta,
  getResourceUploadStatusMeta,
} from './status-course-detail';

describe('班课状态', () => {
  it('进行中为 running，未开始与已结束为 neutral', () => {
    expect(getClassCourseStatusMeta(CLASS_COURSE_STATUS.RUNNING)).toEqual({
      label: '进行中',
      tone: 'running',
    });
    expect(getClassCourseStatusMeta(CLASS_COURSE_STATUS.NOT_STARTED)).toEqual({
      label: '未开始',
      tone: 'neutral',
    });
    expect(getClassCourseStatusMeta(CLASS_COURSE_STATUS.ENDED)).toEqual({
      label: '已结束',
      tone: 'neutral',
    });
  });

  it('已封存优先于学期状态', () => {
    expect(getClassCourseStatusMeta(CLASS_COURSE_STATUS.RUNNING, true)).toEqual(
      { label: '已封存', tone: 'neutral' },
    );
  });

  it('未知或缺失的状态按原文或“未设置”中性显示', () => {
    expect(getClassCourseStatusMeta(' 已暂停 ')).toEqual({
      label: '已暂停',
      tone: 'neutral',
    });
    expect(getClassCourseStatusMeta(undefined)).toEqual({
      label: '未设置',
      tone: 'neutral',
    });
    expect(getClassCourseStatusMeta(null).label).toBe('未设置');
  });
});

describe('资源上传状态', () => {
  it('上传中带进度，进度限制在 0～100', () => {
    expect(
      getResourceUploadStatusMeta({ percent: 42, status: 'uploading' }),
    ).toEqual({ label: '上传中 42%', tone: 'running' });
    expect(
      getResourceUploadStatusMeta({ percent: 130, status: 'uploading' }).label,
    ).toBe('上传中 100%');
    expect(
      getResourceUploadStatusMeta({ percent: null, status: 'uploading' }).label,
    ).toBe('上传中 0%');
  });

  it('完成为 done，失败为 failed', () => {
    expect(getResourceUploadStatusMeta({ status: 'success' })).toEqual({
      label: '上传完成',
      tone: 'done',
    });
    expect(getResourceUploadStatusMeta({ status: 'error' })).toEqual({
      label: '上传失败',
      tone: 'failed',
    });
  });
});
