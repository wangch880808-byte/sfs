import { mount } from '@vue/test-utils';

import { describe, expect, it } from 'vitest';

import { EmptyState } from '.';

describe('emptyState', () => {
  it('三种空状态各有类名，标题与说明照常显示', () => {
    for (const kind of ['empty', 'filtered', 'blocked'] as const) {
      const wrapper = mount(EmptyState, {
        props: {
          description: '调整筛选条件后再试',
          kind,
          title: '没有符合条件的任务',
        },
      });
      expect(wrapper.classes()).toContain(`nus-empty--${kind}`);
      expect(wrapper.attributes('role')).toBe('status');
      expect(wrapper.find('.nus-empty__title').text()).toBe(
        '没有符合条件的任务',
      );
      expect(wrapper.find('.nus-empty__description').text()).toBe(
        '调整筛选条件后再试',
      );
    }
  });

  it('没有说明与操作时不渲染对应区域；操作放在 action 插槽', () => {
    const plain = mount(EmptyState, { props: { title: '还没有课程' } });
    expect(plain.find('.nus-empty__description').exists()).toBe(false);
    expect(plain.find('.nus-empty__action').exists()).toBe(false);
    const withAction = mount(EmptyState, {
      props: { compact: true, title: '还没有课程' },
      slots: { action: '<button>新建课程</button>' },
    });
    expect(withAction.classes()).toContain('is-compact');
    expect(withAction.find('.nus-empty__action').text()).toBe('新建课程');
  });
});
