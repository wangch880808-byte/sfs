import type { RouteLocationRaw, Router } from 'vue-router';

import { useRouter } from 'vue-router';

import { buildTaskRouteQuery } from '#/views/edu/student/homework/task-display';

/** 兜底的 404 路由名（router/routes/core.ts） */
const FALLBACK_ROUTE_NAME = 'FallbackNotFound';

/** 教师工作台用到的页面地址（菜单与静态路由里的现有地址） */
export const TEACHER_LINKS = {
  courseDetail: (courseId: number) => `/capacity/course/detail/${courseId}`,
  courseList: '/capacity/course_manager/course',
  correct: (homeworkId: number) =>
    `/capacity/homework_manager/homework/correct/${homeworkId}`,
  homeworkDetail: (homeworkId: number) =>
    `/capacity/homework_manager/homework/detail/${homeworkId}`,
  homeworkList: '/capacity/homework_manager/homework',
  messages: '/edu/message',
} as const;

/** 学生首页用到的页面地址 */
export const STUDENT_LINKS = {
  capacity: '/student/capacity',
  courseDetail: (courseId: number) => `/student/course/detail/${courseId}`,
  courseList: '/student/course',
  feedback: (homeworkId: number) => `/student/homework/feedback/${homeworkId}`,
  homeworkList: '/student/homework',
  messages: '/student/message',
  notifications: '/system/notify-message',
  /** 与任务列表一样带上课程和等级（?courseId=&level=），等级没解锁时提交页据此写明原因 */
  submit: (
    homeworkId: number,
    task?: Parameters<typeof buildTaskRouteQuery>[0],
  ) => {
    const query = new URLSearchParams(
      buildTaskRouteQuery(task ?? {}),
    ).toString();
    const path = `/student/homework/submit/${homeworkId}`;
    return query ? `${path}?${query}` : path;
  },
} as const;

/** 管理员看板用到的页面地址 */
export const ADMIN_LINKS = {
  courseList: '/capacity/course_manager/course',
  studentList: '/edu/student',
  teacherList: '/edu/teacher',
  teachingIssue: '/capacity/teaching-issue',
  teachingProcess: '/capacity/teaching-process',
} as const;

/**
 * 当前账号能不能打开这个地址：路由已注册（菜单里有，或是静态路由），不会落到 404。
 * 看板上的指标与面板只在能打开时才做成链接，菜单权限由菜单管理决定，这里不另做判断。
 */
export function isRouteAvailable(
  router: Pick<Router, 'resolve'>,
  to: RouteLocationRaw,
): boolean {
  try {
    const resolved = router.resolve(to);
    return resolved.matched.length > 0 && resolved.name !== FALLBACK_ROUTE_NAME;
  } catch {
    return false;
  }
}

/** 能打开时返回地址，打不开时返回 undefined（调用处据此决定显示成链接还是普通文字） */
export function useAvailableLink() {
  const router = useRouter();
  return <T extends RouteLocationRaw>(to: T): T | undefined =>
    isRouteAvailable(router, to) ? to : undefined;
}
