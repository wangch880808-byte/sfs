/**
 * 提示词的逐行对比：用最长公共子序列（LCS）找出两段文字里相同的行，其余为删除（只在旧内容里）或新增（只在新内容里）。
 * 提示词一般几十到几百行，先去掉首尾相同的行再算，O(n·m) 足够。
 */

export type LineDiffType = 'added' | 'removed' | 'same';

export interface LineDiffOp {
  type: LineDiffType;
  text: string;
  /** 在旧内容里的行号（从 1 开始），新增行没有 */
  oldNo?: number;
  /** 在新内容里的行号（从 1 开始），删除行没有 */
  newNo?: number;
}

export interface SideBySideCell {
  no: number;
  text: string;
  type: LineDiffType;
}

/** 左右并排的一行：左边是旧内容，右边是新内容；一边没有对应行时为空 */
export interface SideBySideRow {
  left?: SideBySideCell;
  right?: SideBySideCell;
}

export interface LineDiffStats {
  added: number;
  removed: number;
}

/** 统一换行符后按行拆开；空文本为 0 行 */
export function splitLines(text: null | string | undefined): string[] {
  if (!text) {
    return [];
  }
  return text.replaceAll('\r\n', '\n').replaceAll('\r', '\n').split('\n');
}

export function diffLines(
  oldText: null | string | undefined,
  newText: null | string | undefined,
): LineDiffOp[] {
  const a = splitLines(oldText);
  const b = splitLines(newText);

  // 去掉首尾相同的行，缩小 LCS 的表
  let start = 0;
  while (start < a.length && start < b.length && a[start] === b[start]) {
    start++;
  }
  let endA = a.length;
  let endB = b.length;
  while (endA > start && endB > start && a[endA - 1] === b[endB - 1]) {
    endA--;
    endB--;
  }

  const midA = a.slice(start, endA);
  const midB = b.slice(start, endB);
  const n = midA.length;
  const m = midB.length;

  // lcs(i, j)：midA[i..] 与 midB[j..] 的最长公共子序列长度，按行展开存成一维表
  const width = m + 1;
  const table = new Uint32Array((n + 1) * width);
  const lcs = (i: number, j: number) => table[i * width + j] ?? 0;
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      table[i * width + j] =
        midA[i] === midB[j]
          ? lcs(i + 1, j + 1) + 1
          : Math.max(lcs(i + 1, j), lcs(i, j + 1));
    }
  }

  const ops: LineDiffOp[] = [];
  const same = (text: string, oldNo: number, newNo: number) =>
    ops.push({ type: 'same', text, oldNo, newNo });
  const removeAt = (i: number) =>
    ops.push({ type: 'removed', text: midA[i] ?? '', oldNo: start + i + 1 });
  const addAt = (j: number) =>
    ops.push({ type: 'added', text: midB[j] ?? '', newNo: start + j + 1 });

  for (let k = 0; k < start; k++) {
    same(a[k] ?? '', k + 1, k + 1);
  }
  let i = 0;
  let j = 0;
  while (i < n && j < m) {
    if (midA[i] === midB[j]) {
      same(midA[i] ?? '', start + i + 1, start + j + 1);
      i++;
      j++;
    } else if (lcs(i + 1, j) >= lcs(i, j + 1)) {
      // 打平时先删除再新增，同一处修改先显示旧行
      removeAt(i++);
    } else {
      addAt(j++);
    }
  }
  while (i < n) removeAt(i++);
  while (j < m) addAt(j++);
  for (let k = 0; k < a.length - endA; k++) {
    same(a[endA + k] ?? '', endA + k + 1, endB + k + 1);
  }
  return ops;
}

export function diffStats(ops: readonly LineDiffOp[]): LineDiffStats {
  let added = 0;
  let removed = 0;
  for (const op of ops) {
    if (op.type === 'added') added++;
    else if (op.type === 'removed') removed++;
  }
  return { added, removed };
}

/** 并排显示：相同的行左右对齐；相邻的删除与新增逐行配对，多出来的一边留空 */
export function toSideBySide(ops: readonly LineDiffOp[]): SideBySideRow[] {
  const rows: SideBySideRow[] = [];
  let removed: SideBySideCell[] = [];
  let added: SideBySideCell[] = [];

  const flush = () => {
    const count = Math.max(removed.length, added.length);
    for (let k = 0; k < count; k++) {
      rows.push({ left: removed[k], right: added[k] });
    }
    removed = [];
    added = [];
  };

  for (const op of ops) {
    const oldNo = op.oldNo ?? 0;
    const newNo = op.newNo ?? 0;
    if (op.type === 'removed') {
      removed.push({ no: oldNo, text: op.text, type: 'removed' });
    } else if (op.type === 'added') {
      added.push({ no: newNo, text: op.text, type: 'added' });
    } else {
      flush();
      rows.push({
        left: { no: oldNo, text: op.text, type: 'same' },
        right: { no: newNo, text: op.text, type: 'same' },
      });
    }
  }
  flush();
  return rows;
}
