/**
 * 界面文案盘点（V1.0.1.6）：node apps/web-antd/tests/copy/inventory.ts [输出目录] [--write-baseline]
 *
 * 输出 inventory.json（全部文案与检查结果）、inventory.md（按页面的清单）、findings.md（按规则的命中），
 * 默认写到 node_modules/.cache/copy-inventory（相对前端仓库根）。
 * --write-baseline：把当前命中写进 apps/web-antd/tests/copy/copyBaseline.json（分批改文案期间的过渡基线，只许减少）。
 * 与 copy-scan.test.ts 用同一套扫描与规则。Node 直接运行（类型剥离），不经过构建。
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { APP_SRC, COPY_RULES_VERSION, pageOf } from './copyRules.ts';
import { findingsOf, fingerprint, scan } from './copyScan.ts';

/** 前端仓库根（NUS-front） */
const root = fileURLToPath(new URL('../../../../', import.meta.url));
const baselineFile = fileURLToPath(
  new URL('copyBaseline.json', import.meta.url),
);

const args = process.argv.slice(2);
const writeBaseline = args.includes('--write-baseline');
const outArg = args.find((a) => !a.startsWith('--'));
const out = resolve(
  outArg ?? join(root, 'node_modules', '.cache', 'copy-inventory'),
);
mkdirSync(out, { recursive: true });

const { entries, files, findings } = scan(root);

const short = (file: string) => file.replace(`${APP_SRC}/`, '');
const esc = (s: string) =>
  s.replaceAll('|', String.raw`\|`).replaceAll('\n', ' ');
const kindName: Record<string, string> = {
  attr: '属性',
  expr: '表达式',
  script: '脚本',
  text: '模板文字',
};

const groupBy = <T>(xs: T[], key: (x: T) => string): Map<string, T[]> => {
  const m = new Map<string, T[]>();
  for (const x of xs) {
    const k = key(x);
    const list = m.get(k);
    if (list) list.push(x);
    else m.set(k, [x]);
  }
  return m;
};

writeFileSync(
  join(out, 'inventory.json'),
  JSON.stringify(
    {
      entries: entries.map((e) => ({
        ...e,
        findings: findingsOf(e).map((f) => `${f.rule}:${f.hit}`),
      })),
      files: files.length,
      findings,
      generatedAt: new Date().toISOString(),
      rules: COPY_RULES_VERSION,
    },
    null,
    1,
  ),
);

const byPage = groupBy(entries, (e) => e.page);
const findingsByPage = groupBy(findings, (f) => pageOf(f.file));
let md = `# 界面文案盘点（前端）\n\n规则 ${COPY_RULES_VERSION}。扫描 ${files.length} 个文件，${entries.length} 条文案，检查命中 ${findings.length} 处。路径相对 ${APP_SRC}。\n\n`;
md += '| 页面或模块 | 条数 | 命中 |\n|---|---|---|\n';
for (const [page, es] of [...byPage].toSorted(
  (a, b) => b[1].length - a[1].length,
))
  md += `| ${page} | ${es.length} | ${findingsByPage.get(page)?.length ?? 0} |\n`;
for (const [page, es] of byPage) {
  md += `\n## ${page}\n\n| 位置 | 类别 | 字段或调用 | 原文 | 检查 |\n|---|---|---|---|---|\n`;
  for (const e of es) {
    const f = findingsOf(e)
      .map((x) => `${x.rule}:${x.hit}`)
      .join(' ');
    md += `| ${short(e.file)}:${e.line} | ${kindName[e.kind]} | ${esc(e.where)} | ${esc(e.text)} | ${esc(f)} |\n`;
  }
}
writeFileSync(join(out, 'inventory.md'), md);

const byRule = groupBy(findings, (f) => f.rule);
const byFindingFile = [...groupBy(findings, (f) => f.file)].toSorted(
  (a, b) => b[1].length - a[1].length || a[0].localeCompare(b[0]),
);
let fm = `# 检查命中（前端）\n\n规则 ${COPY_RULES_VERSION}，共 ${findings.length} 处。路径相对 ${APP_SRC}。\n\n| 规则 | 命中 |\n|---|---|\n`;
for (const [rule, fs] of byRule) fm += `| ${rule} | ${fs.length} |\n`;
fm += '\n## 命中最多的文件\n\n| 文件 | 命中 |\n|---|---|\n';
for (const [file, fs] of byFindingFile.slice(0, 20))
  fm += `| ${short(file)} | ${fs.length} |\n`;
for (const [rule, fs] of byRule) {
  fm += `\n## ${rule}\n\n| 位置 | 命中 | 原文 | 建议 |\n|---|---|---|---|\n`;
  for (const f of fs)
    fm += `| ${short(f.file)}:${f.line} | ${esc(f.hit)} | ${esc(f.text)} | ${esc(f.hint ?? '')} |\n`;
}
writeFileSync(join(out, 'findings.md'), fm);

/** 东亚宽字符的码位范围（十进制；与 prettier 计算行宽一致：这些字符算 2 列） */
const WIDE_RANGES: [number, number][] = [
  [4352, 4447], // U+1100–U+115F 韩文字母
  [11_904, 42_191], // U+2E80–U+A4CF 中日韩部首、标点、假名、汉字、彝文
  [44_032, 55_203], // U+AC00–U+D7A3 韩文音节
  [63_744, 64_255], // U+F900–U+FAFF 兼容汉字
  [65_072, 65_103], // U+FE30–U+FE4F 竖排标点
  [65_280, 65_376], // U+FF00–U+FF60 全角符号
  [65_504, 65_510], // U+FFE0–U+FFE6 全角货币符号
  [131_072, 262_141], // U+20000–U+3FFFD 扩展区汉字
];

/** 显示宽度：东亚宽字符算 2 列，其余算 1 列 */
const width = (s: string) => {
  let n = 0;
  for (const ch of s) {
    const cp = ch.codePointAt(0) ?? 0;
    n += WIDE_RANGES.some(([lo, hi]) => cp >= lo && cp <= hi) ? 2 : 1;
  }
  return n;
};

/** 基线文件的写法与 prettier 的 JSON 格式一致（放得下一行的数组写成一行），提交时不会被格式化改动 */
const formatBaseline = (prints: string[]): string => {
  const note = JSON.stringify(
    '分批改文案期间的过渡基线（docs/ui-copy.md §4）：还没改到的地方允许留下的命中，每条是「文件｜规则｜命中｜原文」。由 node apps/web-antd/tests/copy/inventory.ts --write-baseline 生成；只许删、不许加，改完一处就重新生成。',
  );
  const items = prints.map((p) => JSON.stringify(p));
  const flat = `  "findings": [${items.join(', ')}]`;
  const list =
    items.length === 0 || width(flat) <= 80
      ? flat
      : `  "findings": [\n${items.map((i) => `    ${i}`).join(',\n')}\n  ]`;
  return `{\n  "note": ${note},\n  "rules": ${JSON.stringify(COPY_RULES_VERSION)},\n${list}\n}\n`;
};

if (writeBaseline)
  writeFileSync(
    baselineFile,
    formatBaseline(findings.map((f) => fingerprint(f)).toSorted()),
  );

const counts = [...byRule].map(([r, fs]) => `${r} ${fs.length}`).join('，');
process.stdout.write(
  `文件 ${files.length}，文案 ${entries.length}，命中 ${findings.length}（${counts}）→ ${out}${writeBaseline ? `；已重写基线 ${baselineFile}` : ''}\n`,
);
