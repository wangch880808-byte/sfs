package cn.nus.zknu.module.system.api.notify.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Collection;
import java.util.Map;

/** 同一模板和参数的批量站内信请求。 */
@Data
public class NotifySendBatchToUserReqDTO {
    private Collection<Long> userIds;
    @NotEmpty(message = "通知模板编号不能为空")
    private String templateCode;
    private Map<String, Object> templateParams;
}
