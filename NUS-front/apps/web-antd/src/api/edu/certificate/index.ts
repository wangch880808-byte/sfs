import { requestClient } from '#/api/request';

export namespace EduCertificateApi {
  /** 证书阈值 */
  export interface ThresholdVO {
    threshold: number;
  }
}

/** 获取证书阈值 */
export function getCertificateThreshold() {
  return requestClient.get<EduCertificateApi.ThresholdVO>(
    '/edu/certificate/threshold',
  );
}

/** 设置证书阈值 */
export function setCertificateThreshold(threshold: number) {
  return requestClient.put('/edu/certificate/threshold', { threshold });
}
