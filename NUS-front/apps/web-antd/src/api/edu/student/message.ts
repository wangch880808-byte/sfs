import type { EduMessageApi } from '#/api/edu/message';

import { requestClient } from '#/api/request';

export interface StudentConversation {
  classId: number;
  courseId: number;
  className: string;
  courseName: string;
  teacherName?: string;
  lastMessageContent?: string;
  lastMessageTime?: string;
  unreadCount: number;
  status: number;
}

export function getStudentConversations() {
  return requestClient.get<StudentConversation[]>('/edu/student/message/list');
}

export function getStudentMessageDetail(params: {
  classId: number;
  courseId: number;
}) {
  return requestClient.get<EduMessageApi.ConversationDetail>(
    '/edu/student/message/detail',
    { params },
  );
}

export function sendStudentMessage(data: {
  classId: number;
  content: string;
  courseId: number;
}) {
  return requestClient.post('/edu/student/message/send', data);
}

export function markStudentMessageRead(params: {
  classId: number;
  courseId: number;
}) {
  return requestClient.put('/edu/student/message/read', undefined, { params });
}
