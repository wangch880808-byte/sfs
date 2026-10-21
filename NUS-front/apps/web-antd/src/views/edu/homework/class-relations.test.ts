import type { EduHomeworkApi } from '#/api/edu/homework';

import { describe, expect, it } from 'vitest';

import {
  buildBatchCreatePayloads,
  buildCopyFormValues,
  buildRelationOptions,
  indexRelations,
  normalizeSelectedIds,
  relationsFromCopyTargets,
  resolveSelectedRelations,
} from './class-relations';

/** 两条班课：软件 1 班的班级编号 12 恰好等于数媒 2 班那条班课的编号 12 */
const SOFTWARE_1 = {
  classCourseId: 7,
  classId: 12,
  className: '软件 1 班',
  semesterName: '2026-2027 上学期',
  status: '进行中',
};
const MEDIA_2 = {
  classCourseId: 12,
  classId: 30,
  className: '数媒 2 班',
  semesterName: '2026-2027 上学期',
  status: '进行中',
};
const RELATIONS = [SOFTWARE_1, MEDIA_2];

const BASE_PAYLOAD: EduHomeworkApi.Homework = {
  autoGrade: 0,
  classId: 0,
  courseId: 5,
  deadline: '2026-10-20 23:59:59',
  homeworkLevel: 1,
  homeworkName: '组件化重构',
  homeworkType: 1,
  passScore: 60,
  requirement: '<p>内容</p>',
  score: 100,
};

describe('选中的班级换成班课', () => {
  it('只按班课编号查：选了软件 1 班（班课 7），不会因为班级编号 12 撞上数媒 2 班的班课 12', () => {
    const map = indexRelations(RELATIONS);
    expect(
      resolveSelectedRelations([7], map).map((item) => item.className),
    ).toEqual(['软件 1 班']);
    // 选了数媒 2 班（班课 12），得到的是数媒 2 班，不是班级编号为 12 的软件 1 班
    expect(resolveSelectedRelations([12], map)).toEqual([MEDIA_2]);
  });

  it('按班级编号传进来的值不再兜底：查不到就丢掉', () => {
    const map = indexRelations(RELATIONS);
    // 30 是数媒 2 班的班级编号，不是任何班课编号
    expect(resolveSelectedRelations([30], map)).toEqual([]);
  });

  it('单个值、字符串、重复与无效值都能处理，保持选择顺序', () => {
    const map = indexRelations(RELATIONS);
    expect(
      resolveSelectedRelations(['12', 7, 12, 0, null, 'x'], map).map(
        (item) => item.classCourseId,
      ),
    ).toEqual([12, 7]);
    expect(resolveSelectedRelations(7, map)).toEqual([SOFTWARE_1]);
    expect(resolveSelectedRelations(undefined, map)).toEqual([]);
    expect(normalizeSelectedIds([3, '3', -1, 1.5])).toEqual([3]);
  });
});

describe('班级选项', () => {
  it('值是班课编号；有状态时写在名称后面，没有状态只写名称', () => {
    expect(
      buildRelationOptions([
        SOFTWARE_1,
        { classCourseId: 9, classId: 40, className: '网络 3 班' },
        { classCourseId: 10, classId: 41 },
      ]),
    ).toEqual([
      { label: '软件 1 班（进行中）', value: 7 },
      { label: '网络 3 班', value: 9 },
      { label: '班级 10', value: 10 },
    ]);
  });
});

describe('复制任务的可选班级', () => {
  const targets = [
    { classCourseId: 7, classId: 12, className: '软件 1 班', courseId: 5 },
    { classCourseId: 8, classId: 13, className: '软件 2 班', courseId: 6 },
    { classCourseId: 7, classId: 12, className: '重复', courseId: 5 },
    { classCourseId: 0, classId: 14, className: '无效', courseId: 5 },
    {
      classCourseId: 12,
      classId: 30,
      className: '数媒 2 班',
      courseId: 5,
      semesterName: '2026-2027 上学期',
    },
  ];

  it('只留来源任务那门课的，去重、丢掉编号不全的', () => {
    expect(relationsFromCopyTargets(targets, 5)).toEqual([
      {
        classCourseId: 7,
        classId: 12,
        className: '软件 1 班',
        semesterName: undefined,
      },
      {
        classCourseId: 12,
        classId: 30,
        className: '数媒 2 班',
        semesterName: '2026-2027 上学期',
      },
    ]);
  });

  it('课程编号无效或没有目标时为空', () => {
    expect(relationsFromCopyTargets(targets, undefined)).toEqual([]);
    expect(relationsFromCopyTargets(undefined, 5)).toEqual([]);
    expect(relationsFromCopyTargets(targets, 99)).toEqual([]);
  });
});

describe('多班级新建请求', () => {
  it('每个班课一条：带班级、学期、班课编号，不带任务编号与状态', () => {
    const payloads = buildBatchCreatePayloads(
      { ...BASE_PAYLOAD, id: 99, status: 0 },
      RELATIONS,
    );
    expect(payloads).toHaveLength(2);
    expect(payloads[0]).toMatchObject({
      classCourseId: 7,
      classId: 12,
      semesterName: '2026-2027 上学期',
    });
    expect(payloads[1]).toMatchObject({ classCourseId: 12, classId: 30 });
    for (const item of payloads) {
      expect(item).not.toHaveProperty('id');
      expect(item).not.toHaveProperty('status');
      expect(item).not.toHaveProperty('sourceHomeworkId');
    }
  });

  it('复制时带上来源任务编号', () => {
    const payloads = buildBatchCreatePayloads(BASE_PAYLOAD, [MEDIA_2], 42);
    expect(payloads).toEqual([
      {
        ...BASE_PAYLOAD,
        classCourseId: 12,
        classId: 30,
        semesterName: '2026-2027 上学期',
        sourceHomeworkId: 42,
      },
    ]);
  });
});

describe('复制任务的表单初始值', () => {
  const source: EduHomeworkApi.Homework = {
    ...BASE_PAYLOAD,
    aiConfidenceThreshold: 0.85,
    aiReferenceJson: '参考答案',
    aiReviewMode: 0,
    aiRubricJson: '评分标准',
    autoGrade: 1,
    classId: 12,
    id: 42,
    indicatorFirstId: 3,
    indicatorSecondId: 31,
    passScore: 70,
    score: 120,
    status: 1,
    submissionMode: 'image',
  };

  it('除班级、截止时间外都取来源任务的值', () => {
    const values = buildCopyFormValues(source);
    expect(values).toMatchObject({
      aiConfidenceThreshold: 0.85,
      aiReferenceJson: '参考答案',
      aiReviewMode: 1,
      aiRubricJson: '评分标准',
      autoGrade: 1,
      courseId: 5,
      gradingStrategy: 'assist',
      homeworkLevel: 1,
      homeworkName: '组件化重构',
      homeworkType: 1,
      indicatorFirstId: 3,
      indicatorSecondId: 31,
      passScore: 70,
      requiredAttachmentTypesJson: JSON.stringify(['image']),
      requirement: '<p>内容</p>',
      score: 120,
      submissionMode: 'image',
    });
    expect(values.classId).toBeUndefined();
    expect(values.deadline).toBeUndefined();
    expect(values).not.toHaveProperty('id');
    expect(values).not.toHaveProperty('status');
  });

  it('没有指标与 AI 设置时给默认值', () => {
    const values = buildCopyFormValues({
      ...BASE_PAYLOAD,
      autoGrade: 0,
    });
    expect(values).toMatchObject({
      aiConfidenceThreshold: 0.7,
      aiReferenceJson: '',
      aiRubricJson: '',
      gradingStrategy: 'manual',
      submissionMode: 'text',
    });
    expect(values.indicatorFirstId).toBeUndefined();
    expect(values.indicatorSecondId).toBeUndefined();
  });
});
