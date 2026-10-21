package cn.nus.zknu.module.ai.service.model;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.ai.controller.admin.model.vo.model.AiModelPageReqVO;
import cn.nus.zknu.module.ai.controller.admin.model.vo.model.AiModelSaveReqVO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiModelDO;
import jakarta.validation.Valid;
import org.springframework.ai.chat.model.ChatModel;

import javax.annotation.Nullable;
import java.util.List;

/**
 * AI 模型 Service 接口
 */
public interface AiModelService {

    Long createModel(@Valid AiModelSaveReqVO createReqVO);

    void updateModel(@Valid AiModelSaveReqVO updateReqVO);

    void deleteModel(Long id);

    AiModelDO getModel(Long id);

    AiModelDO getRequiredDefaultModel(Integer type);

    PageResult<AiModelDO> getModelPage(AiModelPageReqVO pageReqVO);

    AiModelDO validateModel(Long id);

    List<AiModelDO> getModelListByStatusAndType(Integer status, Integer type, @Nullable String platform);

    ChatModel getChatModel(Long id);


}
