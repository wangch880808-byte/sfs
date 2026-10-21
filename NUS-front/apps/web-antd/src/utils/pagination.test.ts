import { describe, expect, it } from 'vitest';

import { getPageNoAfterEmptyPage } from './pagination';

describe('分页：当前页变空后回退', () => {
  it('当前页有数据或已在第 1 页时不回退', () => {
    expect(
      getPageNoAfterEmptyPage({
        listLength: 3,
        pageNo: 2,
        pageSize: 10,
        total: 13,
      }),
    ).toBeUndefined();
    expect(
      getPageNoAfterEmptyPage({
        listLength: 0,
        pageNo: 1,
        pageSize: 10,
        total: 0,
      }),
    ).toBeUndefined();
  });

  it('删掉最后一页的最后一条：回到上一页', () => {
    expect(
      getPageNoAfterEmptyPage({
        listLength: 0,
        pageNo: 3,
        pageSize: 10,
        total: 20,
      }),
    ).toBe(2);
  });

  it('一次少了好几页（例如清空或批量删除）：直接回到末页', () => {
    expect(
      getPageNoAfterEmptyPage({
        listLength: 0,
        pageNo: 5,
        pageSize: 10,
        total: 12,
      }),
    ).toBe(2);
    expect(
      getPageNoAfterEmptyPage({
        listLength: 0,
        pageNo: 5,
        pageSize: 10,
        total: 0,
      }),
    ).toBe(1);
  });

  it('总数与列表对不上时也至少回退一页，保证不会原地重复读取', () => {
    expect(
      getPageNoAfterEmptyPage({
        listLength: 0,
        pageNo: 3,
        pageSize: 10,
        total: 100,
      }),
    ).toBe(2);
    expect(
      getPageNoAfterEmptyPage({
        listLength: 0,
        pageNo: 3,
        pageSize: 0,
        total: 100,
      }),
    ).toBe(1);
  });
});
