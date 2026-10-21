/**
 * 系统管理与教务管理列表的确认文案与空状态判断（V1.0.1.6 界面规范 §三“确认框”“空状态”）。
 * 确认框：标题写对象和动作，正文写后果和怎么恢复；确认按钮重复动作名，取消按钮写“返回”。
 * 行内操作用气泡确认（TableAction 的 popConfirm），批量操作用对话框（@vben/common-ui 的 confirm）。
 */

export const CONFIRM_CANCEL_TEXT = '返回';

/** 删除的后果与恢复办法 */
export const DELETE_HINT = '删除后不能恢复，需要时重新新建。';

/** 行内气泡确认：title 与 description 分两行显示 */
export interface PopConfirmCopy {
  cancelText: string;
  description: string;
  okText: string;
  title: string;
}

/** 对话框确认：传给 confirm() */
export interface DialogConfirmCopy {
  cancelText: string;
  confirmText: string;
  content: string;
  title: string;
}

function quoted(name?: null | number | string) {
  return name === undefined || name === null || name === ''
    ? ''
    : `「${name}」`;
}

/** 行内操作：“封存班级「软件 1 班」？” */
export function rowConfirm(
  action: string,
  subject: string,
  name: null | number | string | undefined,
  description: string,
): PopConfirmCopy {
  return {
    cancelText: CONFIRM_CANCEL_TEXT,
    description,
    okText: action,
    title: `${action}${subject}${quoted(name)}？`,
  };
}

/** 批量操作：“封存选中的 3 个班级？” */
export function batchConfirm(
  action: string,
  subject: string,
  count: number,
  description: string,
  unit = '个',
): DialogConfirmCopy {
  return {
    cancelText: CONFIRM_CANCEL_TEXT,
    confirmText: action,
    content: description,
    title: `${action}选中的 ${count} ${unit}${subject}？`,
  };
}

/** 行内删除：“删除角色「教师」？删除后不能恢复，需要时重新新建。” */
export function deleteRowConfirm(
  subject: string,
  name?: null | number | string,
  description: string = DELETE_HINT,
): PopConfirmCopy {
  return rowConfirm('删除', subject, name, description);
}

/** 批量删除 */
export function deleteBatchConfirm(
  subject: string,
  count: number,
  description: string = DELETE_HINT,
  unit = '个',
): DialogConfirmCopy {
  return batchConfirm('删除', subject, count, description, unit);
}

/** 有没保存的修改时，放弃修改的默认后果说明 */
export const DISCARD_CHANGES_HINT =
  '这次填写的内容还没有保存，离开后不会保留。';

/** 离开页面、关闭弹窗前确认：“放弃没保存的修改？”确认按钮写“放弃修改”，取消写“返回” */
export function discardChangesConfirm(
  description: string = DISCARD_CHANGES_HINT,
): DialogConfirmCopy {
  return {
    cancelText: CONFIRM_CANCEL_TEXT,
    confirmText: '放弃修改',
    content: description,
    title: '放弃没保存的修改？',
  };
}

function isFilled(value: unknown): boolean {
  if (value === undefined || value === null || value === '') {
    return false;
  }
  if (Array.isArray(value)) {
    return value.some((item) => isFilled(item));
  }
  return true;
}

/** 列表的筛选条件是否生效：决定空状态写“还没有……”还是“没有符合条件的……” */
export function hasActiveFilters(
  values?: null | Record<string, unknown>,
): boolean {
  return !!values && Object.values(values).some((value) => isFilled(value));
}
