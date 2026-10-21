package cn.nus.zknu.module.system.api.notify;

import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import cn.nus.zknu.module.system.api.notify.dto.NotifySendBatchToUserReqDTO;
import cn.nus.zknu.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import cn.nus.zknu.module.system.service.notify.NotifySendService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内信发送 API 实现类
 *
 * @author hyh
 */
@Service
public class NotifyMessageSendApiImpl implements NotifyMessageSendApi {

    @Resource
    private NotifySendService notifySendService;

    @Override
    public Long sendSingleMessageToAdmin(NotifySendSingleToUserReqDTO reqDTO) {
        return notifySendService.sendSingleNotifyToAdmin(reqDTO.getUserId(),
                reqDTO.getTemplateCode(), reqDTO.getTemplateParams());
    }

    @Override
    public List<Long> sendBatchMessageToAdmin(NotifySendBatchToUserReqDTO reqDTO) {
        return notifySendService.sendBatchNotify(reqDTO.getUserIds(), UserTypeEnum.ADMIN.getValue(),
                reqDTO.getTemplateCode(), reqDTO.getTemplateParams());
    }

}
