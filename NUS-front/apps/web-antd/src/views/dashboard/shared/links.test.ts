import { createMemoryHistory, createRouter } from 'vue-router';

import { describe, expect, it, vi } from 'vitest';

import { isRouteAvailable, STUDENT_LINKS, TEACHER_LINKS } from './links';

const Empty = { render: () => null };

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { component: Empty, name: 'EduMessage', path: '/edu/message' },
      {
        component: Empty,
        name: 'EduHomeworkCorrect',
        path: '/capacity/homework_manager/homework/correct/:homeworkId',
      },
      {
        component: Empty,
        name: 'FallbackNotFound',
        path: '/:path(.*)*',
      },
    ],
  });
}

describe('看板链接', () => {
  it('注册过的地址能打开，带参数与查询串也行', () => {
    const router = createTestRouter();
    expect(isRouteAvailable(router, TEACHER_LINKS.messages)).toBe(true);
    expect(
      isRouteAvailable(router, '/edu/message?classId=1&courseId=1&studentId=3'),
    ).toBe(true);
    expect(isRouteAvailable(router, TEACHER_LINKS.correct(1))).toBe(true);
  });

  it('落到 404 兜底路由、没有匹配或解析出错的地址打不开', () => {
    // 没有匹配时 vue-router 会打一条警告，这里是预期内的
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const router = createTestRouter();
    expect(isRouteAvailable(router, STUDENT_LINKS.homeworkList)).toBe(false);
    expect(
      isRouteAvailable(
        createRouter({ history: createMemoryHistory(), routes: [] }),
        '/edu/message',
      ),
    ).toBe(false);
    expect(isRouteAvailable(router, { name: '不存在的路由' })).toBe(false);
    warn.mockRestore();
  });

  it('地址与现有页面一致', () => {
    expect(TEACHER_LINKS.correct(3)).toBe(
      '/capacity/homework_manager/homework/correct/3',
    );
    expect(TEACHER_LINKS.homeworkDetail(3)).toBe(
      '/capacity/homework_manager/homework/detail/3',
    );
    expect(TEACHER_LINKS.courseDetail(1)).toBe('/capacity/course/detail/1');
    expect(STUDENT_LINKS.submit(2)).toBe('/student/homework/submit/2');
    expect(STUDENT_LINKS.feedback(2)).toBe('/student/homework/feedback/2');
    expect(STUDENT_LINKS.courseDetail(1)).toBe('/student/course/detail/1');
  });

  it('学生首页去提交时与任务列表一样带上课程和等级', () => {
    expect(STUDENT_LINKS.submit(2, { courseId: 3, homeworkLevel: 2 })).toBe(
      '/student/homework/submit/2?courseId=3&level=2',
    );
    // 缺课程时只带等级；等级为 0 也照带，与任务列表一致
    expect(STUDENT_LINKS.submit(2, { homeworkLevel: 0 })).toBe(
      '/student/homework/submit/2?level=0',
    );
    expect(
      STUDENT_LINKS.submit(2, { courseId: null, homeworkLevel: null }),
    ).toBe('/student/homework/submit/2');
  });

  it('带查询串的提交页地址照样能判断能不能打开', () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          component: Empty,
          name: 'StudentHomeworkSubmit',
          path: '/student/homework/submit/:homeworkId',
        },
      ],
    });
    const link = STUDENT_LINKS.submit(5, { courseId: 7, homeworkLevel: 3 });
    expect(isRouteAvailable(router, link)).toBe(true);
    expect(router.resolve(link).query).toEqual({ courseId: '7', level: '3' });
  });
});
