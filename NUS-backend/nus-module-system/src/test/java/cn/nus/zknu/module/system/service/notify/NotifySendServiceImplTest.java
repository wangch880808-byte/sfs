package cn.nus.zknu.module.system.service.notify;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.dal.dataobject.notify.NotifyTemplateDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import java.util.*;
import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.system.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotifySendServiceImplTest extends BaseMockitoUnitTest {
    @InjectMocks private NotifySendServiceImpl service;
    @Mock private NotifyTemplateService notifyTemplateService;
    @Mock private NotifyMessageService notifyMessageService;
    private NotifyTemplateDO template;

    @BeforeEach
    void setUp() {
        template = new NotifyTemplateDO(); template.setCode("test"); template.setStatus(0);
        template.setParams(List.of("name")); template.setContent("你好 {name}");
        lenient().when(notifyTemplateService.getNotifyTemplateByCodeFromCache("test")).thenReturn(template);
    }
    @Test
    void batchValidatesAndRendersOnceAndKeepsUserType() {
        Map<String, Object> params = Map.of("name", "学生", "targetUrl", "/student");
        when(notifyTemplateService.formatNotifyTemplateContent(template.getContent(), params)).thenReturn("你好 学生");
        when(notifyMessageService.createNotifyMessages(List.of(1L, 2L), 2, template, "你好 学生", params))
                .thenReturn(List.of(1L, 2L));
        assertEquals(List.of(1L, 2L), service.sendBatchNotify(Arrays.asList(1L, null, 1L, 2L), 2, "test", params));
        verify(notifyTemplateService).getNotifyTemplateByCodeFromCache("test");
        verify(notifyTemplateService).formatNotifyTemplateContent(template.getContent(), params);
        verify(notifyMessageService).createNotifyMessages(List.of(1L, 2L), 2, template, "你好 学生", params);
    }
    @Test
    void disabledTemplateDoesNotValidateMissingParamsOrInsert() {
        template.setStatus(1);
        assertTrue(service.sendBatchNotify(List.of(1L), 2, "test", null).isEmpty());
        verifyNoInteractions(notifyMessageService);
        verify(notifyTemplateService, never()).formatNotifyTemplateContent(any(), any());
    }
    @Test
    void emptyRecipientsNeverReadTemplate() {
        assertTrue(service.sendBatchNotify(List.of(), 2, "test", null).isEmpty());
        assertTrue(service.sendBatchNotify(null, 2, "test", null).isEmpty());
        assertTrue(service.sendBatchNotify(Arrays.asList((Long) null), 2, "test", null).isEmpty());
        verifyNoInteractions(notifyTemplateService, notifyMessageService);
    }
    @Test
    void missingTemplateAndMissingParamKeepOriginalErrors() {
        assertServiceException(() -> service.sendBatchNotify(List.of(1L), 2, "missing", Map.of()), NOTICE_NOT_FOUND);
        assertServiceException(() -> service.sendBatchNotify(List.of(1L), 2, "test", Map.of()), NOTIFY_SEND_TEMPLATE_PARAM_MISS, "name");
        verifyNoInteractions(notifyMessageService);
    }
}
