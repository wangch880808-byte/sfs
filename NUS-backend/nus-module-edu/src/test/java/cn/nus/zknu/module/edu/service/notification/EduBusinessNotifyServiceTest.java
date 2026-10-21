package cn.nus.zknu.module.edu.service.notification;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.api.notify.NotifyMessageSendApi;
import cn.nus.zknu.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;
import cn.nus.zknu.module.system.api.notify.dto.NotifySendBatchToUserReqDTO;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class EduBusinessNotifyServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduBusinessNotifyService notifyService;
    @Mock
    private NotifyMessageSendApi notifyMessageSendApi;

    @Test
    void sendToUser_addsTargetUrlToTemplateParams() {
        when(notifyMessageSendApi.sendSingleMessageToAdmin(any())).thenReturn(100L);

        notifyService.sendToUser(10L, EduBusinessNotifyService.HOMEWORK_CREATED,
                Map.of("homeworkName", "口语训练"), "/student/homework/submit/20");

        ArgumentCaptor<NotifySendSingleToUserReqDTO> captor =
                ArgumentCaptor.forClass(NotifySendSingleToUserReqDTO.class);
        verify(notifyMessageSendApi).sendSingleMessageToAdmin(captor.capture());
        assertEquals(10L, captor.getValue().getUserId());
        assertEquals(EduBusinessNotifyService.HOMEWORK_CREATED, captor.getValue().getTemplateCode());
        assertEquals("口语训练", captor.getValue().getTemplateParams().get("homeworkName"));
        assertEquals("/student/homework/submit/20", captor.getValue().getTemplateParams().get("targetUrl"));
    }

    @Test
    void sendToUser_downstreamFailureDoesNotEscape() {
        when(notifyMessageSendApi.sendSingleMessageToAdmin(any())).thenThrow(new IllegalStateException("failed"));

        assertFalse(notifyService.sendToUser(10L, EduBusinessNotifyService.HOMEWORK_CREATED,
                Map.of("homeworkName", "口语训练"), null));
    }

    @Test
    void sendToUsers_filtersNullsAndDuplicatesAndReturnsActualSuccessCount() {
        when(notifyMessageSendApi.sendBatchMessageToAdmin(any())).thenReturn(List.of(10L));

        int successCount = notifyService.sendToUsers(Arrays.asList(10L, null, 10L, 20L),
                EduBusinessNotifyService.HOMEWORK_REMIND, Map.of("homeworkName", "口语训练"), null);

        assertEquals(1, successCount);
        ArgumentCaptor<NotifySendBatchToUserReqDTO> req = ArgumentCaptor.forClass(NotifySendBatchToUserReqDTO.class);
        verify(notifyMessageSendApi).sendBatchMessageToAdmin(req.capture());
        assertEquals(List.of(10L, 20L), req.getValue().getUserIds());
        verify(notifyMessageSendApi, never()).sendSingleMessageToAdmin(any());
    }

    @Test
    void batchFailureAndEmptyRecipientsReturnZeroWithoutBlockingBusiness() {
        assertEquals(0, notifyService.sendToUsers(List.of(), "template", Map.of(), null));
        assertEquals(0, notifyService.sendToUsers(List.of(10L), " ", Map.of(), null));
        verifyNoInteractions(notifyMessageSendApi);
        when(notifyMessageSendApi.sendBatchMessageToAdmin(any())).thenThrow(new IllegalStateException("批次失败"));
        assertEquals(0, notifyService.sendToUsers(List.of(10L), "template", Map.of(), "/target"));
    }
}
