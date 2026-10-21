import { mount } from '@vue/test-utils';

import { describe, expect, it } from 'vitest';

import { StatusTag } from '.';

describe('statusTag', () => {
  it('圆点＋文字，类别决定样式类名，默认中性', () => {
    const wrapper = mount(StatusTag, { props: { label: '未提交' } });
    expect(wrapper.classes()).toContain('nus-status--neutral');
    expect(wrapper.find('.nus-status__dot').attributes('aria-hidden')).toBe(
      'true',
    );
    expect(wrapper.text()).toBe('未提交');
  });

  it('强调变体加浅底类名；插槽可以替代 label', () => {
    const wrapper = mount(StatusTag, {
      props: { emphasis: true, tone: 'waiting' },
      slots: { default: '待批改 3 份' },
    });
    expect(wrapper.classes()).toEqual(
      expect.arrayContaining([
        'nus-status',
        'nus-status--waiting',
        'is-emphasis',
      ]),
    );
    expect(wrapper.text()).toBe('待批改 3 份');
  });
});
