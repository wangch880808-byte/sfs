package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordHistoryDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordHistoryMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EduHomeworkRecordHistoryServiceImplTest extends BaseMockitoUnitTest {
    @InjectMocks
    private EduHomeworkRecordHistoryServiceImpl service;
    @Mock
    private EduHomeworkRecordHistoryMapper historyMapper;

    @Test
    void appendCapturesTransitionAndOperator() {
        EduHomeworkRecordDO record = new EduHomeworkRecordDO();
        record.setId(1L);
        record.setHomeworkId(2L);
        record.setStudentId(3L);
        record.setStatus(2);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            service.append(record, "CORRECT", 2, 85, "复评", null, 9L);
            service.append(record, "SUBMIT", 1, null, null, null, null);
        }
        ArgumentCaptor<EduHomeworkRecordHistoryDO> captor = ArgumentCaptor.forClass(EduHomeworkRecordHistoryDO.class);
        verify(historyMapper, times(2)).insert(captor.capture());
        EduHomeworkRecordHistoryDO correction = captor.getAllValues().get(0);
        assertEquals(1L, correction.getRecordId());
        assertEquals(2L, correction.getHomeworkId());
        assertEquals(3L, correction.getStudentId());
        assertEquals(2, correction.getFromStatus());
        assertEquals(2, correction.getToStatus());
        assertEquals(85, correction.getScore());
        assertEquals(9L, correction.getAiReviewId());
        assertEquals(7L, correction.getOperatorId());
        assertNull(captor.getAllValues().get(1).getFromStatus());
    }
}
