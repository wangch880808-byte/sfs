import { mount } from '@vue/test-utils';

import { describe, expect, it, vi } from 'vitest';

import TaskLoadState from './task-load-state.vue';

const push = vi.fn();

vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
}));

/** 按钮文字（antd 会在两个汉字之间自动加空格，比较时去掉） */
function buttonTexts(wrapper: ReturnType<typeof mount>) {
  return wrapper
    .findAll('button')
    .map((button) => button.text().replaceAll(/\s+/g, ''));
}

async function clickButton(wrapper: ReturnType<typeof mount>, index: number) {
  await wrapper.findAll('button').at(index)?.trigger('click');
}

describe('taskLoadState', () => {
  it('读取中写明在读什么', () => {
    const wrapper = mount(TaskLoadState, {
      props: { loading: true, loadingText: '正在读取成绩反馈…' },
    });
    expect(wrapper.attributes('role')).toBe('status');
    expect(wrapper.text()).toContain('正在读取成绩反馈…');
  });

  it('等级未解锁：说明原因与怎么解锁，可回任务列表或去我的课程', async () => {
    push.mockClear();
    const wrapper = mount(TaskLoadState, {
      props: {
        error: {
          kind: 'locked',
          reason: '该等级尚未解锁，请先完成上一等级任务。',
        },
      },
    });
    expect(wrapper.find('.nus-empty--blocked').exists()).toBe(true);
    expect(wrapper.find('.nus-empty__title').text()).toBe('这个任务还没有解锁');
    const description = wrapper.find('.nus-empty__description').text();
    expect(description).toContain('该等级尚未解锁，请先完成上一等级任务。');
    expect(description).toContain('等级闯关');
    expect(buttonTexts(wrapper)).toEqual(['返回任务列表', '查看我的课程']);
    await clickButton(wrapper, 0);
    expect(push).toHaveBeenCalledWith('/student/homework');
    await clickButton(wrapper, 1);
    expect(push).toHaveBeenCalledWith('/student/course');
  });

  it('任务不存在或没有分配：只给回任务列表', () => {
    const wrapper = mount(TaskLoadState, {
      props: { error: { kind: 'unavailable', reason: '' } },
    });
    expect(wrapper.find('.nus-empty__title').text()).toBe('没有找到这个任务');
    expect(buttonTexts(wrapper)).toEqual(['返回任务列表']);
  });

  it('读取失败：带上原因，可以重试', async () => {
    const wrapper = mount(TaskLoadState, {
      props: { error: { kind: 'failed', reason: '系统异常。' } },
    });
    expect(wrapper.find('.nus-empty__title').text()).toBe('任务没有读取成功');
    expect(wrapper.find('.nus-empty__description').text()).toBe(
      '系统异常。可以重试，或返回任务列表。',
    );
    expect(buttonTexts(wrapper)).toEqual(['重试', '返回任务列表']);
    await clickButton(wrapper, 0);
    expect(wrapper.emitted('retry')).toHaveLength(1);
  });

  it('没有错误也不在读取时不显示任何内容', () => {
    const wrapper = mount(TaskLoadState);
    expect(wrapper.find('.nus-empty').exists()).toBe(false);
    expect(wrapper.text()).toBe('');
  });
  it('等级未解锁时显示该等级的具体原因', () => {
    const wrapper = mount(TaskLoadState, {
      props: {
        error: {
          kind: 'locked',
          reason: '该等级尚未解锁，请先完成上一等级任务。',
        },
        lockedDetail: '还差 2 个任务达标。',
      },
    });
    expect(wrapper.find('.nus-empty__description').text()).toContain(
      '请先完成上一等级任务。还差 2 个任务达标。达标率达到要求后自动解锁',
    );
  });
});
