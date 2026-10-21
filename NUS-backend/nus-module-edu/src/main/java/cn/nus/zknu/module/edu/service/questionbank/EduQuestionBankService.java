package cn.nus.zknu.module.edu.service.questionbank;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.questionbank.vo.QuestionBankPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.questionbank.vo.QuestionBankRespVO;

/**
 * 题库管理 Service 接口
 */
public interface EduQuestionBankService {

    PageResult<QuestionBankRespVO> getPage(QuestionBankPageReqVO reqVO);

    void clearByTeacher();

    void delete(Long id);

}
