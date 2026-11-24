import type { AxiosProgressEvent } from '#/api/infra/file';

import { uploadFile as uploadInfraFile } from '#/api/infra/file';
import { requestClient } from '#/api/request';
import {
  inferResourceTypeCode,
  normalizeResourceName,
} from '#/utils/course-resource';

/** 课程资源可能是较大的课件或视频，单独放宽上传超时（默认 30 秒） */
const COURSE_RESOURCE_UPLOAD_TIMEOUT = 30 * 60 * 1000;

export namespace EduCourseResourceApi {
  /** 课程资源 */
  export interface CourseResource {
    id?: number;
    resourceName: string;
    courseId: number;
    fileUrl: string;
    fileType: string;
    fileSize: number;
    standardAudioUrl?: string;
    createTime?: Date | string;
    /** 审核状态：0 待审核，1 通过，2 拒绝 */
    reviewStatus?: number;
    /** 审核原因（驳回时填写） */
    reviewReason?: string;
    reviewerId?: number;
    reviewedAt?: Date | string;
  }

  /** 课程资源创建请求 */
  export interface CourseResourceCreateReqVO {
    resourceName: string;
    courseId: number;
    fileUrl: string;
    fileType: string;
    fileSize: number;
    standardAudioUrl?: string;
  }

  /** 课程资源审核请求 */
  export interface CourseResourceReviewReqVO {
    approved: boolean;
    reason?: string;
  }
}

/** 新增课程资源（与后端 JSON 接口一致） */
export function createCourseResource(
  data: EduCourseResourceApi.CourseResourceCreateReqVO,
  options?: { silentError?: boolean },
) {
  return requestClient.post<number>(
    '/edu/course-resource/upload',
    data,
    options,
  );
}

/** 查询课程资源分页 */
export function getCourseResourcePage(params: {
  courseId: number;
  fileType?: string;
  pageNo?: number;
  pageSize?: number;
  resourceName?: string;
}) {
  return requestClient.get<{
    list: EduCourseResourceApi.CourseResource[];
    total: number;
  }>('/edu/course-resource/list', {
    params: { pageNo: 1, pageSize: 200, ...params },
  });
}

/** 上传课程资源（silentError：出错时不弹全局提示，由调用方按文件名自行提示） */
export async function uploadCourseResource(
  courseId: number,
  file: File,
  resourceName?: string,
  onUploadProgress?: AxiosProgressEvent,
  options?: { silentError?: boolean },
) {
  const uploadResponse = await uploadInfraFile(
    { file, directory: `edu/course-resource/${courseId}` },
    onUploadProgress,
    COURSE_RESOURCE_UPLOAD_TIMEOUT,
    options,
  );
  const fileUrl = uploadResponse?.url || uploadResponse?.data || uploadResponse;
  if (!fileUrl || typeof fileUrl !== 'string') {
    throw new Error('服务器没有返回文件地址。稍后重新上传。');
  }

  const resolvedName = normalizeResourceName(resourceName || file.name);
  const fileType = inferResourceTypeCode(file);
  const fileSize = Number(file.size || 0);
  const resourceId = await createCourseResource(
    {
      courseId,
      resourceName: resolvedName,
      fileUrl,
      fileType,
      fileSize,
    },
    options,
  );

  return {
    id: Number(resourceId || 0) || undefined,
    courseId,
    resourceName: resolvedName,
    fileSize,
    fileType,
    fileUrl,
  } satisfies EduCourseResourceApi.CourseResource;
}

/** 删除课程资源 */
export function deleteCourseResource(id: number) {
  return requestClient.delete(`/edu/course-resource/delete?id=${id}`);
}

/** 查询课程资源列表 */
export async function getCourseResourceList(courseId: number) {
  const result = await getCourseResourcePage({ courseId });
  return result.list ?? [];
}

/** 下载课程资源 */
export function downloadCourseResource(id: number) {
  return requestClient.get<EduCourseResourceApi.CourseResource>(
    `/edu/course-resource/download?id=${id}`,
  );
}

/** 获取课程资源文本预览 */
export function getCourseResourcePreviewText(id: number) {
  return requestClient.get<string>(
    `/edu/course-resource/preview-text?id=${id}`,
  );
}

/** 查询待审核课程资源分页（reviewStatus=0） */
export function getCourseResourceReviewPage(params: {
  courseId?: number;
  fileType?: string;
  pageNo?: number;
  pageSize?: number;
  resourceName?: string;
}) {
  return requestClient.get<{
    list: EduCourseResourceApi.CourseResource[];
    total: number;
  }>('/edu/course-resource/review/page', {
    params: { pageNo: 1, pageSize: 20, ...params },
  });
}

/** 审核课程资源（通过 / 驳回） */
export function reviewCourseResource(
  id: number,
  data: EduCourseResourceApi.CourseResourceReviewReqVO,
) {
  return requestClient.post<boolean>(`/edu/course-resource/${id}/review`, data);
}
