#!/usr/bin/env node
/**
 * 样式指标（V1.0.1.6 界面规范，验收第 2～4、7 条）：统计页面与组件里写死的字号、颜色、圆角、渐变、阴影、!important，
 * 以及自写的状态标签函数和彩色标签。只允许下降：P7 接入单测与基线；改页面时可以先用它自查。
 *
 * 用法：node apps/web-antd/scripts/style-metrics.mjs [文件或目录 ...] [--json] [--detail]
 *       不给路径时扫 apps/web-antd/src/views 与 apps/web-antd/src/components。
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const APP = resolve(dirname(fileURLToPath(import.meta.url)), '..');

/** 规范允许的圆角取值（4 标签、6 按钮输入框、8 卡片表格、12 对话框抽屉，0 与圆形） */
const ALLOWED_RADIUS =
  /^(?:0|0px|4px|6px|8px|12px|50%|999px|9999px|inherit|var\(--[\w-]+\))$/;

export const RULES = {
  /** 写死的 px 字号（CSS 与内联样式） */
  fontSizePx:
    /font-size\s*:\s*\d+(?:\.\d+)?px|fontSize\s*:\s*['"`]?\d+(?:\.\d+)?(?:px)?['"`]?/g,
  /** Tailwind 任意值字号类 */
  twArbitraryText: /\btext-\[(?:length:)?\d+(?:\.\d+)?(?:px|rem|em)\]/g,
  /** 渐变 */
  gradient: /\b(?:linear|radial|conic)-gradient\(/g,
  /** 阴影（不含 none 与引用 token 的） */
  shadow:
    /box-shadow\s*:(?!\s*(?:none|var\(--nus-))[^;]+|(?<![\w-])shadow-(?:sm|md|lg|xl|2xl|inner|float|primary)\b/g,
  /** 写死的颜色：十六进制与数值 rgb/hsl（引用变量的 hsl(var(--x)) 不算） */
  colorLiteral: /#[\da-f]{3,8}(?![\w-])|\brgba?\(\s*\d|\bhsla?\(\s*\d/gi,
  /** !important */
  important: /!important/g,
  /** 自写的状态标签函数（应改用 utils/status.ts 与 StatusTag） */
  statusHelper:
    /\bfunction\s+get\w*Status\w*(?:Tag|Color|Text)\b|\bconst\s+get\w*Status\w*(?:Tag|Color|Text)\s*=/g,
  /** 彩色标签：<Tag color=...> */
  tagColor: /<(?:Tag|a-tag|ATag)\b[^>]*\s(?::|v-bind:)?color=/g,
};

function countRadius(source) {
  let count = 0;
  const details = [];
  for (const match of source.matchAll(/border-radius\s*:\s*([^;}\n]+)/g)) {
    const value = match[1].trim();
    // 按空格拆成四个角的取值，var(...) 作为一个整体
    const parts = value.match(/var\([^)]*\)|\S+/g) ?? [];
    if (!parts.every((part) => ALLOWED_RADIUS.test(part))) {
      count++;
      details.push(match[0]);
    }
  }
  for (const match of source.matchAll(
    /\brounded(?:-[trbl]{1,2})?-(?:2xl|3xl|\[[^\]]+\])/g,
  )) {
    count++;
    details.push(match[0]);
  }
  return { count, details };
}

/** 颜色字面量里排除不是颜色的十六进制（锚点、实体等），保留 3/4/6/8 位 */
function countColors(source) {
  const details = [];
  for (const match of source.matchAll(RULES.colorLiteral)) {
    const text = match[0];
    if (text.startsWith('#') && ![4, 5, 7, 9].includes(text.length)) continue;
    details.push(text);
  }
  return { count: details.length, details };
}

export function scanSource(source) {
  const result = {};
  for (const [name, pattern] of Object.entries(RULES)) {
    if (name === 'colorLiteral') continue;
    const matches = [...source.matchAll(pattern)].map((m) => m[0]);
    result[name] = { count: matches.length, details: matches };
  }
  result.colorLiteral = countColors(source);
  result.radius = countRadius(source);
  return result;
}

function walk(path, files) {
  const stat = statSync(path);
  if (stat.isDirectory()) {
    for (const name of readdirSync(path)) {
      if (name === 'node_modules' || name.startsWith('.')) continue;
      walk(join(path, name), files);
    }
  } else if (
    /\.(?:vue|ts|tsx|css|scss|less)$/.test(path) &&
    !/\.(?:test|spec)\.ts$/.test(path)
  ) {
    files.push(path);
  }
}

export function scanPaths(paths) {
  const files = [];
  for (const path of paths) walk(resolve(path), files);
  return files.map((file) => ({
    file: relative(process.cwd(), file),
    metrics: scanSource(readFileSync(file, 'utf8')),
  }));
}

/** 结果写到标准输出（不用 console.log，项目的 lint 规则只放行 warn 与 error） */
function print(text) {
  process.stdout.write(`${text}\n`);
}

const METRIC_NAMES = [
  'fontSizePx',
  'twArbitraryText',
  'radius',
  'gradient',
  'shadow',
  'colorLiteral',
  'important',
  'statusHelper',
  'tagColor',
];

function main() {
  const args = process.argv.slice(2);
  const json = args.includes('--json');
  const detail = args.includes('--detail');
  const paths = args.filter((arg) => !arg.startsWith('--'));
  const targets =
    paths.length > 0
      ? paths
      : [join(APP, 'src/views'), join(APP, 'src/components')];
  const rows = scanPaths(targets);
  const totals = Object.fromEntries(METRIC_NAMES.map((name) => [name, 0]));
  for (const row of rows)
    for (const name of METRIC_NAMES) totals[name] += row.metrics[name].count;
  if (json) {
    print(JSON.stringify({ totals, files: rows }, null, 2));
    return;
  }
  const short = {
    fontSizePx: 'fs',
    twArbitraryText: 'tw',
    radius: 'rad',
    gradient: 'grad',
    shadow: 'shd',
    colorLiteral: 'clr',
    important: 'imp',
    statusHelper: 'sts',
    tagColor: 'tag',
  };
  print(['file', ...METRIC_NAMES.map((n) => short[n])].join('\t'));
  for (const row of rows) {
    const counts = METRIC_NAMES.map((name) => row.metrics[name].count);
    if (counts.every((c) => c === 0)) continue;
    print([row.file, ...counts].join('\t'));
    if (detail) {
      for (const name of METRIC_NAMES) {
        for (const item of row.metrics[name].details)
          print(`    ${short[name]}: ${item}`);
      }
    }
  }
  print(['TOTAL', ...METRIC_NAMES.map((name) => totals[name])].join('\t'));
}

if (
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url)
)
  main();
