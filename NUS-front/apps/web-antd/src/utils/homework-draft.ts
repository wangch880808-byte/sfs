const PREFIX = 'homework_draft_';

/** 草稿按当前用户和任务隔离；无身份时不写入。 */
export function getHomeworkDraftKey(
  userId: number | string,
  homeworkId: number | string,
) {
  return `${PREFIX}${userId}_${homeworkId}`;
}

/** 清除旧版共享草稿，避免机房换人登录后读到别人的内容。 */
export function clearLegacyHomeworkDrafts(storage: Storage) {
  for (let index = storage.length - 1; index >= 0; index--) {
    const key = storage.key(index);
    if (key && /^homework_draft_\d+$/.test(key)) storage.removeItem(key);
  }
}

/** 登出时仅删除当前用户草稿，同时清除所有旧版共享 key。 */
export function clearUserHomeworkDrafts(
  storage: Storage,
  userId?: number | string,
) {
  clearLegacyHomeworkDrafts(storage);
  if (userId === undefined || userId === null || userId === '') return;
  const prefix = `${PREFIX}${userId}_`;
  for (let index = storage.length - 1; index >= 0; index--) {
    const key = storage.key(index);
    if (key?.startsWith(prefix)) storage.removeItem(key);
  }
}
