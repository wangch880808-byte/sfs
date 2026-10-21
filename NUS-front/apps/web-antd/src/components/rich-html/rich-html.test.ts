import { describe, expect, it } from 'vitest';

import {
  collectImageSources,
  isRichHtmlEmpty,
  prepareRichHtml,
  resolveImagePreview,
  TABLE_WRAP_CLASS,
  toCssSize,
} from './rich-html';

function render(html: string) {
  const root = document.createElement('div');
  root.innerHTML = html;
  return root;
}

function pick<T extends Element = HTMLElement>(
  root: ParentNode,
  selector: string,
): T {
  const element = root.querySelector<T>(selector);
  if (!element) throw new Error(`missing ${selector}`);
  return element;
}

describe('isRichHtmlEmpty', () => {
  it('空串、空白和编辑器留下的空段落都算空', () => {
    expect(isRichHtmlEmpty()).toBe(true);
    expect(isRichHtmlEmpty(null)).toBe(true);
    expect(isRichHtmlEmpty('')).toBe(true);
    expect(isRichHtmlEmpty('   ')).toBe(true);
    expect(isRichHtmlEmpty('<p><br></p>')).toBe(true);
    expect(isRichHtmlEmpty('<p>&nbsp;</p>')).toBe(true);
  });

  it('有文字、图片或表格时不算空', () => {
    expect(isRichHtmlEmpty('<p>完成实验报告</p>')).toBe(false);
    expect(isRichHtmlEmpty('<p><img src="/a.png"></p>')).toBe(false);
    expect(isRichHtmlEmpty('<table><tr><td></td></tr></table>')).toBe(false);
  });
});

describe('prepareRichHtml', () => {
  it('空内容返回空串', () => {
    expect(prepareRichHtml()).toBe('');
    expect(prepareRichHtml(null)).toBe('');
  });

  it('每个表格外包一层可滚动的 div', () => {
    const root = render(
      prepareRichHtml(
        '<p>说明</p><table><tr><td>1</td></tr></table><table><tr><td>2</td></tr></table>',
      ),
    );
    const wraps = root.querySelectorAll(`.${TABLE_WRAP_CLASS}`);
    expect(wraps).toHaveLength(2);
    for (const wrap of wraps) {
      expect(wrap.children).toHaveLength(1);
      expect(wrap.firstElementChild?.tagName).toBe('TABLE');
    }
    expect(root.firstElementChild?.tagName).toBe('P');
  });

  it('已经包过的表格不重复包，处理两次结果不变', () => {
    const once = prepareRichHtml('<table><tr><td>1</td></tr></table>');
    expect(prepareRichHtml(once)).toBe(once);
    expect(render(once).querySelectorAll(`.${TABLE_WRAP_CLASS}`)).toHaveLength(
      1,
    );
  });

  it('嵌套表格各自包一层', () => {
    const root = render(
      prepareRichHtml(
        '<table><tr><td><table><tr><td>内</td></tr></table></td></tr></table>',
      ),
    );
    expect(root.querySelectorAll(`.${TABLE_WRAP_CLASS}`)).toHaveLength(2);
  });

  it('去掉图片、视频行内写死的高度，保留宽度和其他样式', () => {
    const root = render(
      prepareRichHtml(
        '<img src="/a.png" width="1920" height="1080" style="width: 1920px; height: 1080px; border: 1px solid"><video src="/v.mp4" style="height: 600px"></video>',
      ),
    );
    const image = pick<HTMLImageElement>(root, 'img');
    expect(image.style.height).toBe('');
    expect(image.style.width).toBe('1920px');
    expect(image.getAttribute('style')).toContain('border');
    expect(image.getAttribute('height')).toBe('1080');
    expect(pick(root, 'video').hasAttribute('style')).toBe(false);
  });

  it('不改动文字内容', () => {
    expect(prepareRichHtml('<p>第一段</p><p>第二段</p>')).toBe(
      '<p>第一段</p><p>第二段</p>',
    );
  });
});

describe('collectImageSources / resolveImagePreview', () => {
  const html =
    '<p><img src="/1.png"><img src=""></p><a href="/x"><img src="/link.png"></a><p>文字<img src="/2.png"></p>';

  it('按出现顺序收集可预览图片，跳过无地址与链接里的图片', () => {
    expect(collectImageSources(render(html))).toEqual(['/1.png', '/2.png']);
  });

  it('点到图片时返回列表和位置', () => {
    const root = render(html);
    const images = root.querySelectorAll('img');
    expect(resolveImagePreview(root, images[3] ?? null)).toEqual({
      current: 1,
      items: ['/1.png', '/2.png'],
    });
    expect(resolveImagePreview(root, images[0] ?? null)?.current).toBe(0);
  });

  it('点到文字、链接里的图片或容器外的图片时不打开预览', () => {
    const root = render(html);
    expect(resolveImagePreview(root, pick(root, 'p'))).toBeNull();
    expect(resolveImagePreview(root, pick(root, 'a img'))).toBeNull();
    expect(resolveImagePreview(root, null)).toBeNull();
    const outside = document.createElement('img');
    outside.src = '/out.png';
    expect(resolveImagePreview(root, outside)).toBeNull();
  });
});

describe('toCssSize', () => {
  it('数字按像素，字符串原样，空值不设', () => {
    expect(toCssSize(320)).toBe('320px');
    expect(toCssSize('40vh')).toBe('40vh');
    expect(toCssSize()).toBeUndefined();
    expect(toCssSize('')).toBeUndefined();
  });
});
