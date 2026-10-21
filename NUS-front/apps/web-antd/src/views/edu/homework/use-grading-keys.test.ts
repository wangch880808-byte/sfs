import { describe, expect, it } from 'vitest';

import { isTypingTarget, resolveGradingKey } from './use-grading-keys';

function keyEvent(
  key: string,
  target: EventTarget | null = document.body,
  mods: Partial<KeyboardEvent> = {},
) {
  return {
    altKey: false,
    ctrlKey: false,
    defaultPrevented: false,
    isComposing: false,
    key,
    metaKey: false,
    target,
    ...mods,
  } as KeyboardEvent;
}

describe('批改页快捷键', () => {
  it('j、↓ 下一份，K、↑ 上一份', () => {
    expect(resolveGradingKey(keyEvent('j'))).toBe('next');
    expect(resolveGradingKey(keyEvent('ArrowDown'))).toBe('next');
    expect(resolveGradingKey(keyEvent('K'))).toBe('prev');
    expect(resolveGradingKey(keyEvent('ArrowUp'))).toBe('prev');
    expect(resolveGradingKey(keyEvent('x'))).toBeUndefined();
  });

  it('输入时不切换，只认 Ctrl 或 Cmd + Enter', () => {
    const textarea = document.createElement('textarea');
    const input = document.createElement('input');
    expect(isTypingTarget(textarea)).toBe(true);
    expect(resolveGradingKey(keyEvent('j', textarea))).toBeUndefined();
    expect(resolveGradingKey(keyEvent('ArrowDown', input))).toBeUndefined();
    expect(
      resolveGradingKey(keyEvent('Enter', textarea, { ctrlKey: true })),
    ).toBe('save-next');
    expect(
      resolveGradingKey(keyEvent('Enter', textarea, { metaKey: true })),
    ).toBe('save-next');
    expect(resolveGradingKey(keyEvent('Enter', textarea))).toBeUndefined();
  });

  it('数字框、下拉里的方向键交给控件；弹窗里一律不处理', () => {
    const numberBox = document.createElement('div');
    numberBox.className = 'ant-input-number';
    const inner = document.createElement('span');
    numberBox.append(inner);
    expect(resolveGradingKey(keyEvent('ArrowUp', inner))).toBeUndefined();

    const dialog = document.createElement('div');
    dialog.setAttribute('role', 'dialog');
    const button = document.createElement('button');
    dialog.append(button);
    expect(
      resolveGradingKey(keyEvent('Enter', button, { ctrlKey: true })),
    ).toBeUndefined();
    expect(resolveGradingKey(keyEvent('j', button))).toBeUndefined();
  });

  it('输入法组字、带 Alt、已被处理的按键不响应', () => {
    expect(
      resolveGradingKey(keyEvent('j', null, { isComposing: true })),
    ).toBeUndefined();
    expect(
      resolveGradingKey(keyEvent('j', null, { altKey: true })),
    ).toBeUndefined();
    expect(
      resolveGradingKey(keyEvent('j', null, { defaultPrevented: true })),
    ).toBeUndefined();
  });
});
