import type { PageResult } from '@vben/request';

import { LONG_REQUEST_TIMEOUT, requestClient } from '#/api/request';

export namespace EduDataExchangeApi {
  export type BatchStatus =
    | 'CANCELLED'
    | 'CONFIRMING'
    | 'FAILED'
    | 'PREVIEW_INVALID'
    | 'PREVIEW_READY'
    | 'SUCCESS';

  export interface ImportBatch {
    batchNo: string;
    bizType: string;
    templateVersion: string;
    sourceFileName: string;
    status: BatchStatus;
    totalCount: number;
    validCount: number;
    invalidCount: number;
    insertedCount: number;
    updatedCount: number;
    skippedCount: number;
    expireTime?: string;
    confirmTime?: string;
    failureReason?: string;
    hasErrorFile: boolean;
    initialPasswordMode?: 'FIXED' | 'USERNAME';
    defaultInitialPassword?: string;
    createTime: string;
    /** 跳过行按原因计数（成绩册：UNCHANGED、EMPTY 等）；业务没有细分原因时不返回 */
    skipReasonCounts?: Record<string, number>;
  }

  export interface ImportItem {
    sheetName: string;
    rowNumber: number;
    businessKey?: string;
    normalizedData?: string;
    action: 'INSERT' | 'SKIP' | 'UPDATE';
    validationStatus: 'INVALID' | 'VALID';
    validationErrors?: string;
    beforeData?: string;
    afterData?: string;
  }
}

/** 大文件预检逐行核对，几百行时可能超过默认的 30 秒 */
const PREVIEW_TIMEOUT_MS = 5 * 60_000;
/** 确认导入只受理并转入后台执行（V1.0.1.8），给足余量，超时后由调用方改为查询批次状态 */
const CONFIRM_TIMEOUT_MS = 60_000;

export function downloadDataExchangeTemplate(bizType: string) {
  return requestClient.download(`/edu/data-exchange/templates/${bizType}`);
}

export function previewDataExchangeImport(
  bizType: string,
  file: File,
  options?: Record<string, unknown>,
) {
  const formData = new FormData();
  formData.append('file', file);
  if (options && Object.keys(options).length > 0) {
    formData.append('optionsJson', JSON.stringify(options));
  }
  return requestClient.post<EduDataExchangeApi.ImportBatch>(
    `/edu/data-exchange/imports/${bizType}/preview`,
    formData,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: PREVIEW_TIMEOUT_MS,
    },
  );
}

export function getDataExchangeImport(batchNo: string) {
  return requestClient.get<EduDataExchangeApi.ImportBatch>(
    `/edu/data-exchange/imports/${batchNo}`,
  );
}

export function getDataExchangeImportItems(
  batchNo: string,
  params: { pageNo: number; pageSize: number },
) {
  return requestClient.get<PageResult<EduDataExchangeApi.ImportItem>>(
    `/edu/data-exchange/imports/${batchNo}/items`,
    { params },
  );
}

export function confirmDataExchangeImport(
  batchNo: string,
  options?: { initialPassword?: string },
) {
  return requestClient.post<EduDataExchangeApi.ImportBatch>(
    `/edu/data-exchange/imports/${batchNo}/confirm`,
    options,
    { timeout: CONFIRM_TIMEOUT_MS },
  );
}

export function cancelDataExchangeImport(batchNo: string) {
  return requestClient.post<EduDataExchangeApi.ImportBatch>(
    `/edu/data-exchange/imports/${batchNo}/cancel`,
  );
}

export function getDataExchangeErrorFileUrl(batchNo: string) {
  return requestClient.get<string>(
    `/edu/data-exchange/imports/${batchNo}/errors`,
  );
}

export function exportOrganizationMasterData(params?: Record<string, unknown>) {
  return requestClient.download('/edu/master-data/organization/export', {
    params,
    timeout: LONG_REQUEST_TIMEOUT,
  });
}

export function exportStudentMasterData(params?: Record<string, unknown>) {
  return requestClient.download('/edu/student-data/export', {
    params,
    timeout: LONG_REQUEST_TIMEOUT,
  });
}
