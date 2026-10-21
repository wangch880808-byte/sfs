<script setup lang="ts">
import type { SystemUserProfileApi } from '#/api/system/user/profile';

import { computed } from 'vue';

import { preferences } from '@vben/preferences';
import { formatDateTime } from '@vben/utils';

import { Descriptions, DescriptionsItem, Tooltip } from 'ant-design-vue';

import { updateUserProfile } from '#/api/system/user/profile';
import { CropperAvatar } from '#/components/cropper';
import { useUpload } from '#/components/upload/use-upload';

const props = defineProps<{
  profile?: SystemUserProfileApi.UserProfileRespVO;
}>();

const emit = defineEmits<{
  (e: 'success'): void;
}>();

const avatar = computed(
  () => props.profile?.avatar || preferences.app.defaultAvatar,
);

async function handelUpload({
  file,
  filename,
}: {
  file: Blob;
  filename: string;
}) {
  // 1. 上传头像，获取 URL
  const { httpRequest } = useUpload();
  // 将 Blob 转换为 File
  const fileObj = new File([file], filename, { type: file.type });
  const avatar = await httpRequest(fileObj);
  // 2. 更新用户头像
  await updateUserProfile({ avatar });
}
</script>

<template>
  <div v-if="profile" class="profile-user">
    <div class="profile-user__head">
      <Tooltip title="点击更换头像">
        <CropperAvatar
          :show-btn="false"
          :upload-api="handelUpload"
          :value="avatar"
          :width="96"
          @change="emit('success')"
        />
      </Tooltip>
      <div class="profile-user__name">{{ profile.nickname }}</div>
      <div class="profile-user__account">账号 {{ profile.username }}</div>
    </div>
    <Descriptions :column="1" class="profile-user__info" size="small">
      <DescriptionsItem label="所属角色">
        {{ profile.roles.map((role) => role.name).join('、') || '-' }}
      </DescriptionsItem>
      <DescriptionsItem label="手机号码">
        <span v-if="profile.mobile">{{ profile.mobile }}</span>
        <span v-else class="profile-user__empty">未填写</span>
      </DescriptionsItem>
      <DescriptionsItem label="用户邮箱">
        <span v-if="profile.email">{{ profile.email }}</span>
        <span v-else class="profile-user__empty">未填写</span>
      </DescriptionsItem>
      <DescriptionsItem label="所属组织">
        {{ profile.dept?.name || '-' }}
      </DescriptionsItem>
      <DescriptionsItem label="所属岗位">
        {{
          profile.posts && profile.posts.length > 0
            ? profile.posts.map((post) => post.name).join('、')
            : '-'
        }}
      </DescriptionsItem>
      <DescriptionsItem label="创建时间">
        {{ formatDateTime(profile.createTime) }}
      </DescriptionsItem>
      <DescriptionsItem label="登录时间">
        {{ formatDateTime(profile.loginDate) }}
      </DescriptionsItem>
    </Descriptions>
  </div>
</template>

<style scoped>
.profile-user__head {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-bottom: var(--nus-space-5);
  margin-bottom: var(--nus-space-4);
  border-bottom: 1px solid var(--nus-border-subtle);
}

.profile-user__name {
  margin-top: var(--nus-space-3);
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
}

.profile-user__account {
  font-size: var(--nus-fs-dense);
  line-height: var(--nus-lh-dense);
  color: var(--nus-text-tertiary);
}

.profile-user__info :deep(.ant-descriptions-item-label) {
  width: 88px;
  color: var(--nus-text-secondary);
}

.profile-user__info :deep(.ant-descriptions-item-content) {
  font-variant-numeric: tabular-nums;
  color: var(--nus-text);
}

.profile-user__empty {
  color: var(--nus-text-tertiary);
}
</style>
