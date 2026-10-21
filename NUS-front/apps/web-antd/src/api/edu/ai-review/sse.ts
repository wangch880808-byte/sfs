import { useAppConfig } from '@vben/hooks';
import { fetchEventSource } from '@vben/request';
import { useAccessStore } from '@vben/stores';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);

interface CommonResultPayload<T> {
  code: number;
  data?: T;
  msg?: string;
}

export function parseCommonResultSseData(raw: string): string | undefined {
  if (!raw || raw === '[DONE]') return undefined;
  let payload: CommonResultPayload<unknown>;
  try {
    payload = JSON.parse(raw);
  } catch {
    throw new Error('AI 返回的内容无法解析。稍后重试。');
  }
  if (payload.code !== 0) {
    throw new Error(payload.msg || 'AI 服务返回错误，原因未知。稍后重试。');
  }
  if (payload.data === undefined || payload.data === null) return undefined;
  return String(payload.data);
}

export async function streamAiCommonResult({
  ctrl,
  data,
  onData,
  path,
}: {
  ctrl: AbortController;
  data: unknown;
  onData: (content: string) => void;
  path: string;
}) {
  const accessStore = useAccessStore();
  let received = false;
  let terminalError: Error | undefined;
  try {
    await fetchEventSource(`${apiURL}${path}`, {
      method: 'post',
      headers: {
        Authorization: `Bearer ${accessStore.accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
      openWhenHidden: true,
      signal: ctrl.signal,
      onmessage(event) {
        try {
          const content = parseCommonResultSseData(event.data);
          if (content !== undefined && content.length > 0) {
            received = true;
            onData(content);
          }
        } catch (error) {
          terminalError =
            error instanceof Error
              ? error
              : new Error('AI 服务返回错误，原因未知。稍后重试。');
          ctrl.abort();
          throw terminalError;
        }
      },
      onerror(error) {
        throw terminalError || error;
      },
    });
  } catch (error) {
    if (terminalError) throw terminalError;
    throw error;
  }
  if (!received && !ctrl.signal.aborted) {
    throw new Error('AI 没有返回内容。稍后重试。');
  }
}
