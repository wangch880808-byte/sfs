<script lang="ts" setup>
import type { NotificationItem } from '@vben/layouts';

import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

import { AuthenticationLoginExpiredModal } from '@vben/common-ui';
import { useWatermark } from '@vben/hooks';
import { AntdProfileOutlined } from '@vben/icons';
import {
  BasicLayout,
  LockScreen,
  Notification,
  UserDropdown,
} from '@vben/layouts';
import { preferences, updatePreferences } from '@vben/preferences';
import { useAccessStore, useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import { Tooltip } from 'ant-design-vue';

import { getCurrentSemester } from '#/api/edu/semester';
import {
  getUnreadNotifyMessageCount,
  getUnreadNotifyMessageList,
  updateAllNotifyMessageRead,
  updateNotifyMessageRead,
} from '#/api/system/notify/message';
import { $t } from '#/locales';
import { router } from '#/router';
import { useAuthStore } from '#/store';
import { createBackgroundPoller } from '#/utils/background-poller';
import { getNotifyTargetUrl, getNotifyTitle } from '#/utils/notify-message';
import { NOTIFY_UNREAD_CHANGED_EVENT } from '#/utils/notify-unread';
import { resolvePersona } from '#/utils/persona';
import LoginForm from '#/views/_core/authentication/login.vue';

const userStore = useUserStore();
const authStore = useAuthStore();
const accessStore = useAccessStore();
const { destroyWatermark, updateWatermark } = useWatermark();

const notifications = ref<NotificationItem[]>([]);
const unreadCount = ref(0);
const showDot = computed(() => unreadCount.value > 0);
const currentSemesterName = ref('未设置');
const currentSemesterStartDate = ref('');
const currentSemesterEndDate = ref('');
const semesterLoading = ref(false);
const semesterLabel = computed(
  () => `当前学期：${currentSemesterName.value || '未设置'}`,
);
const semesterTooltip = computed(() => {
  if (semesterLoading.value) return '正在读取当前学期…';
  if (!currentSemesterStartDate.value && !currentSemesterEndDate.value)
    return '学期时间未设置';
  return `${currentSemesterStartDate.value || '—'} ~ ${currentSemesterEndDate.value || '—'}`;
});
/** 未读通知数的后台轮询：静默请求，页面在后台时跳过，失败后退避 */
const unreadCountPoller = createBackgroundPoller({
  intervalMs: 30 * 1000,
  task: async () => {
    if (!userStore.userInfo) {
      return;
    }
    unreadCount.value = await getUnreadNotifyMessageCount({
      silentError: true,
    });
  },
});

const menus = computed(() => [
  {
    handler: () => {
      // 学生的个人中心是学生资料页（已从导航移到头像菜单）；兼任教师或管理员的用通用资料页
      const { profileRoute } = resolvePersona(userStore.userRoles);
      router.push({
        name: router.hasRoute(profileRoute) ? profileRoute : 'Profile',
      });
    },
    icon: AntdProfileOutlined,
    text: $t('ui.widgets.profile'),
  },
]);

const avatar = computed(() => {
  return userStore.userInfo?.avatar ?? preferences.app.defaultAvatar;
});

async function handleLogout() {
  await authStore.logout(false);
}

async function handleSemesterLoad() {
  semesterLoading.value = true;
  try {
    const data = await getCurrentSemester();
    currentSemesterName.value = data?.semesterName || '未设置';
    currentSemesterStartDate.value = data?.semesterStartDate || '';
    currentSemesterEndDate.value = data?.semesterEndDate || '';
  } catch {
    currentSemesterName.value = '未设置';
    currentSemesterStartDate.value = '';
    currentSemesterEndDate.value = '';
  } finally {
    semesterLoading.value = false;
  }
}

/** 获得未读消息数（只是角标，失败时保留原值，不弹提示） */
async function handleNotificationGetUnreadCount() {
  await unreadCountPoller.refresh({ force: true });
}

/** 获得消息列表 */
async function handleNotificationGetList() {
  const list = await getUnreadNotifyMessageList();
  notifications.value = list.map((item) => ({
    avatar: preferences.app.defaultAvatar,
    date: formatDateTime(item.createTime) as string,
    isRead: false,
    id: item.id,
    link: getNotifyTargetUrl(item),
    message: item.templateContent,
    title: getNotifyTitle(item),
  }));
}

/** 跳转我的通知 */
function handleNotificationViewAll() {
  router.push({
    name: 'MyNotifyMessage',
  });
}

/** 标记所有已读 */
async function handleNotificationMakeAll() {
  await updateAllNotifyMessageRead();
  unreadCount.value = 0;
  notifications.value = [];
}

/** 清空通知 */
async function handleNotificationClear() {
  await handleNotificationMakeAll();
}

/** 标记单个已读 */
async function handleNotificationRead(item: NotificationItem) {
  if (!item.id) {
    return;
  }
  await updateNotifyMessageRead([item.id]);
  await handleNotificationGetUnreadCount();
  notifications.value = notifications.value.filter((n) => n.id !== item.id);
}

/** 处理通知打开 */
function handleNotificationOpen(open: boolean) {
  if (!open) {
    return;
  }
  void handleNotificationGetList();
  void handleNotificationGetUnreadCount();
}

/** 窗口获得焦点：正常时刷新一次；失败退避期间交给轮询，不额外请求 */
function handleNotificationRefresh() {
  void unreadCountPoller.refresh();
}

/** 页面回到前台：立即刷新一次 */
function handleVisibilityChange() {
  if (!document.hidden) {
    void unreadCountPoller.refresh({ force: true });
  }
}

// ========== 初始化 ==========
onMounted(() => {
  // 顶部使用“当前学期”替换搜索入口
  if (preferences.widget.globalSearch) {
    updatePreferences({ widget: { globalSearch: false } });
  }
  // 首次加载未读数量，之后每 30 秒在后台刷新
  unreadCountPoller.start();
  void handleSemesterLoad();
  window.addEventListener('focus', handleNotificationRefresh);
  document.addEventListener('visibilitychange', handleVisibilityChange);
  window.addEventListener('edu-semester-updated', handleSemesterLoad);
  window.addEventListener(
    NOTIFY_UNREAD_CHANGED_EVENT,
    handleNotificationRefresh,
  );
});

onUnmounted(() => {
  unreadCountPoller.stop();
  window.removeEventListener('focus', handleNotificationRefresh);
  document.removeEventListener('visibilitychange', handleVisibilityChange);
  window.removeEventListener('edu-semester-updated', handleSemesterLoad);
  window.removeEventListener(
    NOTIFY_UNREAD_CHANGED_EVENT,
    handleNotificationRefresh,
  );
});

watch(
  () => ({
    enable: preferences.app.watermark,
    content: preferences.app.watermarkContent,
  }),
  async ({ enable, content }) => {
    if (enable) {
      await updateWatermark({
        content:
          content ||
          `${userStore.userInfo?.id} - ${userStore.userInfo?.nickname}`,
      });
    } else {
      destroyWatermark();
    }
  },
  {
    immediate: true,
  },
);
</script>

<template>
  <BasicLayout @clear-preferences-and-logout="handleLogout">
    <template #user-dropdown>
      <UserDropdown
        :avatar
        :menus
        :text="userStore.userInfo?.nickname"
        :description="userStore.userInfo?.email"
        :tag-text="userStore.userInfo?.username"
        @logout="handleLogout"
      />
    </template>
    <template #notification>
      <Notification
        :dot="showDot"
        :notifications="notifications"
        @clear="handleNotificationClear"
        @make-all="handleNotificationMakeAll"
        @view-all="handleNotificationViewAll"
        @open="handleNotificationOpen"
        @read="handleNotificationRead"
      />
    </template>
    <template #header-right-50>
      <Tooltip :title="semesterTooltip">
        <div
          class="mr-2 max-w-[260px] cursor-default truncate rounded-full border border-border/70 px-3 py-1 text-xs text-foreground/85"
        >
          {{ semesterLabel }}
        </div>
      </Tooltip>
    </template>
    <template #extra>
      <AuthenticationLoginExpiredModal
        v-model:open="accessStore.loginExpired"
        :avatar
      >
        <LoginForm />
      </AuthenticationLoginExpiredModal>
    </template>
    <template #lock-screen>
      <LockScreen :avatar @to-login="handleLogout" />
    </template>
  </BasicLayout>
</template>
