import type { EduHomeworkApi } from '#/api/edu/homework';

type PreviewItem = EduHomeworkApi.HomeworkAiCreatePreviewItem;

/**
 * 把后台重新生成的结果合并到预览里的题目。
 * 成功时换成新题；没成功时只更新提示，保留教师在页面上已改的标题和要求。
 * 返回是否重新生成成功。
 */
export function mergeRegeneratedItem(
  local: PreviewItem,
  latest: PreviewItem,
): boolean {
  local.regenerating = false;
  if (latest.success && !latest.errorMessage) {
    local.homeworkName = latest.homeworkName;
    local.requirement = latest.requirement;
    local.success = true;
    local.errorMessage = undefined;
    return true;
  }
  local.errorMessage = latest.errorMessage;
  const hasLocalContent =
    !!local.homeworkName?.trim() || !!local.requirement?.trim();
  if (!hasLocalContent) {
    local.success = latest.success;
  }
  return false;
}

/** 轮询时偶发的网络错误先按“仍在生成”处理，连续失败达到上限才放弃。 */
export function createTolerantRequest<T>(
  request: () => Promise<T>,
  maxConsecutiveFailures = 3,
): () => Promise<T | undefined> {
  let failures = 0;
  return async () => {
    try {
      const result = await request();
      failures = 0;
      return result;
    } catch (error) {
      failures += 1;
      if (failures >= maxConsecutiveFailures) {
        throw error;
      }
      return undefined;
    }
  };
}
