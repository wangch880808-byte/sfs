import type { Recordable } from '@vben/types';

import { listIcons } from '@vben/icons';

/**
 * 一个缓存对象，在不刷新页面时，无需重复请求远程接口
 */
export const ICONS_MAP: Recordable<string[]> = {};

const LOCAL_ICON_PREFIXES = new Set(['ant-design', 'carbon', 'lucide', 'mdi']);

const PENDING_REQUESTS: Recordable<Promise<string[]>> = {};

function getLocalIcons(prefix: string) {
  try {
    return listIcons('', prefix);
  } catch {
    return [];
  }
}

/**
 * 通过Iconify接口获取图标集数据。
 * 同一时间多个图标选择器同时请求同一个图标集时，实际上只会发起一次请求（所有请求共享同一份结果）。
 * 请求结果会被缓存，刷新页面前同一个图标集不会再次请求
 * @param prefix 图标集名称
 * @returns 图标集中包含的所有图标名称
 */
export async function fetchIconsData(prefix: string): Promise<string[]> {
  if (Reflect.has(ICONS_MAP, prefix) && ICONS_MAP[prefix]) {
    return ICONS_MAP[prefix];
  }
  if (Reflect.has(PENDING_REQUESTS, prefix) && PENDING_REQUESTS[prefix]) {
    return PENDING_REQUESTS[prefix];
  }
  PENDING_REQUESTS[prefix] = Promise.resolve().then(() => {
    if (!LOCAL_ICON_PREFIXES.has(prefix)) {
      console.warn(`[IconPicker] Blocked remote icon prefix: ${prefix}`);
      ICONS_MAP[prefix] = [];
      return ICONS_MAP[prefix];
    }

    const icons = getLocalIcons(prefix);
    if (icons.length === 0) {
      console.warn(`[IconPicker] No local icons found for prefix: ${prefix}`);
    }
    ICONS_MAP[prefix] = icons;
    return ICONS_MAP[prefix];
  });
  return PENDING_REQUESTS[prefix];
}
