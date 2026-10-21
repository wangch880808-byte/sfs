import { mount } from '@vue/test-utils';

import { describe, expect, it } from 'vitest';

import GridEmpty from './grid-empty.vue';

describe('列表空状态', () => {
  it('还没有数据：标题写“还没有……”，说明写下一步', () => {
    const wrapper = mount(GridEmpty, {
      props: { hint: '新建角色后，可以分配权限。', subject: '角色' },
    });
    expect(wrapper.classes()).toContain('nus-empty--empty');
    expect(wrapper.find('.nus-empty__title').text()).toBe('还没有角色');
    expect(wrapper.find('.nus-empty__description').text()).toBe(
      '新建角色后，可以分配权限。',
    );
  });

  it('筛选没有结果：换成筛选的写法', () => {
    const wrapper = mount(GridEmpty, {
      props: {
        filtered: true,
        hint: '新建角色后，可以分配权限。',
        subject: '角色',
      },
    });
    expect(wrapper.classes()).toContain('nus-empty--filtered');
    expect(wrapper.find('.nus-empty__title').text()).toBe('没有符合条件的角色');
    expect(wrapper.find('.nus-empty__description').text()).toContain('重置');
  });

  it('可以单独指定没有数据时的标题', () => {
    const wrapper = mount(GridEmpty, {
      props: { emptyTitle: '没有在线令牌', subject: '令牌' },
    });
    expect(wrapper.find('.nus-empty__title').text()).toBe('没有在线令牌');
    expect(wrapper.find('.nus-empty__description').exists()).toBe(false);
  });
});
