import { onBeforeUnmount, onMounted, ref } from 'vue';

/** 当前时间，每分钟更新一次：“刚刚”“N 分钟前”“已等 N 小时”放久了也不会过时 */
export function useNow(intervalMs = 60 * 1000) {
  const now = ref(Date.now());
  let timer: ReturnType<typeof setInterval> | undefined;
  onMounted(() => {
    timer = setInterval(() => {
      now.value = Date.now();
    }, intervalMs);
  });
  onBeforeUnmount(() => {
    if (timer) clearInterval(timer);
  });
  return now;
}
