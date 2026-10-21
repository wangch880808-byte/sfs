/** 新密码规则与后端保持一致。旧密码只校验非空，兼容旧的初始密码。 */
export function getNewPasswordError(
  newPassword: string,
  oldPassword: string,
  username?: string,
): null | string {
  if (newPassword.length < 8 || newPassword.length > 16) {
    return '新密码长度须为 8–16 位';
  }
  if (!/[a-z]/i.test(newPassword) || !/\d/.test(newPassword)) {
    return '新密码须同时包含字母和数字';
  }
  if (username && newPassword === username) {
    return '新密码不能与用户名相同';
  }
  if (newPassword === oldPassword) {
    return '新旧密码不能相同';
  }
  return null;
}

export function getPasswordFormError(
  oldPassword: string,
  newPassword: string,
  confirmPassword: string,
  username?: string,
): null | string {
  if (!oldPassword) {
    return '请输入旧密码';
  }
  const newPasswordError = getNewPasswordError(
    newPassword,
    oldPassword,
    username,
  );
  if (newPasswordError) {
    return newPasswordError;
  }
  if (confirmPassword !== newPassword) {
    return '两次输入的新密码不一致';
  }
  return null;
}
