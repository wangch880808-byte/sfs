package cn.nus.zknu.module.system.api.notify;

import cn.nus.zknu.module.system.api.notify.dto.NotifySendBatchToUserReqDTO;
import cn.nus.zknu.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 站内信发送 API 接口
 *
 * @author hyh
 */
public interface NotifyMessageSendApi {

    /**
     * 发送单条站内信给 Admin 用户
     *
     * @param reqDTO 发送请求
     * @return 发送消息 ID
     */
    Long sendSingleMessageToAdmin(@Valid NotifySendSingleToUserReqDTO reqDTO);

    /** 返回实际成功收件人；每 500 人一批，失败批次不影响后续批次。 */
    List<Long> sendBatchMessageToAdmin(@Valid NotifySendBatchToUserReqDTO reqDTO);

}
