package cn.nus.zknu.module.ai.service.prompt;

import cn.nus.zknu.module.ai.controller.admin.prompt.vo.AiPromptTemplateCreateReqVO;
import cn.nus.zknu.module.ai.controller.admin.prompt.vo.AiPromptTemplateRespVO;
import cn.nus.zknu.module.ai.controller.admin.prompt.vo.AiPromptTemplateSummaryRespVO;
import cn.nus.zknu.module.ai.enums.prompt.AiPromptCodeEnum;

import java.util.List;

/**
 * AI 提示词：按业务读取启用版本，没有启用版本时使用内置默认内容。
 */
public interface AiPromptTemplateService {

    /** 当前生效的提示词；读取失败或没有启用版本时返回内置默认内容，不会抛出异常。 */
    String getActiveContent(AiPromptCodeEnum code);

    List<AiPromptTemplateSummaryRespVO> getSummaryList();

    List<AiPromptTemplateRespVO> getHistory(String businessCode);

    Long createVersion(AiPromptTemplateCreateReqVO reqVO);

    void activateVersion(Long id);

}
