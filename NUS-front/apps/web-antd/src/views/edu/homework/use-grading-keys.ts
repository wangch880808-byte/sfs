import { onActivated, onBeforeUnmount, onDeactivated, onMounted } from 'vue';

/**
 * 批改页快捷键：J 或 ↓ 下一份，K 或 ↑ 上一份，Ctrl（Mac 上 Cmd）+ Enter 保存并看下一份。
 * 正在输入（输入框、文本域、下拉）或弹窗打开时只认 Ctrl + Enter，其余按键交还给控件。
 */
export type GradingKeyAction = 'next' | 'prev' | 'save-next';

type KeyEventLike = Pick<
  KeyboardEvent,
  | 'altKey'
  | 'ctrlKey'
  | 'defaultPrevented'
  | 'isComposing'
  | 'key'
  | 'metaKey'
  | 'target'
>;

const TYPING_TAGS = new Set(['INPUT', 'SELECT', 'TEXTAREA']);
/** 这些容器里的方向键属于控件本身（下拉、日期、数字框） */
const KEY_OWNER_SELECTOR =
  '.ant-select, .ant-picker, .ant-input-number, [role="listbox"], [role="combobox"]';
/** 弹窗里的按键一律交给弹窗（含 Ctrl + Enter） */
const DIALOG_SELECTOR = '.ant-modal-root, [role="dialog"]';

function closest(target: EventTarget | null, selector: string) {
  if (!target || typeof (target as Element).closest !== 'function') {
    return false;
  }
  return Boolean((target as Element).closest(selector));
}

export function isTypingTarget(target: EventTarget | null): boolean {
  if (!target || typeof (target as Element).closest !== 'function') {
    return false;
  }
  const element = target as HTMLElement;
  if (TYPING_TAGS.has(element.tagName) || element.isContentEditable) {
    return true;
  }
  return closest(element, KEY_OWNER_SELECTOR);
}

export function resolveGradingKey(
  event: KeyEventLike,
): GradingKeyAction | undefined {
  if (event.defaultPrevented || event.isComposing) return undefined;
  if (closest(event.target, DIALOG_SELECTOR)) return undefined;
  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
    return 'save-next';
  }
  if (event.ctrlKey || event.metaKey || event.altKey) return undefined;
  if (isTypingTarget(event.target)) return undefined;
  switch (event.key) {
    case 'ArrowDown':
    case 'j':
    case 'J': {
      return 'next';
    }
    case 'ArrowUp':
    case 'k':
    case 'K': {
      return 'prev';
    }
    default: {
      return undefined;
    }
  }
}

/** 页面可见时注册；被缓存的标签页切走后不再响应 */
export function useGradingKeys(onAction: (action: GradingKeyAction) => void) {
  function handleKeydown(event: KeyboardEvent) {
    const action = resolveGradingKey(event);
    if (!action) return;
    event.preventDefault();
    onAction(action);
  }
  let active = false;
  function attach() {
    if (active) return;
    window.addEventListener('keydown', handleKeydown);
    active = true;
  }
  function detach() {
    if (!active) return;
    window.removeEventListener('keydown', handleKeydown);
    active = false;
  }
  onMounted(attach);
  onActivated(attach);
  onDeactivated(detach);
  onBeforeUnmount(detach);
  return { handleKeydown };
}
