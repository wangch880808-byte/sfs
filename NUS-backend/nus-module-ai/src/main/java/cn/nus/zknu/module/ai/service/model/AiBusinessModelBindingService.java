package cn.nus.zknu.module.ai.service.model;

import cn.nus.zknu.module.ai.controller.admin.model.vo.binding.AiBusinessModelBindingRespVO;
import cn.nus.zknu.module.ai.controller.admin.model.vo.binding.AiBusinessModelBindingSaveReqVO;
import cn.nus.zknu.module.ai.dal.dataobject.model.AiModelDO;

import java.util.List;

public interface AiBusinessModelBindingService {

    List<AiBusinessModelBindingRespVO> getBindingList();

    void saveBinding(AiBusinessModelBindingSaveReqVO request);

    void saveBindings(List<AiBusinessModelBindingSaveReqVO> requests);

    AiModelDO getRequiredModel(String businessCode);

}
