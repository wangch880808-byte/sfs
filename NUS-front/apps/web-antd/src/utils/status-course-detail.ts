import type { StatusMeta } from './status';

import { ARCHIVE_COPY } from './archive';

/**
 * 课程详情、班级详情与课程资源的状态呈现（V1.0.1.6 界面规范）。类别沿用 utils/status.ts：
 * 进行中为 running，未开始与已结束为 neutral，已封存为 neutral；上传中 running、上传完成 done、上传失败 failed。
 */

/** 班课的学期状态：后端按学期起止日期给出 */
export const CLASS_COURSE_STATUS = {
  ENDED: '已结束',
  NOT_STARTED: '未开始',
  RUNNING: '进行中',
} as const;

const CLASS_COURSE_STATUS_META: Record<string, StatusMeta> = {
  [CLASS_COURSE_STATUS.ENDED]: { label: '已结束', tone: 'neutral' },
  [CLASS_COURSE_STATUS.NOT_STARTED]: { label: '未开始', tone: 'neutral' },
  [CLASS_COURSE_STATUS.RUNNING]: { label: '进行中', tone: 'running' },
};

/** 班级在课程里的状态；班课绑定或班级已封存时显示“已封存” */
export function getClassCourseStatusMeta(
  status: null | string | undefined,
  archived = false,
): StatusMeta {
  if (archived) {
    return { label: ARCHIVE_COPY.archived, tone: 'neutral' };
  }
  const key = (status ?? '').trim();
  return (
    CLASS_COURSE_STATUS_META[key] ?? {
      label: key || '未设置',
      tone: 'neutral',
    }
  );
}

export type ResourceUploadState = 'error' | 'success' | 'uploading';

/** 课程资源上传队列里单个文件的状态；上传中带上进度 */
export function getResourceUploadStatusMeta(task: {
  percent?: null | number;
  status: ResourceUploadState;
}): StatusMeta {
  switch (task.status) {
    case 'error': {
      return { label: '上传失败', tone: 'failed' };
    }
    case 'success': {
      return { label: '上传完成', tone: 'done' };
    }
    default: {
      const percent = Math.min(100, Math.max(0, Number(task.percent) || 0));
      return { label: `上传中 ${percent}%`, tone: 'running' };
    }
  }
}
