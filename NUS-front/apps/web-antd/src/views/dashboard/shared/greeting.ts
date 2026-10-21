/**
 * 首页页头的问候（V1.0.1.6 界面规范）：标题只写“问候，名字”，不加称谓、感叹号和口号。
 */
export function getGreeting(date: Date = new Date()): string {
  const hour = date.getHours();
  if (hour < 6) return '夜深了';
  if (hour < 9) return '早安';
  if (hour < 12) return '上午好';
  if (hour < 14) return '中午好';
  if (hour < 18) return '下午好';
  return '晚上好';
}

/** 页头标题：“晚上好，王敏”；没有名字时只写问候 */
export function getGreetingTitle(
  name?: null | string,
  date: Date = new Date(),
): string {
  const greeting = getGreeting(date);
  const trimmed = name?.trim();
  return trimmed ? `${greeting}，${trimmed}` : greeting;
}
