/**
 * 分页列表在删除、封存、移出等操作后，当前页可能已经没有数据（例如删掉了最后一页的最后一条）。
 * 这时表格变空、分页器还停在原页（或被组件改到末页却没有重新读取）。
 * 这里算出应当回退到的页码：当前页为空且不在第 1 页时，回到按总数算出的末页，至少回退一页；不需要回退时返回 undefined。
 */
export function getPageNoAfterEmptyPage(options: {
  /** 当前页读到的条数 */
  listLength: number;
  /** 刚才读取的页码 */
  pageNo: number;
  pageSize: number;
  /** 后端返回的总数 */
  total: number;
}): number | undefined {
  const pageNo = Math.floor(Number(options.pageNo) || 1);
  if (options.listLength > 0 || pageNo <= 1) {
    return undefined;
  }
  const pageSize = Number(options.pageSize) || 0;
  const total = Math.max(0, Number(options.total) || 0);
  const lastPage = pageSize > 0 ? Math.max(1, Math.ceil(total / pageSize)) : 1;
  return Math.max(1, Math.min(lastPage, pageNo - 1));
}
