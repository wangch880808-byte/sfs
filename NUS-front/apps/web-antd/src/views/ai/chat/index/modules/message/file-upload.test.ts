import { flushPromises, mount } from '@vue/test-utils';

import { message } from 'ant-design-vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import FileUpload from './file-upload.vue';

const httpRequest = vi.fn();

vi.mock('#/components/upload/use-upload', () => ({
  useUpload: () => ({ httpRequest }),
}));

// 图标不联网取图
vi.mock('@vben/icons', () => ({ IconifyIcon: { render: () => null } }));

function mountUpload() {
  return mount(FileUpload);
}

async function selectFile(wrapper: ReturnType<typeof mountUpload>) {
  const input = wrapper.find('input[type="file"]');
  Object.defineProperty(input.element, 'files', {
    configurable: true,
    value: [new File(['a'], 'a.txt', { type: 'text/plain' })],
  });
  await input.trigger('change');
}

describe('aI 对话附件上传的模拟进度', () => {
  beforeEach(() => {
    // 只接管进度用的 setInterval，flushPromises 仍用真实计时器
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    httpRequest.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('上传失败时停掉进度计时器，只提示一次', async () => {
    const error = vi.spyOn(message, 'error').mockReturnValue(undefined as any);
    httpRequest.mockRejectedValue({
      data: { code: 500, msg: '存储服务不可用' },
    });
    const wrapper = mountUpload();
    await selectFile(wrapper);
    await flushPromises();
    expect(vi.getTimerCount()).toBe(0);
    expect(error).toHaveBeenCalledTimes(1);
    expect(wrapper.emitted('uploadError')).toHaveLength(1);
    wrapper.unmount();
  });

  it('没拿到文件地址也算失败，同样停掉计时器', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    vi.spyOn(message, 'error').mockReturnValue(undefined as any);
    httpRequest.mockResolvedValue(undefined);
    const wrapper = mountUpload();
    await selectFile(wrapper);
    await flushPromises();
    expect(vi.getTimerCount()).toBe(0);
    expect(wrapper.emitted('uploadError')).toHaveLength(1);
    wrapper.unmount();
  });

  it('上传成功时停掉计时器并回传地址', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    httpRequest.mockResolvedValue('https://files.example/a.txt');
    const wrapper = mountUpload();
    await selectFile(wrapper);
    await flushPromises();
    expect(vi.getTimerCount()).toBe(0);
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      ['https://files.example/a.txt'],
    ]);
    wrapper.unmount();
  });

  it('上传还没结束就离开页面时，卸载组件停掉计时器', async () => {
    httpRequest.mockReturnValue(new Promise(() => {}));
    const wrapper = mountUpload();
    await selectFile(wrapper);
    expect(vi.getTimerCount()).toBe(1);
    wrapper.unmount();
    expect(vi.getTimerCount()).toBe(0);
  });
});
