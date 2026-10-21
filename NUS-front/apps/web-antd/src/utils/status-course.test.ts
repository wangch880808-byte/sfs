import { describe, expect, it } from 'vitest';

import {
  COURSE_PUBLIC_AUDIT_STATUS,
  getAiTaskPreviewItemStatusMeta,
  getCoursePublicAuditStatusMeta,
  getCourseStatusMeta,
} from './status-course';

describe('公共课审批状态', () => {
  it('无需审批、空值与不认识的取值不显示', () => {
    expect(
      getCoursePublicAuditStatusMeta(COURSE_PUBLIC_AUDIT_STATUS.NONE),
    ).toBeNull();
    expect(getCoursePublicAuditStatusMeta(null)).toBeNull();
    expect(getCoursePublicAuditStatusMeta(undefined)).toBeNull();
    expect(getCoursePublicAuditStatusMeta('')).toBeNull();
    expect(getCoursePublicAuditStatusMeta(9)).toBeNull();
  });

  it('待审批对审批人是待处理，对申请的教师是进行中', () => {
    expect(getCoursePublicAuditStatusMeta(1, 'approver')).toEqual({
      label: '待审批',
      tone: 'waiting',
    });
    expect(getCoursePublicAuditStatusMeta(1, 'applicant')).toEqual({
      label: '待审批',
      tone: 'running',
    });
    expect(getCoursePublicAuditStatusMeta('1')).toEqual({
      label: '待审批',
      tone: 'running',
    });
  });

  it('审批通过为已完成，审批驳回为失败', () => {
    for (const audience of ['applicant', 'approver'] as const) {
      expect(getCoursePublicAuditStatusMeta(2, audience)).toEqual({
        label: '审批通过',
        tone: 'done',
      });
      expect(getCoursePublicAuditStatusMeta(3, audience)).toEqual({
        label: '审批驳回',
        tone: 'failed',
      });
    }
  });
});

describe('课程卡片状态', () => {
  it('已封存优先于审批状态', () => {
    expect(getCourseStatusMeta({ publicAuditStatus: 1, status: 1 })).toEqual({
      label: '已封存',
      tone: 'neutral',
    });
  });

  it('正常且无需审批的课程不显示状态', () => {
    expect(getCourseStatusMeta({ publicAuditStatus: 0, status: 0 })).toBeNull();
    expect(getCourseStatusMeta({})).toBeNull();
  });

  it('正常课程显示审批状态，按看的人区分类别', () => {
    expect(
      getCourseStatusMeta({ publicAuditStatus: 1, status: 0 }, 'approver'),
    ).toEqual({ label: '待审批', tone: 'waiting' });
    expect(getCourseStatusMeta({ publicAuditStatus: 3, status: '0' })).toEqual({
      label: '审批驳回',
      tone: 'failed',
    });
  });
});

describe('新建任务预览里单题的生成结果', () => {
  it('生成成功为已完成，失败要教师处理', () => {
    expect(getAiTaskPreviewItemStatusMeta(true)).toEqual({
      label: 'AI 已生成',
      tone: 'done',
    });
    expect(getAiTaskPreviewItemStatusMeta(false)).toEqual({
      label: '需编辑或重新生成',
      tone: 'waiting',
    });
    expect(getAiTaskPreviewItemStatusMeta(undefined).tone).toBe('waiting');
  });
});
