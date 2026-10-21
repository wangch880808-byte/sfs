import { describe, expect, it } from 'vitest';

import { diffLines, diffStats, splitLines, toSideBySide } from './line-diff';

const types = (oldText: string, newText: string) =>
  diffLines(oldText, newText).map((op) => `${op.type[0]}:${op.text}`);

describe('line-diff', () => {
  it('拆行：统一换行符，空文本为 0 行', () => {
    expect(splitLines('')).toEqual([]);
    expect(splitLines(undefined)).toEqual([]);
    expect(splitLines('a\r\nb\rc')).toEqual(['a', 'b', 'c']);
    expect(splitLines('a\n')).toEqual(['a', '']);
  });

  it('完全相同时全是相同行，行号左右一致', () => {
    const ops = diffLines('a\nb', 'a\nb');
    expect(ops).toEqual([
      { type: 'same', text: 'a', oldNo: 1, newNo: 1 },
      { type: 'same', text: 'b', oldNo: 2, newNo: 2 },
    ]);
    expect(diffStats(ops)).toEqual({ added: 0, removed: 0 });
  });

  it('中间改一行：先删除旧行再新增新行', () => {
    expect(types('a\nb\nc', 'a\nB\nc')).toEqual(['s:a', 'r:b', 'a:B', 's:c']);
  });

  it('插入与删除：用最长公共子序列保留相同的行', () => {
    expect(types('a\nb\nc\nd', 'a\nc\nx\nd\ne')).toEqual([
      's:a',
      'r:b',
      's:c',
      'a:x',
      's:d',
      'a:e',
    ]);
    const ops = diffLines('a\nb\nc\nd', 'a\nc\nx\nd\ne');
    expect(diffStats(ops)).toEqual({ added: 2, removed: 1 });
    // 行号分别对应旧内容与新内容
    expect(ops.find((op) => op.text === 'x')).toEqual({
      type: 'added',
      text: 'x',
      newNo: 3,
    });
    expect(ops.find((op) => op.text === 'd')).toEqual({
      type: 'same',
      text: 'd',
      oldNo: 4,
      newNo: 4,
    });
  });

  it('一边为空：全部新增或全部删除', () => {
    expect(types('', 'a\nb')).toEqual(['a:a', 'a:b']);
    expect(types('a\nb', '')).toEqual(['r:a', 'r:b']);
  });

  it('并排：相同行对齐，相邻的删除与新增逐行配对', () => {
    const rows = toSideBySide(diffLines('a\nb\nc\nd', 'a\nB\nd\ne'));
    expect(rows).toEqual([
      {
        left: { no: 1, text: 'a', type: 'same' },
        right: { no: 1, text: 'a', type: 'same' },
      },
      {
        left: { no: 2, text: 'b', type: 'removed' },
        right: { no: 2, text: 'B', type: 'added' },
      },
      { left: { no: 3, text: 'c', type: 'removed' }, right: undefined },
      {
        left: { no: 4, text: 'd', type: 'same' },
        right: { no: 3, text: 'd', type: 'same' },
      },
      { left: undefined, right: { no: 4, text: 'e', type: 'added' } },
    ]);
  });
});
