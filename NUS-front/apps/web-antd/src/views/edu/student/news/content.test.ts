import { describe, expect, it } from 'vitest';

import { normalizeNewsHtml } from './content';

function parse(html: string) {
  const wrapper = document.createElement('div');
  wrapper.innerHTML = normalizeNewsHtml(html);
  return wrapper;
}

function pick<T extends Element = HTMLElement>(
  wrapper: HTMLElement,
  selector: string,
): T {
  const element = wrapper.querySelector<T>(selector);
  if (!element) throw new Error(`missing ${selector}`);
  return element;
}

describe('normalizeNewsHtml', () => {
  it('keeps empty content empty', () => {
    expect(normalizeNewsHtml()).toBe('');
    expect(normalizeNewsHtml(null)).toBe('');
    expect(normalizeNewsHtml('')).toBe('');
  });

  it('drops hard-coded colors, fonts and sizes but keeps other styles', () => {
    const wrapper = parse(
      '<p style="color: black; font-size: 16px; font-family: SimSun; text-indent: 2em; text-align: center">正文</p>',
    );
    const paragraph = pick(wrapper, 'p');
    expect(paragraph.textContent).toBe('正文');
    expect(paragraph.style.color).toBe('');
    expect(paragraph.style.fontSize).toBe('');
    expect(paragraph.style.fontFamily).toBe('');
    expect(paragraph.style.textIndent).toBe('2em');
    expect(paragraph.style.textAlign).toBe('center');
  });

  it('removes the style attribute when nothing is left', () => {
    const wrapper = parse(
      '<span style="background-color: white; line-height: 2">文字</span>',
    );
    expect(pick(wrapper, 'span').hasAttribute('style')).toBe(false);
  });

  it('cleans legacy font tags and background attributes', () => {
    const wrapper = parse(
      '<table bgcolor="white"><tr><td><font color="red" size="5" face="SimSun">要闻</font></td></tr></table>',
    );
    const font = pick(wrapper, 'font');
    expect(font.textContent).toBe('要闻');
    expect(font.hasAttribute('color')).toBe(false);
    expect(font.hasAttribute('size')).toBe(false);
    expect(font.hasAttribute('face')).toBe(false);
    expect(pick(wrapper, 'table').hasAttribute('bgcolor')).toBe(false);
  });

  it('lets images scale with the column', () => {
    const wrapper = parse(
      '<img src="/a.png" alt="配图" style="width: 900px; height: 600px">',
    );
    const image = pick(wrapper, 'img');
    expect(image.getAttribute('src')).toBe('/a.png');
    expect(image.getAttribute('alt')).toBe('配图');
    expect(image.hasAttribute('style')).toBe(false);
  });
});
