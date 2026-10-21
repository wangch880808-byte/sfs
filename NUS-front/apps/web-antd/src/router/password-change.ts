export const PASSWORD_CHANGE_PATH = '/auth/initial-password';

/** 登录后，除改密页外的导航都必须先完成初始密码修改。 */
export function shouldRedirectToPasswordChange(
  required: boolean,
  targetPath: string,
): boolean {
  return required && targetPath !== PASSWORD_CHANGE_PATH;
}
