<script setup lang="tsx">
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SystemSocialUserApi } from '#/api/system/social/user';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { confirm, VbenLoading } from '@vben/common-ui';
import { DICT_TYPE, SystemUserSocialTypeEnum } from '@vben/constants';
import { getDictLabel } from '@vben/hooks';
import { SvgDingDingIcon } from '@vben/icons';
import { getUrlValue } from '@vben/utils';

import { Button, message, Modal, Spin } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  getWechatMiniProgramConfig,
  socialAuthRedirect,
} from '#/api/core/auth';
import {
  createWechatBindQr,
  getBindSocialUserList,
  getWechatBinding,
  getWechatBindQrStatus,
  socialBind,
  socialUnbind,
  unbindWechat,
} from '#/api/system/social/user';
import { EmptyState } from '#/components/empty-state';
import { StatusTag } from '#/components/status-tag';
import { getSocialBindingMeta } from '#/utils/status-infra-ai';

const emit = defineEmits<{
  (e: 'update:activeName', v: string): void;
}>();

const route = useRoute();
const wechatEnabled = ref(false);
const wechatBinding = ref<SystemSocialUserApi.WechatBinding>({ bound: false });
const wechatBindModalOpen = ref(false);
const wechatBindLoading = ref(false);
const wechatBindError = ref('');
const wechatBindState = ref<'CONFIRMED' | 'SCANNED' | 'WAITING'>('WAITING');
const wechatBindQrImage = ref('');
const wechatBindTicket = ref('');
const wechatBindBrowserToken = ref('');
let wechatBindPollTimer: number | undefined;
/** 已经绑定的平台 */
const bindList = ref<SystemSocialUserApi.SocialUser[]>([]);
const allBindList = computed<any[]>(() => {
  return Object.values(SystemUserSocialTypeEnum).map((social) => {
    const socialUser = bindList.value.find((item) => item.type === social.type);
    return {
      ...social,
      socialUser,
    };
  });
});

function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'type',
      title: '绑定平台',
      minWidth: 100,
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.SYSTEM_SOCIAL_TYPE },
      },
    },
    {
      field: 'openid',
      title: '标识',
      minWidth: 180,
    },
    {
      field: 'nickname',
      title: '名称',
      minWidth: 180,
    },
    {
      field: 'operation',
      title: '操作',
      minWidth: 80,
      align: 'center',
      fixed: 'right',
      slots: {
        default: ({ row }: { row: SystemSocialUserApi.SocialUser }) => {
          return (
            <Button
              danger
              onClick={() => onUnbind(row)}
              size="small"
              type="link"
            >
              解绑
            </Button>
          );
        },
      },
    },
  ];
}

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: useGridColumns(),
    minHeight: 0,
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async () => {
          bindList.value = await getBindSocialUserList();
          return bindList.value;
        },
      },
    },
    rowConfig: {
      keyField: 'id',
    },
    pagerConfig: {
      enabled: false,
    },
    toolbarConfig: {
      enabled: false,
    },
  },
});

/** 解绑账号 */
function onUnbind(row: SystemSocialUserApi.SocialUser) {
  const platform = getDictLabel(DICT_TYPE.SYSTEM_SOCIAL_TYPE, row.type);
  confirm({
    cancelText: '返回',
    confirmText: '解绑',
    content: `解绑后不能再用这个${platform}账号登录平台，可以随时重新绑定。`,
    icon: 'warning',
    title: `解绑${platform}账号？`,
  }).then(async () => {
    await socialUnbind({ type: row.type, openid: row.openid });
    // 提示成功
    message.success(`已解绑${platform}账号`);
    await gridApi.reload();
  });
}

/** 绑定账号（跳转授权页面） */
async function onBind(bind: any) {
  const type = bind.type;
  if (type <= 0) {
    return;
  }
  try {
    // 计算 redirectUri（标准 query 形式，避免 type%3D20 这类非标准参数）
    const redirectUri = `${location.origin}/profile?${new URLSearchParams({
      type: String(type),
    }).toString()}`;

    // 进行跳转
    window.location.href = await socialAuthRedirect(type, redirectUri);
  } catch (error) {
    console.error('社交绑定处理失败:', error);
  }
}

async function loadWechatBinding() {
  wechatBinding.value = await getWechatBinding();
}

async function loadWechatConfig() {
  try {
    const config = await getWechatMiniProgramConfig({ silentError: true });
    wechatEnabled.value = config.enabled;
    if (config.enabled) {
      await loadWechatBinding();
    }
  } catch {
    wechatEnabled.value = false;
  }
}

function stopWechatBindPolling() {
  if (wechatBindPollTimer !== undefined) {
    window.clearInterval(wechatBindPollTimer);
    wechatBindPollTimer = undefined;
  }
}

async function pollWechatBindStatus() {
  if (!wechatBindTicket.value || !wechatBindBrowserToken.value) {
    return;
  }
  try {
    const status = await getWechatBindQrStatus({
      browserToken: wechatBindBrowserToken.value,
      ticket: wechatBindTicket.value,
    });
    wechatBindState.value = status.state;
    if (status.state === 'CONFIRMED') {
      stopWechatBindPolling();
      await loadWechatBinding();
      message.success('微信小程序绑定成功');
      wechatBindModalOpen.value = false;
    }
  } catch (error) {
    stopWechatBindPolling();
    wechatBindError.value =
      (error as Error)?.message || '绑定码已失效，请重新生成';
  }
}

async function openWechatBind() {
  stopWechatBindPolling();
  wechatBindModalOpen.value = true;
  wechatBindLoading.value = true;
  wechatBindError.value = '';
  wechatBindState.value = 'WAITING';
  wechatBindQrImage.value = '';
  try {
    const result = await createWechatBindQr();
    wechatBindQrImage.value = result.qrCodeDataUrl;
    wechatBindTicket.value = result.ticket;
    wechatBindBrowserToken.value = result.browserToken;
    wechatBindPollTimer = window.setInterval(pollWechatBindStatus, 1500);
  } catch (error) {
    wechatBindError.value =
      (error as Error)?.message || '绑定码没有生成出来，请稍后重试';
  } finally {
    wechatBindLoading.value = false;
  }
}

function closeWechatBindModal() {
  stopWechatBindPolling();
  wechatBindModalOpen.value = false;
}

function onUnbindWechat() {
  confirm({
    cancelText: '返回',
    confirmText: '解绑',
    content: '解绑后不能再用微信扫码登录平台，可以随时重新绑定。',
    icon: 'warning',
    title: '解绑微信小程序？',
  }).then(async () => {
    await unbindWechat();
    await loadWechatBinding();
    message.success('微信小程序已解绑');
  });
}

/** 监听路由变化，处理社交绑定回调 */
async function bindSocial() {
  // 社交绑定
  const type = Number(getUrlValue('type'));
  const code = route.query.code as string;
  const state = route.query.state as string;
  if (!code) {
    return;
  }
  await socialBind({ type, code, state });
  // 提示成功
  message.success('绑定成功');
  emit('update:activeName', 'userSocial');
  await gridApi.reload();
  // 清理 URL 参数，避免刷新重复触发
  window.history.replaceState({}, '', location.pathname);
}

/** 初始化 */
onMounted(async () => {
  await Promise.all([bindSocial(), loadWechatConfig()]);
});

onBeforeUnmount(stopWechatBindPolling);
</script>

<template>
  <div class="user-social">
    <!-- 微信小程序 -->
    <section v-if="wechatEnabled" class="user-social__section">
      <div class="user-social__row">
        <div class="min-w-0">
          <div class="user-social__title">
            <span>微信扫码登录</span>
            <StatusTag v-bind="getSocialBindingMeta(wechatBinding.bound)" />
          </div>
          <p class="user-social__desc">
            <template v-if="wechatBinding.bound">
              微信标识：{{
                wechatBinding.openidMasked
              }}。所有账号都可以扫码登录，学生账号还会开放实训任务功能。
            </template>
            <template v-else>
              用微信扫描绑定码，在小程序里确认后，就可以扫码登录平台。
            </template>
          </p>
        </div>
        <Button
          v-if="!wechatBinding.bound"
          type="primary"
          @click="openWechatBind"
        >
          扫码绑定
        </Button>
        <Button v-else danger @click="onUnbindWechat">解绑微信</Button>
      </div>
    </section>

    <!-- 已绑定的第三方账号 -->
    <section class="user-social__section">
      <h3 class="user-social__heading">已绑定的账号</h3>
      <Grid>
        <template #loading>
          <VbenLoading spinning text="正在读取已绑定的账号…" />
        </template>
        <template #empty>
          <EmptyState
            compact
            description="在下方选择平台完成绑定后，会显示在这里。"
            title="还没有绑定第三方账号"
          />
        </template>
      </Grid>
    </section>

    <!-- 可以绑定的平台 -->
    <section class="user-social__section">
      <h3 class="user-social__heading">可以绑定的平台</h3>
      <div class="user-social__platforms">
        <div
          v-for="item in allBindList"
          :key="item.type"
          class="user-social__platform"
        >
          <SvgDingDingIcon
            v-if="item.type === SystemUserSocialTypeEnum.DINGTALK.type"
            :aria-label="item.title"
            class="size-8 shrink-0"
          />
          <div class="min-w-0 flex-1">
            <div class="user-social__platform-name">
              {{ getDictLabel(DICT_TYPE.SYSTEM_SOCIAL_TYPE, item.type) }}
            </div>
            <div class="user-social__platform-desc">
              <template v-if="item.socialUser">
                {{ item.socialUser?.nickname || '已绑定' }}
              </template>
              <template v-else>
                绑定{{
                  getDictLabel(DICT_TYPE.SYSTEM_SOCIAL_TYPE, item.type)
                }}账号后可以扫码登录
              </template>
            </div>
          </div>
          <StatusTag
            v-if="item.socialUser"
            v-bind="getSocialBindingMeta(true)"
          />
          <Button v-else size="small" @click="onBind(item)">绑定</Button>
        </div>
      </div>
    </section>

    <Modal
      :footer="null"
      :open="wechatBindModalOpen"
      title="绑定微信小程序"
      @cancel="closeWechatBindModal"
    >
      <div class="user-social__qr">
        <Spin v-if="wechatBindLoading" tip="正在生成绑定码…" />
        <div v-else-if="wechatBindError" class="text-center">
          <p class="user-social__qr-error">{{ wechatBindError }}</p>
          <Button @click="openWechatBind">重新生成</Button>
        </div>
        <div v-else-if="wechatBindState === 'SCANNED'" class="text-center">
          <p class="user-social__qr-title">已扫码</p>
          <p class="user-social__qr-desc">请在小程序里确认绑定当前平台账号</p>
        </div>
        <template v-else>
          <img
            v-if="wechatBindQrImage"
            :src="wechatBindQrImage"
            alt="微信小程序绑定码"
            class="user-social__qr-image"
          />
          <p class="user-social__qr-desc">用微信扫一扫，打开小程序完成绑定</p>
        </template>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
/* 社交绑定（V1.0.1.6 界面规范）：卡片里不再套卡片，分区之间用分隔线；状态用圆点＋文字 */
.user-social__section + .user-social__section {
  padding-top: var(--nus-space-5);
  margin-top: var(--nus-space-5);
  border-top: 1px solid var(--nus-border-subtle);
}

.user-social__row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--nus-space-4);
  align-items: center;
  justify-content: space-between;
}

.user-social__title {
  display: flex;
  gap: var(--nus-space-2);
  align-items: center;
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  color: var(--nus-text);
}

.user-social__desc {
  margin: var(--nus-space-1) 0 0;
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.user-social__heading {
  margin: 0 0 var(--nus-space-3);
  font-size: var(--nus-fs-body);
  font-weight: var(--nus-fw-medium);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.user-social__platforms {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--nus-space-3);
}

.user-social__platform {
  display: flex;
  gap: var(--nus-space-3);
  align-items: center;
  padding: var(--nus-space-3) var(--nus-space-4);
  background: var(--nus-bg-sunken);
  border-radius: var(--nus-radius);
}

.user-social__platform-name {
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
}

.user-social__platform-desc {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
  white-space: nowrap;
}

.user-social__qr {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  text-align: center;
}

.user-social__qr-image {
  width: 240px;
  height: 240px;
  object-fit: contain;
}

.user-social__qr-title {
  margin: 0;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  color: var(--nus-text);
}

.user-social__qr-desc {
  margin: var(--nus-space-3) 0 0;
  font-size: var(--nus-fs-dense);
  color: var(--nus-text-tertiary);
}

.user-social__qr-error {
  margin: 0 0 var(--nus-space-3);
  font-size: var(--nus-fs-dense);
  color: var(--nus-danger);
}
</style>
