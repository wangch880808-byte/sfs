import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace EduQuestionBankApi {
  /** 题库条目 */
  export interface QuestionBank {
    id: number;
    title: string;
    indicatorFirstId: number;
    indicatorFirstName?: string;
    indicatorSecondId: number;
    indicatorSecondName?: string;
    homeworkLevel: number;
    requirement: string;
    createTime?: Date | string;
  }

  /** 分页查询参数 */
  export interface QuestionBankPageReqVO extends PageParam {
    indicatorFirstId?: number;
    indicatorSecondId?: number;
    homeworkLevel?: number;
    title?: string;
  }

}

/** 分页查询题库 */
export function getQuestionBankPage(
  params: EduQuestionBankApi.QuestionBankPageReqVO,
) {
  return requestClient.get<PageResult<EduQuestionBankApi.QuestionBank>>(
    '/edu/question-bank/page',
    { params },
  );
}

/** 清空题库 */
export function clearQuestionBank() {
  return requestClient.delete('/edu/question-bank/clear');
}

/** 删除单条 */
export function deleteQuestionBank(id: number) {
  return requestClient.delete(`/edu/question-bank/delete?id=${id}`);
}
