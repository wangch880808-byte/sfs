import { mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';

import { describe, expect, it } from 'vitest';

import { DashboardPanel, DashboardRow, MetricTile } from '.';

const Empty = { render: () => null };

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ component: Empty, path: '/:path(.*)*' }],
  });
}

const global = () => ({ plugins: [createTestRouter()] });

describe('看板面板', () => {
  it('第一次读取时写明在读什么，不显示内容与数量', () => {
    const wrapper = mount(DashboardPanel, {
      global: global(),
      props: {
        count: '2 份',
        loading: true,
        loadingText: '正在读取待批改的提交',
        title: '待批改',
      },
      slots: { default: '<p class="body">内容</p>' },
    });
    expect(wrapper.find('[role="status"]').text()).toContain(
      '正在读取待批改的提交',
    );
    expect(wrapper.find('.body').exists()).toBe(false);
    expect(wrapper.find('.dash-panel__count').exists()).toBe(false);
  });

  it('没读到时只让这一块降级：说明原因，点重试通知父组件', async () => {
    const wrapper = mount(DashboardPanel, {
      global: global(),
      props: {
        error: true,
        errorTitle: '待批改的提交没有读到',
        title: '待批改',
      },
    });
    const alert = wrapper.find('[role="alert"]');
    expect(alert.text()).toContain('待批改的提交没有读到');
    expect(alert.text()).toContain('稍后重试');
    await alert.find('button').trigger('click');
    expect(wrapper.emitted('retry')).toHaveLength(1);
  });

  it('没有数据时显示空状态插槽；有数据时显示内容、数量与查看全部', () => {
    const empty = mount(DashboardPanel, {
      global: global(),
      props: { empty: true, title: '待批改' },
      slots: { empty: '<p class="empty">没有待批改的提交</p>' },
    });
    expect(empty.find('.empty').exists()).toBe(true);

    const full = mount(DashboardPanel, {
      global: global(),
      props: {
        count: '2 份',
        moreText: '全部任务',
        moreTo: '/capacity/homework_manager/homework',
        title: '待批改',
      },
      slots: { default: '<p class="body">内容</p>' },
    });
    expect(full.find('h2').text()).toBe('待批改');
    expect(full.find('.dash-panel__count').text()).toBe('2 份');
    expect(full.find('.body').exists()).toBe(true);
    const more = full.find('a.dash-panel__more');
    expect(more.text()).toBe('全部任务');
    expect(more.attributes('href')).toBe('/capacity/homework_manager/homework');
  });
});

describe('看板列表行', () => {
  it('有地址时整行是链接，带动作提示', () => {
    const wrapper = mount(DashboardRow, {
      global: global(),
      props: {
        action: '批改',
        to: '/capacity/homework_manager/homework/correct/1',
      },
      slots: { default: '页面结构与语义化标签', meta: 'Web 前端开发实训' },
    });
    expect(wrapper.element.tagName).toBe('A');
    expect(wrapper.classes()).toContain('is-interactive');
    expect(wrapper.find('.dash-row__title').text()).toBe(
      '页面结构与语义化标签',
    );
    expect(wrapper.find('.dash-row__meta').text()).toBe('Web 前端开发实训');
    expect(wrapper.find('.dash-row__action').text()).toBe('批改');
  });

  it('只能点时是按钮，点击通知父组件；都不是时是普通行', async () => {
    const button = mount(DashboardRow, {
      global: global(),
      props: { clickable: true },
      slots: { default: '作业批改完成' },
    });
    expect(button.element.tagName).toBe('BUTTON');
    expect(button.attributes('type')).toBe('button');
    await button.trigger('click');
    expect(button.emitted('click')).toHaveLength(1);

    const plain = mount(DashboardRow, {
      global: global(),
      slots: { default: '已读的消息' },
    });
    expect(plain.element.tagName).toBe('DIV');
    expect(plain.classes()).not.toContain('is-interactive');
    expect(plain.find('.dash-row__action').exists()).toBe(false);
  });
});

describe('指标', () => {
  it('还没读到数值时显示占位符，读到后显示数值与单位', () => {
    const pending = mount(MetricTile, {
      global: global(),
      props: { label: '待批改', unit: '份' },
    });
    expect(pending.find('.metric-tile__placeholder').text()).toBe('—');
    expect(pending.find('.metric-tile__unit').exists()).toBe(false);

    const ready = mount(MetricTile, {
      global: global(),
      props: {
        label: '待批改',
        meta: '最早一份已等 2 天',
        unit: '份',
        value: 0,
      },
    });
    expect(ready.find('.metric-card__value').text()).toBe('0份');
    expect(ready.find('.metric-card__meta').text()).toBe('最早一份已等 2 天');
    expect(ready.element.tagName).toBe('DIV');
  });

  it('给了地址时整块是链接', () => {
    const wrapper = mount(MetricTile, {
      global: global(),
      props: { label: '未读留言', to: '/edu/message', unit: '条', value: 2 },
    });
    expect(wrapper.element.tagName).toBe('A');
    expect(wrapper.attributes('href')).toBe('/edu/message');
    expect(wrapper.classes()).toContain('is-link');
  });
});
