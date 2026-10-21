#!/usr/bin/env node
/**
 * 重新生成样式指标基线（只在改掉存量问题、指标下降后运行）：node apps/web-antd/tests/style/write-baseline.mjs
 */
import { writeFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { scanPaths } from '../../scripts/style-metrics.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const APP = join(HERE, '..', '..');
const files = {};
const totals = {};
for (const row of scanPaths([
  join(APP, 'src/views'),
  join(APP, 'src/components'),
])) {
  const file = relative(APP, join(process.cwd(), row.file))
    .split('\\')
    .join('/');
  for (const [name, { count }] of Object.entries(row.metrics)) {
    if (count === 0) continue;
    files[file] = { ...files[file], [name]: count };
    totals[name] = (totals[name] ?? 0) + count;
  }
}
const sorted = Object.fromEntries(
  Object.entries(files).toSorted(([a], [b]) => a.localeCompare(b)),
);
writeFileSync(
  join(HERE, 'style-baseline.json'),
  `${JSON.stringify({ files: sorted, totals }, null, 2)}\n`,
);
process.stdout.write(`baseline written: ${JSON.stringify(totals)}\n`);
