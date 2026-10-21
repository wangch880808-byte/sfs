import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/student/news/detail/:id',
    component: () => import('#/views/edu/student/news/detail.vue'),
    name: 'StudentNewsDetail',
    meta: {
      title: '资讯详情',
      activePath: '/student/news',
      hideInMenu: true,
    },
  },
  {
    path: '/student/course/detail/:courseId',
    component: () => import('#/views/edu/student/course/detail.vue'),
    name: 'StudentCourseDetail',
    meta: {
      title: '课程详情',
      activePath: '/student/course',
      hideInMenu: true,
    },
  },
  {
    path: '/student/course/indicator/detail/:indicatorId',
    component: () => import('#/views/edu/student/course/indicator-detail.vue'),
    name: 'StudentCourseIndicatorDetail',
    meta: {
      title: '指标课程详情',
      activePath: '/student/course',
      hideInMenu: true,
    },
  },
  {
    path: '/student/homework/submit/:homeworkId',
    component: () => import('#/views/edu/student/homework/submit.vue'),
    name: 'StudentHomeworkSubmit',
    meta: {
      title: '任务提交',
      activePath: '/student/homework',
      hideInMenu: true,
    },
  },
  {
    path: '/student/homework/feedback/:homeworkId',
    component: () => import('#/views/edu/student/homework/feedback.vue'),
    name: 'StudentHomeworkFeedback',
    meta: {
      title: '成绩反馈',
      activePath: '/student/homework',
      hideInMenu: true,
    },
  },
  {
    path: '/student/capacity/report',
    component: () => import('#/views/edu/student/capacity/report.vue'),
    name: 'StudentCapacityReport',
    meta: {
      title: '实训报告',
      activePath: '/student/capacity',
      hideInMenu: true,
    },
  },
];

export default routes;
