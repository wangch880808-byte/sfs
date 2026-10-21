import { z } from '#/adapter/form';

/**
 * 个人资料表单：手机、邮箱、性别都可以不填；填了才校验格式。
 * 接口返回的空值可能是 null，规则也放行 null，免得没填的字段一打开就报错。
 */
export const profileMobileRule = z
  .string()
  .regex(/^1\d{10}$/, '请输入正确的手机号')
  .optional()
  .or(z.literal(''))
  .nullable();

export const profileEmailRule = z
  .string()
  .email('请输入正确的邮箱')
  .optional()
  .or(z.literal(''))
  .nullable();

export const profileSexRule = z.number().nullish();

/**
 * 提交前整理：null 转成 undefined（不改这个字段）；只含空白的字符串转成空字符串，
 * 这样清空手机或邮箱时后端会把它清掉（后端允许空手机号与空邮箱）。
 */
export function normalizeProfileValues<T extends Record<string, any>>(
  values: T,
): T {
  const result: Record<string, any> = { ...values };
  for (const key of Object.keys(result)) {
    const value = result[key];
    if (value === null) {
      result[key] = undefined;
    } else if (typeof value === 'string' && value.trim() === '') {
      result[key] = '';
    }
  }
  return result as T;
}
