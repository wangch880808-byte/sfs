import type { VxeTableGridOptions } from '#/adapter/vxe-table';

type GridColumns = NonNullable<VxeTableGridOptions['columns']>;

/**
 * 教师、学生列表在系统用户列的基础上调整：
 * - 列表已按用户类型限定，「用户类型」列每行都一样，去掉；
 * - 状态列改用 StatusTag 插槽；
 * - 用户编号只给最小宽度，给姓名、班级等列让出位置。
 */
export function adaptEduUserColumns(columns: GridColumns = []): GridColumns {
  return columns
    .filter((column) => column.field !== 'userType')
    .map((column) => {
      if (column.field === 'status') {
        return {
          ...column,
          cellRender: undefined,
          slots: { default: 'status' },
        };
      }
      if (column.field === 'id') {
        return { ...column, minWidth: 80 };
      }
      return column;
    });
}
