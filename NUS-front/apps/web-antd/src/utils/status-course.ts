import type { StatusMeta } from './status';

import { ARCHIVE_COPY, isArchived } from './archive';

/**
 * 实训课程列表与课程弹窗的状态呈现（V1.0.1.6 界面规范）。类别沿用 utils/status.ts：
 * 已封存 neutral；公共课申请待审批时，对审批人是等他处理（waiting），对申请的教师是在等管理员（running）；
 * 审批通过 done、审批驳回 failed。正常、无需审批的课程不显示状态。
 */

/** 公共课申请里看状态的人：审批人（管理员）或申请人（教师） */
export type CourseAuditAudience = 'applicant' | 'approver';

/** 公共课审批状态（capacity_course.public_audit_status） */
export const COURSE_PUBLIC_AUDIT_STATUS = {
  NONE: 0,
  PENDING: 1,
  APPROVED: 2,
  REJECTED: 3,
} as const;

const PUBLIC_AUDIT_STATUS_META: Record<
  CourseAuditAudience,
  Record<number, StatusMeta>
> = {
  applicant: {
    1: { label: '待审批', tone: 'running' },
    2: { label: '审批通过', tone: 'done' },
    3: { label: '审批驳回', tone: 'failed' },
  },
  approver: {
    1: { label: '待审批', tone: 'waiting' },
    2: { label: '审批通过', tone: 'done' },
    3: { label: '审批驳回', tone: 'failed' },
  },
};

/** 公共课审批状态；无需审批或取值不认识时返回 null（不显示） */
export function getCoursePublicAuditStatusMeta(
  status: null | number | string | undefined,
  audience: CourseAuditAudience = 'applicant',
): null | StatusMeta {
  if (status === null || status === undefined || status === '') {
    return null;
  }
  return PUBLIC_AUDIT_STATUS_META[audience][Number(status)] ?? null;
}

/** 课程卡片上的状态：已封存优先，其次是公共课审批状态；正常的课程返回 null */
export function getCourseStatusMeta(
  course: {
    publicAuditStatus?: null | number | string;
    status?: null | number | string;
  },
  audience: CourseAuditAudience = 'applicant',
): null | StatusMeta {
  if (isArchived(Number(course.status))) {
    return { label: ARCHIVE_COPY.archived, tone: 'neutral' };
  }
  return getCoursePublicAuditStatusMeta(course.publicAuditStatus, audience);
}

/** AI 新建任务预览里单道题的生成结果：生成失败的题要教师补全或重新生成 */
export function getAiTaskPreviewItemStatusMeta(
  success: boolean | null | undefined,
): StatusMeta {
  return success
    ? { label: 'AI 已生成', tone: 'done' }
    : { label: '需编辑或重新生成', tone: 'waiting' };
}
