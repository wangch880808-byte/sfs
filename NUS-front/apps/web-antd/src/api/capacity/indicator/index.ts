import { requestClient } from '#/api/request';

export namespace CapacityIndicatorApi {
  export interface Indicator {
    id?: number;
    indicatorName: string;
    indicatorDetails?: string;
    indicatorLevel: number;
    parentIndicatorId: number;
    weight: number;
    status: number;
    createTime?: Date;
    parentIndicatorName?: string;
    children?: Indicator[];
  }

  export interface ResetWeightItem {
    id: number;
    weight: number;
  }

  export interface ResetWeightReqVO {
    indicatorLevel: number;
    parentIndicatorId: number;
    indicators: ResetWeightItem[];
  }

  export interface BatchCreateItem {
    indicatorName: string;
    indicatorDetails?: string;
    weight: number;
    status: number;
  }

  export interface BatchCreateReqVO {
    indicatorLevel: number;
    parentIndicatorId: number;
    indicators: BatchCreateItem[];
  }
}

/** 查询指标列表 */
export function getIndicatorList() {
  return requestClient.get<CapacityIndicatorApi.Indicator[]>(
    '/capacity/indicator/list',
  );
}

/** 查询指标详情 */
export function getIndicator(id: number) {
  return requestClient.get<CapacityIndicatorApi.Indicator>(
    `/capacity/indicator/get?id=${id}`,
  );
}

/** 新增指标 */
export function createIndicator(data: CapacityIndicatorApi.Indicator) {
  return requestClient.post('/capacity/indicator/create', data);
}

/** 批量新增指标 */
export function createIndicatorBatch(data: CapacityIndicatorApi.BatchCreateReqVO) {
  return requestClient.post('/capacity/indicator/create-batch', data);
}

/** 修改指标 */
export function updateIndicator(data: CapacityIndicatorApi.Indicator) {
  return requestClient.put('/capacity/indicator/update', data);
}

/** 批量重设指标权重 */
export function resetIndicatorWeights(data: CapacityIndicatorApi.ResetWeightReqVO) {
  return requestClient.put('/capacity/indicator/reset-weight', data);
}

/** 删除指标 */
export function deleteIndicator(id: number) {
  return requestClient.delete(`/capacity/indicator/delete?id=${id}`);
}
