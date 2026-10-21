<script lang="ts" setup>
import type { StudentNewsApi } from '#/api/edu/student/news';

import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Button, Spin } from 'ant-design-vue';

import { getStudentNews } from '#/api/edu/student/news';
import { EmptyState } from '#/components/empty-state';

import { normalizeNewsHtml } from './content';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const detail = ref<StudentNewsApi.NewsDetail>();

const newsId = computed(() => Number(route.params.id));

/** 页头说明：来源、作者、发布日期与抓取时间 */
const metaText = computed(() => {
  if (!detail.value) return '';
  return [
    detail.value.sourceName,
    `作者：${detail.value.author || '-'}`,
    `发布日期：${detail.value.publishDate || '-'}`,
    `抓取时间：${detail.value.lastCrawledAt || '-'}`,
  ]
    .filter(Boolean)
    .join(' · ');
});

/** 正文去掉写死的颜色与字号后再交给 v-dompurify-html 过滤 */
const contentHtml = computed(() =>
  normalizeNewsHtml(detail.value?.contentHtml),
);

async function loadDetail() {
  if (!newsId.value) {
    router.back();
    return;
  }
  loading.value = true;
  try {
    detail.value = await getStudentNews(newsId.value);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadDetail();
});
</script>

<template>
  <Page
    :description="metaText"
    :title="detail?.title || '资讯详情'"
    auto-content-height
  >
    <template #extra>
      <Button @click="router.back()">返回</Button>
    </template>

    <Spin :spinning="loading" tip="正在读取资讯…">
      <article v-if="detail" class="news-article surface-panel">
        <div
          v-if="contentHtml"
          v-dompurify-html="contentHtml"
          class="news-article__content"
        ></div>
        <div v-else class="news-article__content news-article__text">
          {{ detail.contentText || '暂无正文' }}
        </div>
      </article>

      <EmptyState
        v-else-if="!loading"
        description="它可能已被删除，返回列表查看其他资讯。"
        title="找不到这条资讯"
      >
        <template #action>
          <Button @click="router.back()">返回</Button>
        </template>
      </EmptyState>
    </Spin>
  </Page>
</template>

<style scoped>
.news-article {
  max-width: 800px;
  padding: 32px 40px;
}

.news-article__content {
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text);
  overflow-wrap: anywhere;
}

.news-article__text {
  white-space: pre-wrap;
}

.news-article__content :deep(p) {
  margin: 0 0 12px;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
}

.news-article__content :deep(:is(h1, h2, h3, h4, h5, h6)) {
  margin: 24px 0 8px;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
}

.news-article__content :deep(:is(ul, ol)) {
  padding-left: 20px;
  margin: 0 0 12px;
}

.news-article__content :deep(ul) {
  list-style: disc;
}

.news-article__content :deep(ol) {
  list-style: decimal;
}

.news-article__content :deep(a) {
  color: var(--nus-primary);
}

.news-article__content :deep(img) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 16px auto;
  border-radius: var(--nus-radius);
}

.news-article__content :deep(table) {
  width: 100%;
  margin: 0 0 12px;
  border-collapse: collapse;
}

.news-article__content :deep(td),
.news-article__content :deep(th) {
  padding: 6px 8px;
  border: 1px solid var(--nus-border-subtle);
}

.news-article__content :deep(th) {
  font-weight: var(--nus-fw-semibold);
  background: var(--nus-bg-sunken);
}

@media (width <= 768px) {
  .news-article {
    padding: 20px 16px;
  }
}
</style>
