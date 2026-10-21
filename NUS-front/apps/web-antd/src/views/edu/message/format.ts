/**
 * 留言时间的呈现（教师“留言互动”与学生“我的留言”共用）：对话里显示到分钟；
 * 会话列表里今天只显示时分、今年显示月日、更早显示年月日。
 */

const pad = (value: number) => String(value).padStart(2, '0');

/** “2026-10-06T19:46:12” → “2026-10-06 19:46” */
export function formatMessageTime(value?: null | string): string {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '';
}

export function formatListTime(
  value?: null | string,
  now: Date = new Date(),
): string {
  const full = formatMessageTime(value);
  if (full.length < 16) {
    return full;
  }
  const day = full.slice(0, 10);
  const today = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  if (day === today) {
    return full.slice(11);
  }
  if (day.startsWith(`${now.getFullYear()}-`)) {
    return full.slice(5, 10);
  }
  return day;
}

/** 头像位置显示姓名的第一个字 */
export function nameInitial(name?: null | string): string {
  const trimmed = (name ?? '').trim();
  return [...trimmed][0] ?? '?';
}
