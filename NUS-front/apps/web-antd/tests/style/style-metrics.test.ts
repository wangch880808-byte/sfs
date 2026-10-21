import { readFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

// @ts-expect-error 自查脚本是 .mjs，没有类型声明
import { scanPaths } from '../../scripts/style-metrics.mjs';

/**
 * 样式指标的守护（V1.0.1.6 验收第 2～4、7 条）：页面与组件里写死的字号、颜色、圆角、渐变、阴影、!important、
 * 自写状态函数与彩色标签，每个文件只许比基线少，不许多；新文件一律为 0。
 * 改掉了存量问题就重新生成基线：node apps/web-antd/tests/style/write-baseline.mjs
 */
type Metrics = Record<string, { count: number; details: string[] }>;
interface Row {
  file: string;
  metrics: Metrics;
}

const HERE = dirname(fileURLToPath(import.meta.url));
const APP = join(HERE, '..', '..');
const BASELINE: Record<string, Record<string, number>> = JSON.parse(
  readFileSync(join(HERE, 'style-baseline.json'), 'utf8'),
).files;

const rows: Row[] = (
  scanPaths([join(APP, 'src/views'), join(APP, 'src/components')]) as Row[]
).map((row) => ({
  ...row,
  file: relative(APP, join(process.cwd(), row.file)).split('\\').join('/'),
}));

describe('样式指标（只许下降）', () => {
  it('扫到了页面与组件', () => {
    expect(rows.length).toBeGreaterThan(150);
  });

  it('每个文件的各项指标不超过基线；不在基线里的文件全部为 0', () => {
    const over: string[] = [];
    for (const row of rows) {
      const allowed = BASELINE[row.file] ?? {};
      for (const [name, { count, details }] of Object.entries(row.metrics)) {
        if (count > (allowed[name] ?? 0)) {
          over.push(
            `${row.file} ${name} ${count} > ${allowed[name] ?? 0}：${details.slice(0, 3).join(' | ')}`,
          );
        }
      }
    }
    expect(over).toEqual([]);
  });

  it('基线只许删：基线里的条目都还在（改掉了就重新生成基线）', () => {
    const now = new Map(rows.map((row) => [row.file, row.metrics]));
    const stale: string[] = [];
    for (const [file, metrics] of Object.entries(BASELINE)) {
      for (const [name, count] of Object.entries(metrics)) {
        const current = now.get(file)?.[name]?.count ?? 0;
        if (current < count)
          stale.push(`${file} ${name} ${count} → ${current}`);
      }
    }
    expect(stale).toEqual([]);
  });
});
