import { requestClient } from '#/api/request';

export namespace EduMessageApi {
  /** 留言学生 */
  export interface MessageStudent {
    studentId: number;
    studentName: string;
    avatar?: string;
    lastMessageTime?: string;
    lastMessageContent?: string;
    unreadCount: number;
  }

  /** 留言详情 */
  export interface MessageDetail {
    id: number;
    studentId: number;
    teacherId: number;
    content: string;
    type: number; // 0=学生留言, 1=教师回复
    createTime: string;
    senderName?: string;
    readStatus: number;
  }

  export interface ConversationDetail {
    status: number;
    studentName: string;
    messages: MessageDetail[];
  }

  /** 教师回复请求 */
  export interface ReplyReqVO {
    classId: number;
    courseId: number;
    studentId: number;
    content: string;
  }
}

/** 留言学生列表 */
export function getMessageStudentList(params: {
  classId: number;
  courseId: number;
  name?: string;
}) {
  return requestClient.get<EduMessageApi.MessageStudent[]>(
    '/edu/message/student-list',
    { params },
  );
}

/** 留言详情 */
export function getMessageDetail(params: {
  classId: number;
  courseId: number;
  studentId: number;
}) {
  return requestClient.get<EduMessageApi.ConversationDetail>(
    '/edu/message/detail',
    { params },
  );
}

/** 教师回复 */
export function replyMessage(data: EduMessageApi.ReplyReqVO) {
  return requestClient.post('/edu/message/reply', data);
}

/** 关闭会话 */
export function closeConversation(params: {
  classId: number;
  courseId: number;
  studentId: number;
}) {
  return requestClient.put('/edu/message/close', undefined, { params });
}

/** 重新开启会话 */
export function reopenConversation(params: {
  classId: number;
  courseId: number;
  studentId: number;
}) {
  return requestClient.put('/edu/message/reopen', undefined, { params });
}
