package cn.nus.zknu.module.system.service.notify;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.dal.dataobject.notify.*;
import cn.nus.zknu.module.system.dal.mysql.notify.NotifyMessageMapper;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.util.*;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotifyMessageServiceImplTest extends BaseMockitoUnitTest {
    @InjectMocks private NotifyMessageServiceImpl service;
    @Mock private NotifyMessageMapper mapper;

    @Test
    void thousandAndOneRecipientsUseThreeBatchesAndPreserveAllMessageFields() {
        NotifyTemplateDO template = template();
        Map<String, Object> params = Map.of("name", "学生", "targetUrl", "/student");
        List<Long> ids = LongStream.rangeClosed(1, 1001).boxed().toList();
        when(mapper.insertMessagesBatch(anyCollection())).thenReturn(true);
        assertEquals(ids, service.createNotifyMessages(ids, 2, template, "正文", params));
        ArgumentCaptor<Collection<NotifyMessageDO>> messages = ArgumentCaptor.forClass(Collection.class);
        verify(mapper, times(3)).insertMessagesBatch(messages.capture());
        assertEquals(List.of(500, 500, 1), messages.getAllValues().stream().map(Collection::size).toList());
        List<NotifyMessageDO> rows = messages.getAllValues().stream().flatMap(Collection::stream).toList();
        assertEquals(ids, rows.stream().map(NotifyMessageDO::getUserId).toList());
        assertTrue(rows.stream().allMatch(row -> row.getUserType() == 2 && !row.getReadStatus()
                && row.getTemplateId().equals(7L) && row.getTemplateCode().equals("test")
                && row.getTemplateType() == 1 && row.getTemplateNickname().equals("教务")
                && row.getTemplateContent().equals("正文") && row.getTemplateParams().equals(params)));
        verify(mapper, never()).insert(any(NotifyMessageDO.class));
    }
    @Test
    void failedBatchDoesNotCountAnyRecipientAndLaterBatchesContinue() {
        when(mapper.insertMessagesBatch(anyCollection())).thenReturn(true).thenThrow(new IllegalStateException("第二批失败")).thenReturn(true);
        List<Long> ids = LongStream.rangeClosed(1, 1001).boxed().toList();
        List<Long> expected = new ArrayList<>(ids.subList(0, 500)); expected.add(1001L);
        assertEquals(expected, service.createNotifyMessages(ids, 2, template(), "正文", Map.of()));
        verify(mapper, times(3)).insertMessagesBatch(anyCollection());
    }
    @Test
    void emptyDoesNotInsertAndFalseBatchIsNotSuccessful() {
        assertEquals(List.of(), service.createNotifyMessages(List.of(), 2, template(), "正文", Map.of()));
        verifyNoInteractions(mapper);
        when(mapper.insertMessagesBatch(anyCollection())).thenReturn(false);
        assertEquals(List.of(), service.createNotifyMessages(List.of(1L), 2, template(), "正文", Map.of()));
    }
    private NotifyTemplateDO template() {
        NotifyTemplateDO template = new NotifyTemplateDO(); template.setId(7L); template.setCode("test");
        template.setType(1); template.setNickname("教务"); return template;
    }
}
