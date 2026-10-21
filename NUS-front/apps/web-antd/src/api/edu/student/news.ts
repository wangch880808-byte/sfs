import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace StudentNewsApi {
  export interface NewsSource {
    sourceCode: string;
    sourceName: string;
  }

  export interface NewsPageReqVO extends PageParam {
    sourceCode?: string;
    keyword?: string;
  }

  export interface NewsItem {
    id: number;
    sourceCode: string;
    sourceName: string;
    title: string;
    author?: string;
    publishDate?: string;
    articleUrl?: string;
    contentText?: string;
    imageUrls?: string[];
    lastCrawledAt?: string;
  }

  export interface NewsDetail {
    id: number;
    sourceCode: string;
    sourceName: string;
    title: string;
    author?: string;
    publishDate?: string;
    articleUrl?: string;
    contentText?: string;
    contentHtml?: string;
    rawMeta?: string;
    firstCrawledAt?: string;
    lastCrawledAt?: string;
  }
}

/** 获取资讯来源列表 */
export function getStudentNewsSources() {
  return requestClient.get<StudentNewsApi.NewsSource[]>('/edu/student/news/sources');
}

/** 获取资讯分页 */
export function getStudentNewsPage(params: StudentNewsApi.NewsPageReqVO) {
  return requestClient.get<PageResult<StudentNewsApi.NewsItem>>(
    '/edu/student/news/page',
    { params },
  );
}

/** 获取资讯详情 */
export function getStudentNews(id: number) {
  return requestClient.get<StudentNewsApi.NewsDetail>(
    `/edu/student/news/get?id=${id}`,
  );
}
