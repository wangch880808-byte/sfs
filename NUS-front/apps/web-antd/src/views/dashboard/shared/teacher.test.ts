import type { EduCourseManageApi } from '#/api/edu/course-manage';
import type { EduHomeworkApi } from '#/api/edu/homework';
import type { EduHomeworkRecordApi } from '#/api/edu/homework-record';
import type { SystemNotifyMessageApi } from '#/api/system/notify/message';

import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';

import {
  buildTaskIndex,
  buildTeacherSummary,
  countDueSoon,
  groupPendingRecords,
  pickRecentSubmissions,
  pickStudentMessages,
  pickUpcomingTasks,
  summarizeTeacherCourses,
} from './teacher';

const at = (value: string) => dayjs(value).valueOf();
const NOW = at('2026-10-06 20:00:00');

const homework = (
  id: number,
  deadline: string,
  status = 1,
): EduHomeworkApi.Homework => ({
  autoGrade: 0,
  classId: 1,
  courseId: 1,
  deadline,
  homeworkLevel: 1,
  homeworkName: `任务${id}`,
  homeworkType: 1,
  id,
  passScore: 60,
  requirement: '',
  score: 100,
  status,
});

const homeworks = [
  homework(1, '2026-10-08 18:00:00'),
  homework(2, '2026-10-16 19:46:04'),
  homework(3, '2026-10-01 10:00:00', 2),
  homework(4, '2026-10-20 10:00:00', 0),
];

const courseDetail: EduCourseManageApi.CourseDetail = {
  classes: [
    {
      archived: false,
      classCourseId: 1,
      classId: 1,
      className: '软件工程 2025 级 1 班',
      studentCount: 3,
    },
    {
      archived: true,
      classCourseId: 2,
      classId: 2,
      className: '已封存的班',
      studentCount: 30,
    },
  ],
  courseId: 1,
  courseName: 'Web 前端开发实训',
  resources: [],
  tasks: [
    {
      classId: 1,
      homeworkId: 1,
      homeworkName: '任务1',
      studentCount: 3,
      submittedCount: 2,
    },
    {
      classId: 1,
      homeworkId: 2,
      homeworkName: '任务2',
      studentCount: 3,
      submittedCount: 0,
    },
  ],
};

const record = (
  id: number,
  homeworkId: number,
  status: number,
  submitTime: string,
  studentName = `学生${id}`,
): EduHomeworkRecordApi.HomeworkRecord => ({
  homeworkId,
  id,
  status,
  studentId: id,
  studentName,
  submitTime,
});

const index = buildTaskIndex({
  courseDetails: [courseDetail],
  courseOptions: [{ id: 1, name: '课程选项里的名字' }],
  homeworks,
});

describe('教师工作台：任务索引', () => {
  it('只收已发布与已截止的任务，补上课程名、班级名和提交人数', () => {
    expect([...index.keys()]).toEqual([1, 2, 3]);
    expect(index.get(1)).toMatchObject({
      className: '软件工程 2025 级 1 班',
      courseName: 'Web 前端开发实训',
      deadline: at('2026-10-08 18:00:00'),
      homeworkName: '任务1',
      status: 1,
      studentCount: 3,
      submittedCount: 2,
    });
  });

  it('没有课程详情时课程名取课程选项，班级名与人数为空', () => {
    const plain = buildTaskIndex({
      courseOptions: [{ id: 1, name: 'Web 前端开发实训' }],
      homeworks,
    });
    expect(plain.get(2)).toMatchObject({
      className: undefined,
      courseName: 'Web 前端开发实训',
      studentCount: undefined,
    });
  });
});

describe('教师工作台：待批改', () => {
  it('待批改与重新提交按任务归组，等得最久的在前；不在索引里的任务与其他状态不算', () => {
    const groups = groupPendingRecords(
      [
        record(1, 2, 1, '2026-10-06 19:00:00'),
        record(2, 1, 1, '2026-10-05 09:00:00'),
        record(3, 1, 4, '2026-10-06 08:00:00'),
        record(4, 1, 2, '2026-10-01 08:00:00'),
        record(5, 4, 1, '2026-10-01 08:00:00'),
        record(6, 99, 1, '2026-10-01 08:00:00'),
      ],
      index,
    );
    expect(groups).toEqual([
      {
        className: '软件工程 2025 级 1 班',
        count: 2,
        courseId: 1,
        courseName: 'Web 前端开发实训',
        homeworkId: 1,
        homeworkName: '任务1',
        oldestSubmitTime: at('2026-10-05 09:00:00'),
        resubmittedCount: 1,
      },
      expect.objectContaining({ count: 1, homeworkId: 2 }),
    ]);
  });

  it('没有提交时间的组排在后面', () => {
    const groups = groupPendingRecords(
      [
        { homeworkId: 2, id: 1, status: 1, studentId: 1 },
        record(2, 1, 1, '2026-10-06 19:00:00'),
      ],
      index,
    );
    expect(groups.map((group) => group.homeworkId)).toEqual([1, 2]);
  });
});

describe('教师工作台：即将截止', () => {
  it('只要已发布、还没截止的任务，按截止时间从近到远', () => {
    expect(
      pickUpcomingTasks(index, NOW).map((task) => task.homeworkId),
    ).toEqual([1, 2]);
    expect(pickUpcomingTasks(index, NOW, 1)).toHaveLength(1);
  });

  it('3 天内截止的数量', () => {
    expect(countDueSoon(index, NOW)).toBe(1);
    expect(countDueSoon(index, at('2026-10-14 20:00:00'))).toBe(1);
    expect(countDueSoon(index, at('2026-10-20 20:00:00'))).toBe(0);
  });
});

describe('教师工作台：最近提交', () => {
  it('按提交时间从新到旧，只保留索引里的任务，学生名兜底', () => {
    const items = pickRecentSubmissions(
      [
        record(1, 1, 2, '2026-10-05 09:00:00'),
        record(2, 2, 1, '2026-10-06 19:00:00'),
        { ...record(3, 1, 4, '2026-10-06 19:30:00'), studentName: '' },
        record(4, 4, 1, '2026-10-06 19:59:00'),
        { homeworkId: 1, id: 5, status: 1, studentId: 5 },
      ],
      index,
      2,
    );
    expect(items).toEqual([
      {
        homeworkId: 1,
        homeworkName: '任务1',
        recordId: 3,
        status: 4,
        studentName: '学生',
        submitTime: at('2026-10-06 19:30:00'),
      },
      expect.objectContaining({ recordId: 2, studentName: '学生2' }),
    ]);
  });
});

describe('教师工作台：学生留言', () => {
  const message = (
    id: number,
    templateCode: string,
    templateParams: SystemNotifyMessageApi.NotifyMessage['templateParams'],
  ) =>
    ({
      createTime: new Date(at('2026-10-06 19:46:10')),
      id,
      readStatus: false,
      templateCode,
      templateContent: `站内信正文${id}`,
      templateParams,
    }) as SystemNotifyMessageApi.NotifyMessage;

  it('只取“收到学生留言”，整理出学生、课程、内容和会话地址', () => {
    const items = pickStudentMessages([
      message(1, 'edu.teacher.message.created', {
        content: '老师，响应式布局要兼容多窄？',
        courseName: 'Web 前端开发实训',
        studentName: '李晓雨',
        targetUrl: '/edu/message?classId=1&courseId=1&studentId=3',
      }),
      message(2, 'edu.teacher.public-course.audit-result', {}),
      message(
        3,
        'edu.teacher.message.created',
        JSON.stringify({ studentName: '赵一鸣' }),
      ),
      message(4, 'edu.teacher.message.created', '不是 JSON'),
    ]);
    expect(items).toEqual([
      {
        content: '老师，响应式布局要兼容多窄？',
        courseName: 'Web 前端开发实训',
        createTime: at('2026-10-06 19:46:10'),
        id: 1,
        studentName: '李晓雨',
        targetUrl: '/edu/message?classId=1&courseId=1&studentId=3',
      },
      expect.objectContaining({
        content: '站内信正文3',
        courseName: undefined,
        id: 3,
        studentName: '赵一鸣',
        targetUrl: undefined,
      }),
      expect.objectContaining({ id: 4, studentName: '学生' }),
    ]);
  });
});

describe('教师工作台：我的课程', () => {
  it('班级不含已封存，学生数按班级合计，待批改按课程合计', () => {
    const groups = groupPendingRecords(
      [
        record(1, 1, 1, '2026-10-06 19:00:00'),
        record(2, 2, 4, '2026-10-06 19:00:00'),
      ],
      index,
    );
    expect(
      summarizeTeacherCourses(
        [
          { id: 1, name: 'Web 前端开发实训' },
          { id: 2, name: '没读到详情的课' },
        ],
        [courseDetail],
        groups,
      ),
    ).toEqual([
      {
        classCount: 1,
        courseId: 1,
        courseName: 'Web 前端开发实训',
        coverUrl: undefined,
        pendingCount: 2,
        studentCount: 3,
        taskCount: 2,
      },
      { courseId: 2, courseName: '没读到详情的课', pendingCount: 0 },
    ]);
  });

  it('没给待批改分组时不写待批改份数', () => {
    expect(
      summarizeTeacherCourses([{ id: 1, name: 'Web' }], [])[0]?.pendingCount,
    ).toBeUndefined();
  });
});

describe('教师工作台：页头说明', () => {
  it('列出要处理的事，没有就说没有；不用感叹号', () => {
    expect(buildTeacherSummary({ dueSoon: 1, pending: 2, unread: 1 })).toBe(
      '2 份提交待批改，1 条留言未读，1 项任务 3 天内截止。',
    );
    expect(buildTeacherSummary({ pending: 0, unread: 3 })).toBe(
      '3 条留言未读。',
    );
    expect(buildTeacherSummary({})).toBe('目前没有待批改的提交和未读留言。');
  });
});
