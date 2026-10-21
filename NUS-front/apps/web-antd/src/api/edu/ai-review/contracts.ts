export interface AiTaskStartResponse {
  taskId: number;
}

export function parseAiTaskStartResponse(value: unknown): AiTaskStartResponse {
  if (!value || typeof value !== 'object' || !('taskId' in value)) {
    throw new Error(
      'AI 任务已启动，但服务器没有返回任务编号，无法显示进度。稍后刷新页面查看结果。',
    );
  }
  if (Object.keys(value).length !== 1) {
    throw new Error(
      'AI 任务已启动，但服务器返回的结果格式不对，无法显示进度。稍后刷新页面查看结果。',
    );
  }
  const taskId = (value as { taskId?: unknown }).taskId;
  if (
    typeof taskId !== 'number' ||
    !Number.isSafeInteger(taskId) ||
    taskId <= 0
  ) {
    throw new Error(
      'AI 任务已启动，但服务器返回的任务编号无效，无法显示进度。稍后刷新页面查看结果。',
    );
  }
  return { taskId };
}
