<script lang="ts" setup>
import type { StudentNewsApi } from '#/api/edu/student/news';

import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Button, Pagination, Spin } from 'ant-design-vue';

import { getStudentNews, getStudentNewsPage } from '#/api/edu/student/news';
import { EmptyState } from '#/components/empty-state';

const router = useRouter();
const loading = ref(false);
const list = ref<StudentNewsApi.NewsItem[]>([]);
const total = ref(0);

const query = reactive<StudentNewsApi.NewsPageReqVO>({
  pageNo: 1,
  pageSize: 10,
});

function toSnippet(text?: string) {
  if (!text) return '暂无摘要';
  const normalized = text.replaceAll(/\s+/g, ' ').trim();
  if (!normalized) return '暂无摘要';
  return normalized.length > 120
    ? `${normalized.slice(0, 120)}...`
    : normalized;
}

async function loadList() {
  loading.value = true;
  try {
    const result = await getStudentNewsPage(query);
    list.value = result.list ?? [];
    total.value = result.total ?? 0;
    await hydrateImageUrls();
  } finally {
    loading.value = false;
  }
}

async function hydrateImageUrls() {
  const tasks = list.value
    .filter((item) => !item.imageUrls || item.imageUrls.length === 0)
    .map(async (item) => {
      try {
        const detail = await getStudentNews(item.id);
        item.imageUrls = extractImageUrlsFromHtml(detail.contentHtml);
      } catch {
        item.imageUrls = [];
      }
    });
  await Promise.all(tasks);
}

function extractImageUrlsFromHtml(contentHtml?: string) {
  if (!contentHtml) return [];
  const wrapper = document.createElement('div');
  wrapper.innerHTML = contentHtml;
  const urls = [...wrapper.querySelectorAll('img[src]')]
    .map((img) => (img.getAttribute('src') || '').trim())
    .filter(Boolean);
  return [...new Set(urls)].slice(0, 5);
}

function handlePageChange(page: number, pageSize: number) {
  query.pageNo = page;
  query.pageSize = pageSize;
  loadList();
}

function handleOpenDetail(item: StudentNewsApi.NewsItem) {
  router.push({
    path: `/student/news/detail/${item.id}`,
  });
}

/** 链接地址（支持新标签页打开）；点击仍走 handleOpenDetail */
function detailHref(item: StudentNewsApi.NewsItem) {
  return router.resolve({ path: `/student/news/detail/${item.id}` }).href;
}

onMounted(() => {
  loadList();
});
</script>

<template>
  <Page
    auto-content-height
    description="周师要闻与特别关注，点开标题查看全文。"
    title="校园资讯"
  >
    <template #extra>
      <Button :loading="loading" @click="loadList">刷新</Button>
    </template>

    <Spin :spinning="loading" tip="正在读取校园资讯…">
      <div v-if="list.length > 0" class="page-stack">
        <ul class="news-list surface-panel">
          <li v-for="item in list" :key="item.id">
            <a
              :href="detailHref(item)"
              class="news-item"
              @click.prevent="handleOpenDetail(item)"
            >
              <div class="news-item__body">
                <div class="news-item__meta">
                  <span>{{ item.sourceName }}</span>
                  <span aria-hidden="true">·</span>
                  <span>{{ item.publishDate || '-' }}</span>
                  <template v-if="item.author">
                    <span aria-hidden="true">·</span>
                    <span>作者：{{ item.author }}</span>
                  </template>
                </div>
                <h3 class="news-item__title">{{ item.title }}</h3>
                <p class="news-item__snippet">
                  {{ toSnippet(item.contentText) }}
                </p>
              </div>
              <div v-if="item.imageUrls?.length" class="news-item__thumb">
                <img
                  :src="item.imageUrls[0]"
                  alt="资讯配图"
                  class="news-item__img"
                  loading="lazy"
                />
                <span v-if="item.imageUrls.length > 1" class="news-item__count">
                  {{ item.imageUrls.length }} 张图
                </span>
              </div>
            </a>
          </li>
        </ul>

        <div class="flex justify-end">
          <Pagination
            :current="query.pageNo"
            :page-size="query.pageSize"
            :show-size-changer="true"
            :show-total="(value) => `共 ${value} 条`"
            :total="total"
            @change="handlePageChange"
          />
        </div>
      </div>

      <EmptyState
        v-else-if="!loading"
        description="管理员抓取校园资讯后会显示在这里，稍后刷新查看。"
        title="还没有校园资讯"
      />
    </Spin>
  </Page>
</template>

<style scoped>
.news-list {
  padding: 0;
  margin: 0;
  overflow: hidden;
  list-style: none;
}

.news-list > li + li {
  border-top: 1px solid var(--nus-border-subtle);
}

.news-item {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  padding: 20px 24px;
  color: var(--nus-text);
  transition: var(--nus-transition-colors);
}

.news-item:hover {
  color: var(--nus-text);
  background: var(--nus-bg-hover);
}

.news-item:focus-visible {
  outline: 2px solid var(--nus-primary);
  outline-offset: -2px;
}

.news-item__body {
  flex: 1;
  min-width: 0;
}

.news-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text-tertiary);
}

.news-item__title {
  display: -webkit-box;
  margin: 6px 0 0;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: var(--nus-fs-heading);
  font-weight: var(--nus-fw-semibold);
  line-height: var(--nus-lh-heading);
  color: var(--nus-text);
  -webkit-box-orient: vertical;
}

.news-item:hover .news-item__title {
  color: var(--nus-primary);
}

.news-item__snippet {
  display: -webkit-box;
  margin: 6px 0 0;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: var(--nus-fs-body);
  line-height: var(--nus-lh-body);
  color: var(--nus-text-secondary);
  -webkit-box-orient: vertical;
}

.news-item__thumb {
  position: relative;
  flex: none;
  width: 176px;
  height: 112px;
  overflow: hidden;
  background: var(--nus-bg-sunken);
  border: 1px solid var(--nus-border-subtle);
  border-radius: var(--nus-radius);
}

.news-item__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.news-item__count {
  position: absolute;
  right: 6px;
  bottom: 6px;
  padding: 0 6px;
  font-size: var(--nus-fs-caption);
  line-height: var(--nus-lh-caption);
  color: var(--nus-text);
  background: var(--nus-bg-elevated);
  border-radius: var(--nus-radius-sm);
}

@media (width <= 768px) {
  .news-item {
    flex-direction: column-reverse;
    gap: 12px;
    padding: 16px;
  }

  .news-item__thumb {
    width: 100%;
    height: 160px;
  }
}
</style>
