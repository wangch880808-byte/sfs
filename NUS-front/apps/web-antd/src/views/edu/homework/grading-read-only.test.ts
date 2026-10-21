import { flushPromises, mount } from '@vue/test-utils';

import { describe, expect, it, vi } from 'vitest';

import GradingAiPanel from './grading-ai-panel.vue';
import GradingDetailFooter from './grading-detail-footer.vue';
import GradingRecordList from './grading-record-list.vue';

/**
 * 只读批改页（不是布置者）：左栏、底部操作栏与 AI 初评区块都不出现会写入的操作。
 */
const api = vi.hoisted(() => ({
  calibration: vi.fn(),
  feedback: vi.fn(),
  getReview: vi.fn(),
  reject: vi.fn(),
  trigger: vi.fn(),
}));

vi.mock('#/api/edu/ai-review', () => ({
  getAiCalibration: api.calibration,
  submitAiFeedback: api.feedback,
}));
vi.mock('#/api/edu/homework-ai-review', () => ({
  getHomeworkAiReviewByRecord: api.getReview,
  rejectHomeworkAiReview: api.reject,
  triggerHomeworkAiReview: api.trigger,
}));

/** 按钮文字（antd 会在两个汉字之间自动加空格，比较时去掉） */
function buttonTexts(wrapper: ReturnType<typeof mount>) {
  return wrapper
    .findAll('button')
    .map((button) => button.text().replaceAll(/\s+/g, ''));
}

const FOOTER_PROPS = {
  aiApplying: false,
  canCorrect: true,
  formReady: true,
  redoDisabled: false,
  redoLabel: '打回重做',
  submitting: false,
  undoError: '',
  undoing: false,
};

describe('批改页底部操作栏', () => {
  it('布置者：打回、应用 AI 初评、保存、保存并下一份都在', () => {
    const wrapper = mount(GradingDetailFooter, { props: FOOTER_PROPS });
    expect(buttonTexts(wrapper)).toEqual([
      '打回重做',
      '应用AI初评',
      '保存',
      '保存并下一份',
    ]);
  });

  it('只读：没有任何操作按钮，只留切换学生的快捷键说明', () => {
    const wrapper = mount(GradingDetailFooter, {
      props: { ...FOOTER_PROPS, readOnly: true },
    });
    expect(wrapper.findAll('button')).toHaveLength(0);
    expect(wrapper.text()).toContain('↑ ↓ 切换学生');
    expect(wrapper.text()).not.toContain('保存');
  });
});

const RECORDS = [
  { id: 1, status: 1, studentName: '甲', studentNo: 'S1' },
  { id: 2, status: 2, studentName: '乙', studentNo: 'S2', score: 80 },
];

function mountList(readOnly?: boolean) {
  return mount(GradingRecordList, {
    props: {
      checkedIds: [1],
      draftIds: new Set<number>(),
      keyword: '',
      loading: false,
      readOnly,
      records: RECORDS,
      statusFilter: 'all',
      submitting: false,
    } as any,
  });
}

describe('批改页左栏', () => {
  it('布置者：每行有勾选框，勾选后能批量打回', () => {
    const wrapper = mountList();
    expect(wrapper.findAll('.grading-row__check')).toHaveLength(2);
    expect(buttonTexts(wrapper)).toContain('打回所选1份');
  });

  it('只读：没有勾选框，也没有批量打回', () => {
    const wrapper = mountList(true);
    expect(wrapper.findAll('.grading-row__check')).toHaveLength(0);
    expect(wrapper.find('.grading-list__selection').exists()).toBe(false);
    expect(wrapper.text()).toContain('甲');
  });
});

async function mountAiPanel(readOnly?: boolean) {
  api.getReview.mockResolvedValue({
    aiComment: '结构完整',
    aiScore: 85,
    homeworkRecordId: 2,
    status: 2,
  });
  api.calibration.mockResolvedValue({});
  const wrapper = mount(GradingAiPanel, {
    props: {
      canAiReview: !readOnly,
      homeworkId: 7,
      readOnly,
      recordId: 2,
      recordStatus: 2,
      reloadToken: 0,
    },
  });
  await flushPromises();
  return wrapper;
}

describe('批改页 AI 初评区块', () => {
  it('布置者：能触发、填入、忽略与反馈', async () => {
    const texts = buttonTexts(await mountAiPanel());
    expect(texts).toEqual(
      expect.arrayContaining([
        '手动触发初评',
        '填入批改表单',
        '忽略AI初评',
        '加严',
        '正常',
        '放宽',
      ]),
    );
  });

  it('只读：能看到初评内容，但没有触发、填入、忽略与反馈', async () => {
    api.calibration.mockClear();
    const wrapper = await mountAiPanel(true);
    expect(wrapper.text()).toContain('结构完整');
    expect(buttonTexts(wrapper)).toEqual([]);
    expect(api.calibration).not.toHaveBeenCalled();
    await (wrapper.vm as any).applyOrTrigger();
    expect(api.trigger).not.toHaveBeenCalled();
  });
});
