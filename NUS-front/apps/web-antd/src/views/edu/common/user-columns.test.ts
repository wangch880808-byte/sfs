import { describe, expect, it } from 'vitest';

import { adaptEduUserColumns } from './user-columns';

describe('教师、学生列表的列', () => {
  const columns = [
    { type: 'checkbox' as const, width: 40 },
    { field: 'id', minWidth: 100, title: '用户编号' },
    { field: 'userType', minWidth: 120, title: '用户类型' },
    {
      cellRender: { name: 'CellSwitch' },
      field: 'status',
      minWidth: 100,
      title: '状态',
    },
    { field: 'createTime', minWidth: 160, title: '创建时间' },
  ];

  it('去掉每行都一样的「用户类型」列，编号列收窄', () => {
    const result = adaptEduUserColumns(columns);
    expect(result.map((column) => column.field)).toEqual([
      undefined,
      'id',
      'status',
      'createTime',
    ]);
    expect(result.find((column) => column.field === 'id')?.minWidth).toBe(80);
  });

  it('状态列改用插槽', () => {
    const status = adaptEduUserColumns(columns).find(
      (column) => column.field === 'status',
    );
    expect(status?.cellRender).toBeUndefined();
    expect(status?.slots).toEqual({ default: 'status' });
  });
});
