import { describe, expect, it, vi } from 'vitest';

import { CLASS_ACTION_COLUMN_WIDTH, useGridColumns } from './data';

vi.mock('#/api/system/dept', () => ({ getDeptList: vi.fn() }));
vi.mock('#/adapter/form', () => ({ z: {} }));
vi.mock('#/utils', () => ({ getRangePickerDefaultProps: () => ({}) }));
vi.mock('@vben/hooks', () => ({ getDictOptions: () => [] }));

describe('班级列表的列', () => {
  const columns = useGridColumns() ?? [];
  const byTitle = (title: string) =>
    columns.find((column) => column.title === title);

  it('操作列固定在右侧，宽度收到约 200', () => {
    const actions = byTitle('操作');
    expect(actions?.fixed).toBe('right');
    expect(actions?.width).toBe(CLASS_ACTION_COLUMN_WIDTH);
    expect(CLASS_ACTION_COLUMN_WIDTH).toBeLessThanOrEqual(210);
  });

  it('创建时间、班级编号列收窄', () => {
    expect(byTitle('创建时间')?.minWidth).toBe(160);
    expect(byTitle('班级编号')?.minWidth).toBe(80);
  });
});
