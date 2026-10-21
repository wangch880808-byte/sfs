import { flushPromises, mount } from '@vue/test-utils';
import VueDOMPurifyHTML from 'vue-dompurify-html';

import { ImagePreviewGroup } from 'ant-design-vue';
import { afterEach, describe, expect, it } from 'vitest';

import { RichHtml } from '.';

function mountRichHtml(props: Record<string, unknown>, slots = {}) {
  return mount(RichHtml, {
    attachTo: document.body,
    global: { plugins: [VueDOMPurifyHTML] },
    props,
    slots,
  });
}

afterEach(() => {
  document.body.innerHTML = '';
});

describe('richHtml', () => {
  it('渲染富文本并过滤脚本，表格包上可滚动的外层', () => {
    const wrapper = mountRichHtml({
      html: '<p>完成实验</p><script>window.x = 1</script><table><tr><td>1</td></tr></table>',
    });
    const body = wrapper.find('.rich-html__body');
    expect(body.find('p').text()).toBe('完成实验');
    expect(body.find('script').exists()).toBe(false);
    expect(body.find('.rich-html__table-wrap > table').exists()).toBe(true);
    wrapper.unmount();
  });

  it('内容为空时显示 emptyText，可用 empty 插槽替换', () => {
    const plain = mountRichHtml({ html: '<p><br></p>' });
    expect(plain.find('.rich-html__empty').text()).toBe('暂无内容');
    expect(plain.find('.rich-html__body').exists()).toBe(false);
    plain.unmount();

    const custom = mountRichHtml(
      { emptyText: '还没有填写', html: '' },
      { empty: '<em class="custom-empty">自定义</em>' },
    );
    expect(custom.find('.custom-empty').text()).toBe('自定义');
    expect(custom.find('.rich-html__empty').exists()).toBe(false);
    custom.unmount();
  });

  it('maxHeight 限高后内部滚动', () => {
    const wrapper = mountRichHtml({ html: '<p>内容</p>', maxHeight: 320 });
    const style = wrapper.find('.rich-html').attributes('style');
    expect(style).toContain('max-height: 320px');
    expect(style).toContain('overflow: auto');
    wrapper.unmount();
  });

  it('点击图片打开预览组，定位到被点的那张', async () => {
    const wrapper = mountRichHtml({
      html: '<p><img src="/1.png"></p><p>说明<img src="/2.png"></p>',
    });
    expect(wrapper.findComponent(ImagePreviewGroup).exists()).toBe(false);

    await wrapper.findAll('.rich-html__body img')[1]?.trigger('click');
    await flushPromises();

    const group = wrapper.findComponent(ImagePreviewGroup);
    expect(group.exists()).toBe(true);
    expect(group.props('preview')).toMatchObject({ current: 1, visible: true });
    expect(
      wrapper
        .findAll('.rich-html__preview img')
        .map((image) => image.attributes('src')),
    ).toEqual(['/1.png', '/2.png']);
    expect(
      document.body.querySelector('.ant-image-preview-wrap'),
    ).not.toBeNull();
    expect(
      document.body
        .querySelector('.ant-image-preview-img')
        ?.getAttribute('src'),
    ).toBe('/2.png');

    // 预览关闭时同步回组件状态
    const preview = group.props('preview') as {
      onVisibleChange: (visible: boolean) => void;
    };
    preview.onVisibleChange(false);
    await flushPromises();
    expect(
      wrapper.findComponent(ImagePreviewGroup).props('preview'),
    ).toMatchObject({ visible: false });
    wrapper.unmount();
  });

  it('点文字不打开预览', async () => {
    const wrapper = mountRichHtml({ html: '<p>文字<img src="/1.png"></p>' });
    await wrapper.find('.rich-html__body p').trigger('click');
    await flushPromises();
    expect(wrapper.findComponent(ImagePreviewGroup).exists()).toBe(false);
    wrapper.unmount();
  });
});
