package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.AiEduFeedbackDO;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.AiEduFeedbackMapper;
import cn.nus.zknu.module.edu.dal.mysql.aireview.EduHomeworkAiReviewMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.AI_REVIEW_FEEDBACK_NO_DRAFT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AiEduReviewFeedbackTest extends BaseMockitoUnitTest {

    @InjectMocks private AiEduReviewServiceImpl service;
    @Mock private EduAiBusinessAccessService accessService;
    @Mock private EduHomeworkAiReviewMapper reviewMapper;
    @Mock private AiEduFeedbackMapper feedbackMapper;

    @Test
    void recordsLatestAiDraftScoreInsteadOfFinalScore() {
        EduHomeworkRecordDO record = new EduHomeworkRecordDO();
        record.setId(1L);
        record.setScore(90);
        EduHomeworkAiReviewDO draft = new EduHomeworkAiReviewDO();
        draft.setAiScore(70);
        when(accessService.requireRecordAccess(1L)).thenReturn(record);
        when(reviewMapper.selectLatestScoredByRecordId(1L)).thenReturn(draft);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(5L);
            service.submitFeedback(1L, "strict");
        }
        ArgumentCaptor<AiEduFeedbackDO> captured = ArgumentCaptor.forClass(AiEduFeedbackDO.class);
        verify(feedbackMapper).insert(captured.capture());
        assertEquals(70, captured.getValue().getOriginalScore());
        assertEquals(5L, captured.getValue().getTeacherId());
    }

    @Test
    void rejectsFeedbackWithoutAiDraft() {
        EduHomeworkRecordDO record = new EduHomeworkRecordDO();
        record.setId(1L);
        when(accessService.requireRecordAccess(1L)).thenReturn(record);
        assertServiceException(() -> service.submitFeedback(1L, "normal"), AI_REVIEW_FEEDBACK_NO_DRAFT);
        verifyNoInteractions(feedbackMapper);
    }
}
