import { describe, expect, it } from 'vitest';

import {
  batchConfirm,
  CONFIRM_CANCEL_TEXT,
  DELETE_HINT,
  deleteBatchConfirm,
  deleteRowConfirm,
  DISCARD_CHANGES_HINT,
  discardChangesConfirm,
  hasActiveFilters,
  rowConfirm,
} from './confirm-copy';

describe('确认文案', () => {
  it('行内删除：标题写对象与动作，正文写后果，确认按钮重复动作，取消写返回', () => {
    expect(deleteRowConfirm('角色', '教师')).toEqual({
      cancelText: '返回',
      description: DELETE_HINT,
      okText: '删除',
      title: '删除角色「教师」？',
    });
    expect(deleteRowConfirm('令牌', undefined, '令牌立即失效。').title).toBe(
      '删除令牌？',
    );
  });

  it('批量操作写明数量与量词', () => {
    expect(deleteBatchConfirm('用户', 3)).toEqual({
      cancelText: CONFIRM_CANCEL_TEXT,
      confirmText: '删除',
      content: DELETE_HINT,
      title: '删除选中的 3 个用户？',
    });
    expect(
      batchConfirm('移出', '学生', 2, '可以随时重新加入。', '名'),
    ).toMatchObject({ confirmText: '移出', title: '移出选中的 2 名学生？' });
  });

  it('其他动作沿用同一格式', () => {
    expect(
      rowConfirm('停用', '账号', 'demostudent1', '停用后无法登录。'),
    ).toEqual({
      cancelText: '返回',
      description: '停用后无法登录。',
      okText: '停用',
      title: '停用账号「demostudent1」？',
    });
  });
});

describe('放弃修改的确认', () => {
  it('确认按钮写放弃修改，取消写返回，正文可换成具体后果', () => {
    expect(discardChangesConfirm()).toEqual({
      cancelText: '返回',
      confirmText: '放弃修改',
      content: DISCARD_CHANGES_HINT,
      title: '放弃没保存的修改？',
    });
    expect(discardChangesConfirm('任务设置不会保存。').content).toBe(
      '任务设置不会保存。',
    );
  });
});

describe('筛选条件是否生效', () => {
  it('空值、空字符串、空数组不算筛选', () => {
    expect(hasActiveFilters(undefined)).toBe(false);
    expect(hasActiveFilters({})).toBe(false);
    expect(
      hasActiveFilters({ createTime: [], name: '', status: undefined }),
    ).toBe(false);
    expect(hasActiveFilters({ createTime: [undefined, undefined] })).toBe(
      false,
    );
  });

  it('有值的条件算筛选，包括 0 与 false', () => {
    expect(hasActiveFilters({ name: '教师' })).toBe(true);
    expect(hasActiveFilters({ status: 0 })).toBe(true);
    expect(hasActiveFilters({ readStatus: false })).toBe(true);
    expect(hasActiveFilters({ createTime: ['2026-01-01', '2026-02-01'] })).toBe(
      true,
    );
  });
});
