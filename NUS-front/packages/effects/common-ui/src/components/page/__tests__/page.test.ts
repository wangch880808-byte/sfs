import { mount } from '@vue/test-utils';

import { describe, expect, it } from 'vitest';

import { Page } from '..';

describe('page.vue', () => {
  it('renders title when passed', () => {
    const wrapper = mount(Page, {
      props: {
        title: 'Test Title',
      },
    });

    expect(wrapper.text()).toContain('Test Title');
  });

  it('renders description when passed', () => {
    const wrapper = mount(Page, {
      props: {
        description: 'Test Description',
      },
    });

    expect(wrapper.text()).toContain('Test Description');
  });

  it('renders default slot content', () => {
    const wrapper = mount(Page, {
      slots: {
        default: '<p>Default Slot Content</p>',
      },
    });

    expect(wrapper.html()).toContain('<p>Default Slot Content</p>');
  });

  it('renders footer slot when showFooter is true', () => {
    const wrapper = mount(Page, {
      props: {
        showFooter: true,
      },
      slots: {
        footer: '<p>Footer Slot Content</p>',
      },
    });

    expect(wrapper.html()).toContain('<p>Footer Slot Content</p>');
  });

  it('applies the custom contentClass', () => {
    const wrapper = mount(Page, {
      props: {
        contentClass: 'custom-class',
      },
    });

    const contentDiv = wrapper.find('.vben-page__content');
    expect(contentDiv.classes()).toContain('custom-class');
  });

  it('renders the header on the canvas with title, description and actions', () => {
    const wrapper = mount(Page, {
      props: {
        description: '按班级查看提交与批改进度',
        title: '实训任务',
      },
      slots: {
        extra: '<button>新建任务</button>',
      },
    });

    const header = wrapper.find('.vben-page__header');
    expect(header.exists()).toBe(true);
    // 页头不套卡片
    expect(header.classes()).not.toContain('surface-panel');
    expect(header.classes()).not.toContain('surface-panel-soft');
    expect(wrapper.find('h1.vben-page__title').text()).toBe('实训任务');
    expect(wrapper.find('.vben-page__description').text()).toBe(
      '按班级查看提交与批改进度',
    );
    expect(header.text()).toContain('新建任务');
  });

  it('omits the header when there is no title, description or actions', () => {
    const wrapper = mount(Page, {
      slots: { default: '<p>内容</p>' },
    });
    expect(wrapper.find('.vben-page__header').exists()).toBe(false);
  });

  it('does not render title slot if title prop is provided', () => {
    const wrapper = mount(Page, {
      props: {
        title: 'Test Title',
      },
      slots: {
        title: '<p>Title Slot Content</p>',
      },
    });

    expect(wrapper.text()).toContain('Title Slot Content');
    expect(wrapper.html()).not.toContain('Test Title');
  });

  it('does not render description slot if description prop is provided', () => {
    const wrapper = mount(Page, {
      props: {
        description: 'Test Description',
      },
      slots: {
        description: '<p>Description Slot Content</p>',
      },
    });

    expect(wrapper.text()).toContain('Description Slot Content');
    expect(wrapper.html()).not.toContain('Test Description');
  });
});
