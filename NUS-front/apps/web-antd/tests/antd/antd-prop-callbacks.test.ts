import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

/**
 * antd 4 里有些回调是 prop、不是事件（V1.0.1.9 A6）。用 `@after-close` 这类写法时，Vue 把它编译成 `onAfterClose`，
 * 组件不认这个 prop，回调落进 attrs 永远不会执行（V1.0.1.8 课程详情「添加班级」后列表不刷新就是这样）。
 * 这些回调要用 `:after-close="fn"` 传入。
 *
 * 核实过的 ant-design-vue 4.2.6 源码：
 * - Modal（modal/Modal.js、vc-dialog/Dialog.js）、Alert（alert/index.js）：afterClose 只按 props.afterClose 调用，不 emit；
 *   仓库里也没有自己 emit afterClose 的组件，所以 after-close 在任何组件上都按事件写都不对。
 * - Carousel（carousel/index.js → vc-slick）：beforeChange、afterChange 只按 prop 调用，不 emit。
 * - Drawer 的 afterOpenChange / afterVisibleChange 会 emit（drawer/index.js），Slider 的 afterChange 也会 emit，
 *   这几个按事件写是对的，不在检查之列。
 */

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '..', '..', '..', '..');
const SCAN_DIRS = ['apps/web-antd/src', 'packages'];
const SKIP_DIRS = new Set(['.turbo', 'dist', 'node_modules']);

/** 只能用 prop 传入的回调；tags 为空表示所有组件都适用 */
interface PropCallbackRule {
  callbacks: string[];
  tags?: string[];
}

const PROP_CALLBACK_RULES: PropCallbackRule[] = [
  { callbacks: ['after-close'] },
  {
    callbacks: ['after-change', 'before-change'],
    tags: ['Carousel', 'a-carousel', 'ACarousel'],
  },
];

function walk(dir: string, out: string[]) {
  for (const name of readdirSync(dir)) {
    if (SKIP_DIRS.has(name)) continue;
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      walk(path, out);
    } else if (/\.(?:tsx?|vue)$/.test(name)) {
      out.push(path);
    }
  }
  return out;
}

const camel = (name: string) =>
  name.replaceAll(/-([a-z])/g, (_, c: string) => c.toUpperCase());

/** 取出模板里的开始标签（属性值里的 `>` 不算结束） */
function openingTags(source: string) {
  const tags: { attrs: string; name: string; offset: number }[] = [];
  const re = /<([a-z][\w.-]*)/gi;
  for (;;) {
    const match = re.exec(source);
    if (!match) break;
    let quote = '';
    let end = match.index + match[0].length;
    for (; end < source.length; end++) {
      const ch = source[end];
      if (quote) {
        if (ch === quote) quote = '';
      } else if (ch === '"' || ch === "'") {
        quote = ch;
      } else if (ch === '>') {
        break;
      }
    }
    tags.push({
      attrs: source.slice(match.index + match[0].length, end),
      name: match[1] ?? '',
      offset: match.index,
    });
    re.lastIndex = end;
  }
  return tags;
}

const lineOf = (source: string, offset: number) =>
  source.slice(0, offset).split('\n').length;

/** 在 .vue 模板里找 `@after-close` / `v-on:afterClose` 这类写法 */
function findTemplateMisuses(source: string) {
  const template = source.replaceAll(
    /<(script|style)\b[\s\S]*?<\/\1>/g,
    (block) => block.replaceAll(/[^\n]/g, ' '),
  );
  const found: string[] = [];
  for (const tag of openingTags(template)) {
    for (const rule of PROP_CALLBACK_RULES) {
      if (rule.tags && !rule.tags.includes(tag.name)) continue;
      for (const callback of rule.callbacks) {
        const pattern = new RegExp(
          String.raw`(?:@|v-on:)(?:${callback}|${camel(callback)})(?=[\s=.>/]|$)`,
        );
        const hit = pattern.exec(tag.attrs);
        if (hit) {
          found.push(
            `第 ${lineOf(template, tag.offset)} 行 <${tag.name}> ${hit[0]}`,
          );
        }
      }
    }
  }
  return found;
}

/** 在 h()/JSX 里找 `onAfterClose` 这类写法（只查不限组件的回调） */
function findScriptMisuses(source: string) {
  const found: string[] = [];
  for (const rule of PROP_CALLBACK_RULES) {
    if (rule.tags) continue;
    for (const callback of rule.callbacks) {
      const key = `on${camel(callback).replace(/^./, (c) => c.toUpperCase())}`;
      const pattern = new RegExp(String.raw`\b${key}\s*[:=(]`, 'g');
      for (const match of source.matchAll(pattern)) {
        found.push(`第 ${lineOf(source, match.index)} 行 ${key}`);
      }
    }
  }
  return found;
}

describe('antd 4 里是 prop 的回调不能按事件写（V1.0.1.9 A6）', () => {
  const files = SCAN_DIRS.flatMap((dir) => walk(join(ROOT, dir), []));

  it('扫到了应用与 packages 里的组件', () => {
    const vueFiles = files.filter((file) => file.endsWith('.vue'));
    expect(vueFiles.length).toBeGreaterThan(300);
    expect(
      vueFiles.some((file) =>
        relative(ROOT, file).startsWith('apps/web-antd/src/views/'),
      ),
    ).toBe(true);
    expect(
      vueFiles.some((file) => relative(ROOT, file).startsWith('packages/')),
    ).toBe(true);
  });

  it('能认出各种写法', () => {
    expect(
      findTemplateMisuses(
        '<template><Modal v-model:open="a" @after-close="b" @ok="c" /></template>',
      ),
    ).toHaveLength(1);
    expect(
      findTemplateMisuses(
        '<template><a-modal\n  :x="() => 1 > 0"\n  v-on:afterClose="b"\n/></template>',
      ),
    ).toEqual(['第 1 行 <a-modal> v-on:afterClose']);
    expect(
      findTemplateMisuses(
        '<template><Alert @after-close.once="b" /></template>',
      ),
    ).toHaveLength(1);
    expect(
      findTemplateMisuses(
        '<template><Carousel @after-change="b" /></template>',
      ),
    ).toHaveLength(1);
    // 正确写法与按事件写本来就对的组件不报
    expect(
      findTemplateMisuses(
        '<template><Modal :after-close="b" /><Drawer @after-open-change="c" /><Slider @after-change="d" /></template>',
      ),
    ).toEqual([]);
    // script 里的字符串不当成模板
    expect(
      findTemplateMisuses(
        '<script setup lang="ts">const s = "<Modal @after-close=x>";</script><template><div /></template>',
      ),
    ).toEqual([]);
    expect(findScriptMisuses('h(Modal, { onAfterClose: () => {} })')).toEqual([
      '第 1 行 onAfterClose',
    ]);
    expect(findScriptMisuses('h(Modal, { afterClose: () => {} })')).toEqual([]);
  });

  it('组件上没有把 prop 回调当事件监听', () => {
    const self = fileURLToPath(import.meta.url);
    const misuses: string[] = [];
    for (const file of files) {
      if (file === self) continue;
      const source = readFileSync(file, 'utf8');
      const hits = file.endsWith('.vue')
        ? [...findTemplateMisuses(source), ...findScriptMisuses(source)]
        : findScriptMisuses(source);
      misuses.push(
        ...hits.map((hit) => `${relative(ROOT, file)} ${hit}（改用 :prop）`),
      );
    }
    expect(misuses).toEqual([]);
  });
});
