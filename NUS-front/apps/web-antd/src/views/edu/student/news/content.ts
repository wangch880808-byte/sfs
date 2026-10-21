/**
 * 抓取来的资讯正文常带写死的颜色、字号与字体（行内样式或 <font>），深色下会看不清、字号也和页面不一致：
 * 去掉这些写死的排版，交给详情页统一的正文样式；其余内容原样保留，安全过滤仍由 v-dompurify-html 负责。
 */
const STRIPPED_STYLE_PROPS = [
  'background',
  'background-color',
  'color',
  'font',
  'font-family',
  'font-size',
  'line-height',
];

/** 图片的行内宽高会和“最大宽度 100%”冲突，把图拉变形 */
const STRIPPED_IMAGE_STYLE_PROPS = ['height', 'width'];

const STRIPPED_FONT_ATTRS = ['color', 'face', 'size'];

export function normalizeNewsHtml(html?: null | string): string {
  if (!html) return '';
  const doc = new DOMParser().parseFromString(html, 'text/html');
  for (const element of doc.body.querySelectorAll<HTMLElement>('[style]')) {
    const props =
      element.tagName === 'IMG'
        ? [...STRIPPED_STYLE_PROPS, ...STRIPPED_IMAGE_STYLE_PROPS]
        : STRIPPED_STYLE_PROPS;
    for (const prop of props) {
      element.style.removeProperty(prop);
    }
    if (!element.getAttribute('style')?.trim()) {
      element.removeAttribute('style');
    }
  }
  for (const element of doc.body.querySelectorAll('font')) {
    for (const attr of STRIPPED_FONT_ATTRS) {
      element.removeAttribute(attr);
    }
  }
  for (const element of doc.body.querySelectorAll('[bgcolor]')) {
    element.removeAttribute('bgcolor');
  }
  return doc.body.innerHTML;
}
