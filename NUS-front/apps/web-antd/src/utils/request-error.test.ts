import { describe, expect, it } from 'vitest';

import { getRequestErrorReason } from './request-error';

describe('页面提示用的请求失败原因', () => {
  it('业务错误取后端给的原因，去掉句末标点', () => {
    expect(getRequestErrorReason({ code: 500, msg: '文件类型不支持。' })).toBe(
      '文件类型不支持',
    );
    expect(
      getRequestErrorReason({ response: { data: { msg: '没有权限' } } }),
    ).toBe('没有权限');
  });

  it('断网和超时给出中文说明，写入类请求提示结果未确认', () => {
    expect(
      getRequestErrorReason({
        config: { method: 'get' },
        message: 'Network Error',
      }),
    ).toBe('网络连接中断');
    expect(
      getRequestErrorReason({
        config: { method: 'get' },
        message: 'timeout of 30000ms exceeded',
      }),
    ).toBe('服务器响应超时');
    expect(
      getRequestErrorReason({
        config: { method: 'post' },
        message: 'timeout of 30000ms exceeded',
      }),
    ).toBe('网络中断或响应超时，结果没有确认');
  });

  it('中文的本地错误原样给出，英文技术信息和读不出的情况返回空串', () => {
    expect(
      getRequestErrorReason(
        new Error('服务器没有返回文件地址。稍后重新上传。'),
      ),
    ).toBe('服务器没有返回文件地址。稍后重新上传');
    expect(getRequestErrorReason(new Error('Request failed'))).toBe('');
    expect(getRequestErrorReason(undefined)).toBe('');
  });
});
