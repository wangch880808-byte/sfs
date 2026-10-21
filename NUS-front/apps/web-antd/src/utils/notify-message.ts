import type { SystemNotifyMessageApi } from '#/api/system/notify/message';

const NOTIFY_TITLES: Record<string, string> = {
  'edu.admin.public-course.pending': '公共课待审批',
  'edu.student.class.joined': '已加入班级',
  'edu.student.course.assigned': '新课程已分配',
  'edu.student.homework.corrected': '实训任务已批改',
  'edu.student.homework.created': '新实训任务发布',
  'edu.student.homework.extended': '实训任务个别延期',
  'edu.student.homework.redo': '实训任务打回重做',
  'edu.student.homework.remind': '实训任务催交提醒',
  'edu.student.homework.updated': '实训任务内容变更',
  'edu.student.message.reply': '教师回复留言',
  'edu.teacher.homework.ai-review-failed': 'AI 初评没有完成',
  'edu.teacher.homework.submitted': '新提交待批改',
  'edu.teacher.message.created': '收到学生留言',
  'edu.teacher.public-course.audit-result': '公共课审批结果',
};

const LEGACY_NOTIFY_TARGETS: Record<string, string> = {
  '/capacity/course': '/capacity/course_manager/course',
};

function parseTemplateParams(
  value: SystemNotifyMessageApi.NotifyMessage['templateParams'],
) {
  if (typeof value !== 'string') {
    return value ?? {};
  }
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object'
      ? (parsed as Record<string, unknown>)
      : {};
  } catch {
    return {};
  }
}

export function getNotifyTitle(item: SystemNotifyMessageApi.NotifyMessage) {
  return (
    NOTIFY_TITLES[item.templateCode] || item.templateNickname || '系统通知'
  );
}

export function getNotifyTargetUrl(item: SystemNotifyMessageApi.NotifyMessage) {
  const targetUrl = parseTemplateParams(item.templateParams).targetUrl;
  if (typeof targetUrl !== 'string' || !targetUrl.startsWith('/')) {
    return undefined;
  }
  return LEGACY_NOTIFY_TARGETS[targetUrl] ?? targetUrl;
}
