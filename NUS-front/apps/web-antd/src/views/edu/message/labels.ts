/** 留言页面统一文案（教师“留言互动”与学生“我的留言”共用）。 */
export const messageLabels = {
  closed: '留言已关闭',
  reopened: '留言已重新开启',
  send: '发送',
  close: '关闭留言',
  reopen: '重新开启',
  contactTeacher: '联系老师',
  myMessages: '我的留言',
  empty: '还没有留言',
  selectConversation: '选择一门课程查看留言',
  placeholder: '输入留言，按 Enter 发送，Shift + Enter 换行',

  // 页头
  teacherTitle: '留言互动',
  teacherDescription: '查看学生在课程里的留言并回复，问题解决后可以关闭留言。',
  studentDescription: '按课程给任课老师留言，老师回复后会显示在这里。',

  // 留言列表
  conversationList: '课程留言',
  searchStudent: '搜索学生姓名',
  selectClass: '选择班级',
  selectCourse: '选择课程',
  loadingStudents: '正在读取留言学生…',
  loadingConversations: '正在读取课程留言…',
  loadingMessages: '正在读取留言…',
  needClassCourse: '先选择班级和课程',
  needClassCourseHint: '在右上角选好班级与课程后，这里会列出给你留言的学生。',
  noStudents: '还没有学生留言',
  noStudentsHint: '学生在课程里留言后，会按时间出现在这里。',
  noMatchedStudents: '没有找到匹配的学生',
  noMatchedStudentsHint: '换个姓名再试，或清空搜索查看全部留言学生。',
  clearSearch: '清空搜索',
  noConversations: '还没有可以留言的课程',
  noConversationsHint: '加入班级课程后，可以在这里给任课老师留言。',
  viewCourses: '查看我的课程',

  // 对话区
  noSelection: '还没有打开留言',
  noSelectionTeacherHint: '从左侧选择一位学生，查看留言并回复。',
  noScopeTeacherHint: '先在右上角选择班级和课程，再从左侧选择一位学生。',
  noSelectionStudentHint: '从左侧选择一门课程，查看你和老师的留言。',
  emptyTeacherHint: '你的回复和学生的留言会按时间显示在这里。',
  emptyStudentHint: '你的留言和老师的回复会按时间显示在这里。',
  closedTeacherHint:
    '留言已关闭，学生暂时不能继续留言。需要继续交流时，点右上角的「重新开启」。',
  closedStudentHint: '老师已关闭留言，你暂时不能再发新的留言。',
  unread: (count: number) => `${count} 条未读`,

  // 关闭留言确认
  closeConfirmTitle: (name: string) => `关闭与${name}的留言？`,
  closeConfirmContent:
    '关闭后学生不能继续留言，已有记录会保留；需要时可以随时重新开启。',
  confirmCancel: '返回',
} as const;
