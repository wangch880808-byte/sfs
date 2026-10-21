<script lang="ts" setup>
import { computed } from 'vue';

import { AuthPageLayout } from '@vben/layouts';
import { preferences } from '@vben/preferences';

import { $t } from '#/locales';

const appName = computed(() => preferences.app.name);
const authLogo = '/logo.png';
const sloganImage = '/logo.svg';
</script>

<template>
  <AuthPageLayout
    :app-name="appName"
    :logo="authLogo"
    :logo-dark="authLogo"
    :page-description="$t('authentication.pageDesc')"
    :page-title="$t('authentication.pageTitle')"
    :slogan-image="sloganImage"
    :toolbar-list="['theme']"
  />
</template>
