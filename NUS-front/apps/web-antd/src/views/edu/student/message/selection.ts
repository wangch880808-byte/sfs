import type { StudentConversation } from '#/api/edu/student/message';

export function findConversation(
  conversations: StudentConversation[],
  courseId: number,
  classId?: number,
) {
  return conversations.find(
    (item) =>
      item.courseId === courseId && (!classId || item.classId === classId),
  );
}
