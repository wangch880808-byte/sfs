import { onBeforeUnmount, onMounted } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';

/**
 * 有未保存的内容时拦住离开：站内跳转先问一句（confirmLeave 返回 true 才放行），
 * 关闭、刷新标签页时交给浏览器自带的提示。只在批改页使用。
 */
export function useLeaveGuard(
  shouldBlock: () => boolean,
  confirmLeave: () => Promise<boolean>,
) {
  onBeforeRouteLeave(async () => {
    if (!shouldBlock()) return true;
    return await confirmLeave();
  });

  function handleBeforeUnload(event: BeforeUnloadEvent) {
    if (!shouldBlock()) return;
    event.preventDefault();
    // 旧版浏览器需要设置 returnValue 才会弹出提示
    event.returnValue = '';
  }

  onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload));
  onBeforeUnmount(() =>
    window.removeEventListener('beforeunload', handleBeforeUnload),
  );

  return { handleBeforeUnload };
}
