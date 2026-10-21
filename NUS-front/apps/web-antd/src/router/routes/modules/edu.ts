import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/capacity/course/detail/:courseId',
    component: () => import('#/views/capacity/course/detail.vue'),
    name: 'CapacityCourseDetail',
    meta: {
      title: '课程详情',
      icon: 'ant-design:book-outlined',
      activePath: '/capacity/course_manager/course',
      hideInMenu: true,
    },
  },
  {
    path: '/capacity/course/class/detail/:classCourseId',
    component: () => import('#/views/capacity/course/class-detail.vue'),
    name: 'CapacityCourseClassDetail',
    meta: {
      title: '班级参与详情',
      icon: 'ant-design:team-outlined',
      activePath: '/capacity/course_manager/course',
      hideInMenu: true,
    },
  },
  {
    path: '/capacity/homework_manager/homework/detail/:id',
    component: () => import('#/views/edu/homework/[id].vue'),
    name: 'EduHomeworkDetail',
    meta: {
      title: '实训任务详情',
      icon: 'ant-design:file-text-outlined',
      activePath: '/capacity/homework_manager/homework',
      hideInMenu: true,
    },
  },
  {
    path: '/capacity/homework_manager/homework/correct/:homeworkId',
    component: () => import('#/views/edu/homework/correct.vue'),
    name: 'EduHomeworkCorrect',
    meta: {
      title: '实训任务批改',
      icon: 'ant-design:edit-outlined',
      activePath: '/capacity/homework_manager/homework',
      hideInMenu: true,
    },
  },
];

export default routes;
