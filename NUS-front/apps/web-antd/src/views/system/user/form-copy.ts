/** 用户表单的标题与提示（不依赖接口，便于单测） */

/** 初始密码提示：与后端建号规则一致（4–16 位）；首次登录时再按 utils/password-rule.ts 改成正式密码 */
export const INITIAL_PASSWORD_PLACEHOLDER = '请输入初始密码（4–16 位）';

/** 弹窗标题：新建学生 / 编辑教师；调用方没传对象名时写「用户」 */
export function getUserFormTitle(isEdit: boolean, entityName?: string) {
  const name = entityName?.trim() || '用户';
  return isEdit ? `编辑${name}` : `新建${name}`;
}
