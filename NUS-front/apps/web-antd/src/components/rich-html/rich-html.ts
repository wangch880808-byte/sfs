/**
 * 富文本显示的纯函数：判断内容是否为空、渲染前整理 HTML、点击图片时取预览列表。
 * 安全过滤仍由 v-dompurify-html 负责，这里只做排版上的整理。
 */

export const TABLE_WRAP_CLASS = 'rich-html__table-wrap';

/** 没有文字也算有内容的元素（图片、表格、视频等） */
const MEANINGFUL_SELECTOR =
  'img, video, audio, iframe, embed, object, table, hr, svg, canvas';

/** 行内高度会和“最大宽度 100%、高度自适应”冲突，把大图拉变形 */
const STRIPPED_MEDIA_STYLE_PROPS = ['height', 'max-height', 'min-height'];

function parseBody(html: string) {
  return new DOMParser().parseFromString(html, 'text/html').body;
}

/** 内容为空：空串、只有空白，或编辑器留下的 <p><br></p> 这类空段落 */
export function isRichHtmlEmpty(html?: null | string): boolean {
  if (!html || !html.trim()) return true;
  const body = parseBody(html);
  if (body.querySelector(MEANINGFUL_SELECTOR)) return false;
  // trim 同时去掉不间断空格（编辑器常用 &nbsp; 占位）
  return !body.textContent?.trim();
}

/**
 * 渲染前整理：
 * - 每个表格外包一层可横向滚动的 div；已经包过的不重复包；
 * - 去掉图片、视频行内样式里写死的高度，交给样式按比例缩放（保留宽度，由最大宽度兜底；
 *   height 属性的优先级低于样式，保留它作为宽高比提示）。
 */
export function prepareRichHtml(html?: null | string): string {
  if (!html) return '';
  const body = parseBody(html);
  for (const table of body.querySelectorAll('table')) {
    const parent = table.parentElement;
    if (
      parent?.classList.contains(TABLE_WRAP_CLASS) &&
      parent.children.length === 1
    ) {
      continue;
    }
    const wrap = body.ownerDocument.createElement('div');
    wrap.className = TABLE_WRAP_CLASS;
    table.replaceWith(wrap);
    wrap.append(table);
  }
  for (const media of body.querySelectorAll<HTMLElement>('img, video')) {
    if (!media.hasAttribute('style')) continue;
    for (const prop of STRIPPED_MEDIA_STYLE_PROPS) {
      media.style.removeProperty(prop);
    }
    if (!media.getAttribute('style')?.trim()) {
      media.removeAttribute('style');
    }
  }
  return body.innerHTML;
}

function imageSource(image: HTMLImageElement) {
  return (image.getAttribute('src') || '').trim();
}

/** 可预览的图片：有地址、不在链接里（链接里的图片按链接处理） */
function previewableImages(root: ParentNode) {
  return [...root.querySelectorAll('img')].filter(
    (image) => imageSource(image) && !image.closest('a'),
  );
}

/** 容器内全部可预览图片的地址，按出现顺序 */
export function collectImageSources(root: ParentNode): string[] {
  return previewableImages(root).map((image) => imageSource(image));
}

export interface ImagePreviewTarget {
  /** 被点的图片在列表里的位置 */
  current: number;
  items: string[];
}

/**
 * 点击事件落在容器内的图片上时，返回预览列表与当前位置；否则返回 null。
 * 放在链接里的图片按链接处理，不打开预览。
 */
export function resolveImagePreview(
  root: Element,
  target: EventTarget | null,
): ImagePreviewTarget | null {
  if (!(target instanceof Element)) return null;
  const image = target.closest('img');
  if (!image || !root.contains(image)) return null;
  const images = previewableImages(root);
  const current = images.indexOf(image);
  if (current === -1) return null;
  return { current, items: images.map((image) => imageSource(image)) };
}

/** maxHeight 为数字时按像素处理 */
export function toCssSize(value?: number | string): string | undefined {
  if (value === undefined || value === null || value === '') return undefined;
  return typeof value === 'number' ? `${value}px` : value;
}
