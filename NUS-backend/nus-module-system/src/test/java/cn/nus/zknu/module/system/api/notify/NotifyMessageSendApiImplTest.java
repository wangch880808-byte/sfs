package cn.nus.zknu.module.system.api.notify;

import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.api.notify.dto.NotifySendBatchToUserReqDTO;
import cn.nus.zknu.module.system.service.notify.NotifySendService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class NotifyMessageSendApiImplTest extends BaseMockitoUnitTest {
    @InjectMocks private NotifyMessageSendApiImpl api;
    @Mock private NotifySendService service;
    @Test
    void batchUsesSameAdminUserTypeAsSingleSend() {
        NotifySendBatchToUserReqDTO req = new NotifySendBatchToUserReqDTO();
        req.setUserIds(List.of(1L)); req.setTemplateCode("test"); req.setTemplateParams(Map.of("name", "学生"));
        when(service.sendBatchNotify(req.getUserIds(), UserTypeEnum.ADMIN.getValue(), "test", req.getTemplateParams())).thenReturn(List.of(1L));
        assertEquals(List.of(1L), api.sendBatchMessageToAdmin(req));
        verify(service).sendBatchNotify(req.getUserIds(), UserTypeEnum.ADMIN.getValue(), "test", req.getTemplateParams());
    }
}
