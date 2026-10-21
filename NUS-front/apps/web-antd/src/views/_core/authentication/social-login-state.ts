// 对应后端 system 模块 ErrorCodeConstants.AUTH_THIRD_LOGIN_NOT_BIND。
export const AUTH_THIRD_LOGIN_NOT_BIND_CODE = 1_002_000_005;

export function shouldShowSocialBindForm(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return false;
  }
  const response = error as {
    data?: { code?: unknown };
    response?: { data?: { code?: unknown } };
  };
  return (
    Number(response.response?.data?.code ?? response.data?.code) ===
    AUTH_THIRD_LOGIN_NOT_BIND_CODE
  );
}
