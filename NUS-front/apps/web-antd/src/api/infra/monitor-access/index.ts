import { requestClient } from '#/api/request';

export type MonitorTarget = 'DRUID' | 'JAVA' | 'SKYWALKING';
export type MonitorTheme = 'DARK' | 'LIGHT';

interface MonitorAccessTicket {
  url: string;
}

export async function createMonitorAccessUrl(
  target: MonitorTarget,
  theme: MonitorTheme,
  options?: { silentError?: boolean },
) {
  const ticket = await requestClient.post<MonitorAccessTicket>(
    '/infra/monitor-access/ticket',
    undefined,
    { params: { target, theme }, ...options },
  );
  return ticket.url;
}
