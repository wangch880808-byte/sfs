import { requestClient } from '#/api/request';

export namespace EduWorkspaceApi {
  export interface TodoItem {
    category: string;
    label: string;
    count: number;
    route: string;
  }

  export interface TodoResp {
    todos: TodoItem[];
  }

  export interface DailyStat {
    date: string;
    submissions: number;
    gradings: number;
  }

  export interface RuntimeResp {
    weekClassCount: number;
    weekSubmissionCount: number;
    weekGradingCount: number;
    weekAiCallCount: number;
    dailyStats: DailyStat[];
  }
}

/** 获取待办事项 */
export function getWorkspaceTodos() {
  return requestClient.get<EduWorkspaceApi.TodoResp>('/edu/workspace/todos');
}

/** 获取运行概况 */
export function getWorkspaceRuntime() {
  return requestClient.get<EduWorkspaceApi.RuntimeResp>(
    '/edu/workspace/runtime',
  );
}
