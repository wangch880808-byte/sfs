<script setup lang="ts">
import type { MarkdownViewProps } from './typing';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import { useClipboard } from '@vueuse/core';
import { message } from 'ant-design-vue';
import MarkdownIt from 'markdown-it';

import {
  decodeCopyPayload,
  encodeCopyPayload,
  escapeHtml,
  highlightCode,
} from '#/utils/highlight';

// 定义组件属性
const props = defineProps<MarkdownViewProps>();

const { copy } = useClipboard(); // 初始化 copy 到粘贴板
const contentRef = ref<HTMLElement | null>(null);
const handleContentClick = async (event: Event) => {
  const target = event.target as HTMLElement | null;
  if (target?.id !== 'copy') {
    return;
  }
  await copy(decodeCopyPayload(String(target.dataset.copy || '')));
  message.success('已复制代码');
};

const md = new MarkdownIt({
  highlight(str, lang) {
    try {
      const copyHtml = `<div id="copy" class="markdown-view__copy" data-copy="${escapeHtml(encodeCopyPayload(str))}">复制</div>`;
      const highlightedCode = highlightCode(str, lang);
      return `<pre>${copyHtml}<code class="hljs">${highlightedCode}</code></pre>`;
    } catch {
      return `<pre><code class="hljs">${escapeHtml(str)}</code></pre>`;
    }
  },
});

function normalizeLeadingFullWidthSpaces(line: string) {
  return line.replace(/^\u3000+/, (spaces) => ' '.repeat(spaces.length));
}

function normalizeMarkdown(raw: string) {
  const lines = String(raw || '')
    .replaceAll(/\r\n?/g, '\n')
    .split('\n');

  let fenceMarker = '';

  return lines
    .map((sourceLine) => {
      let line = normalizeLeadingFullWidthSpaces(sourceLine);
      const fenceMatch = line.match(/^\s{0,3}(`{3,}|~{3,})/);
      if (fenceMatch) {
        const marker = fenceMatch[1]?.slice(0, 3) || '';
        if (!fenceMarker) {
          fenceMarker = marker;
        } else if (fenceMarker === marker) {
          fenceMarker = '';
        }
        return line;
      }
      if (fenceMarker) {
        return line;
      }

      line = line.replace(/^(\s{0,3}#{1,6})([^\s#].*)$/, '$1 $2');
      line = line.replace(/^(\s{0,3}>)([^\s>].*)$/, '$1 $2');
      line = line.replace(
        /^(\s{0,3})([-*+•·])(.*)$/,
        (_, indent, marker, content) => {
          const normalizedMarker =
            marker === '•' || marker === '·' ? '-' : marker;
          const normalizedContent = String(content || '').trimStart();
          return normalizedContent
            ? `${indent}${normalizedMarker} ${normalizedContent}`
            : `${indent}${normalizedMarker}`;
        },
      );
      line = line.replace(
        /^(\s{0,3})(\d+)([.、．])(.*)$/,
        (_, indent, order, _separator, content) => {
          const normalizedContent = String(content || '').trimStart();
          return normalizedContent
            ? `${indent}${order}. ${normalizedContent}`
            : `${indent}${order}.`;
        },
      );

      return line;
    })
    .join('\n');
}

/** 渲染 markdown */
const renderedMarkdown = computed(() => {
  return md.render(normalizeMarkdown(props.content));
});

/** 初始化 */
onMounted(() => {
  contentRef.value?.addEventListener('click', handleContentClick);
});

onBeforeUnmount(() => {
  contentRef.value?.removeEventListener('click', handleContentClick);
});
</script>

<template>
  <div ref="contentRef" class="markdown-view" v-html="renderedMarkdown"></div>
</template>

<style lang="scss">
/*
 * Markdown 内容（V1.0.1.6 界面规范）：正文 14、标题不超过区块标题 15，颜色全部取 token，深浅色都可读；
 * 代码块用凹陷底与等宽字体，语法着色只用主色、状态色与辅助文字色（不再引入 highlight.js 的深色主题）。
 */
.markdown-view {
  max-width: 100%;
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-regular);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  overflow-wrap: anywhere;

  > :first-child {
    margin-top: 0;
  }

  > :last-child {
    margin-bottom: 0;
  }

  p {
    margin: 0 0 8px;
  }

  h1,
  h2,
  h3,
  h4,
  h5,
  h6 {
    margin: 16px 0 8px;
    font-weight: var(--nus-fw-semibold);
    color: var(--nus-text);
  }

  h1,
  h2 {
    font-size: var(--nus-fs-heading);
    line-height: var(--nus-lh-heading);
  }

  h3,
  h4,
  h5,
  h6 {
    font-size: var(--nus-fs-body);
    line-height: var(--nus-lh-body);
  }

  ul,
  ol {
    padding-left: 20px;
    margin: 0 0 8px;
  }

  ul {
    list-style: disc;
  }

  ol {
    list-style: decimal;
  }

  ul ul,
  ol ul {
    list-style: circle;
  }

  ul ul ul,
  ol ol,
  ul ol {
    list-style: square;
  }

  li {
    margin: 4px 0;
  }

  li > p {
    margin: 0;
  }

  a {
    color: var(--nus-primary);
    text-decoration: none;
    transition: var(--nus-transition-colors);

    &:hover {
      color: var(--nus-primary-hover);
      text-decoration: underline;
    }
  }

  strong {
    font-weight: var(--nus-fw-semibold);
  }

  blockquote {
    padding: 0 12px;
    margin: 0 0 8px;
    color: var(--nus-text-secondary);
    border-left: 2px solid var(--nus-border);
  }

  hr {
    margin: 16px 0;
    border: 0;
    border-top: 1px solid var(--nus-border-subtle);
  }

  table {
    display: block;
    max-width: 100%;
    margin: 0 0 8px;
    overflow-x: auto;
    font-size: var(--nus-fs-dense);
    line-height: var(--nus-lh-dense);
    border-collapse: collapse;
  }

  th,
  td {
    padding: 6px 12px;
    text-align: left;
    border: 1px solid var(--nus-border-subtle);
  }

  th {
    font-weight: var(--nus-fw-medium);
    color: var(--nus-text-secondary);
    background: var(--nus-bg-sunken);
  }

  /* 行内代码 */
  :not(pre) > code {
    padding: 1px 4px;
    font-family: var(--nus-font-mono);
    font-size: var(--nus-fs-dense);
    background: var(--nus-bg-sunken);
    border: 1px solid var(--nus-border-subtle);
    border-radius: var(--nus-radius-sm);
  }

  /* 代码块：凹陷底、等宽字体，横向滚动不撑开外层 */
  pre {
    position: relative;
    max-width: 100%;
    margin: 0 0 8px;
    overflow: hidden;
    background: var(--nus-bg-sunken);
    border: 1px solid var(--nus-border-subtle);
    border-radius: var(--nus-radius);
  }

  pre code.hljs {
    display: block;
    padding: 28px 12px 12px;
    overflow-x: auto;
    font-family: var(--nus-font-mono);
    font-size: var(--nus-fs-dense);
    line-height: var(--nus-lh-dense);
    color: var(--nus-text);
    white-space: pre;
    background: transparent;
  }

  .markdown-view__copy {
    position: absolute;
    top: 4px;
    right: 8px;
    padding: 0 6px;
    font-size: var(--nus-fs-caption);
    line-height: var(--nus-lh-caption);
    color: var(--nus-text-tertiary);
    cursor: pointer;
    border-radius: var(--nus-radius-sm);
    transition: var(--nus-transition-colors);

    &:hover {
      color: var(--nus-text);
      background: var(--nus-bg-hover);
    }
  }

  /* 语法着色（token 取色，深浅色各自过 AA） */
  .hljs-comment,
  .hljs-quote {
    font-style: italic;
    color: var(--nus-text-tertiary);
  }

  .hljs-keyword,
  .hljs-selector-tag,
  .hljs-literal,
  [class~='hljs-built_in'],
  .hljs-type,
  .hljs-meta .hljs-keyword {
    color: var(--nus-primary);
  }

  .hljs-string,
  .hljs-regexp,
  .hljs-addition,
  .hljs-template-tag,
  .hljs-template-variable {
    color: var(--nus-success);
  }

  .hljs-number,
  .hljs-symbol,
  .hljs-bullet,
  .hljs-variable,
  .hljs-attr,
  .hljs-attribute {
    color: var(--nus-warning);
  }

  .hljs-deletion {
    color: var(--nus-danger);
  }

  .hljs-title,
  .hljs-section,
  .hljs-name,
  .hljs-selector-id,
  .hljs-selector-class {
    font-weight: var(--nus-fw-medium);
    color: var(--nus-text);
  }

  .hljs-meta,
  .hljs-params,
  .hljs-punctuation {
    color: var(--nus-text-secondary);
  }

  .hljs-emphasis {
    font-style: italic;
  }

  .hljs-strong {
    font-weight: var(--nus-fw-semibold);
  }
}
</style>
