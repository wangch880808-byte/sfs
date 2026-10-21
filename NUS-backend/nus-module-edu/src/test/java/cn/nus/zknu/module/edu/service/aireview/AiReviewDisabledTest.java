package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.module.edu.service.aireview.config.EduAiReviewProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.AI_REVIEW_DISABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiReviewDisabledTest {

    @Test
    void batchReviewFailsBeforeCreatingTaskWhenDisabled() {
        EduAiReviewProperties properties = new EduAiReviewProperties();
        properties.setEnabled(false);
        AiEduReviewServiceImpl service = new AiEduReviewServiceImpl();
        ReflectionTestUtils.setField(service, "properties", properties);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.correctAllAsync(1L, null));
        assertEquals(AI_REVIEW_DISABLED.getCode(), exception.getCode());
    }

    @Test
    void manualReviewFailsExplicitlyWhenDisabled() {
        EduAiReviewProperties properties = new EduAiReviewProperties();
        properties.setEnabled(false);
        EduHomeworkAiReviewServiceImpl service = new EduHomeworkAiReviewServiceImpl();
        ReflectionTestUtils.setField(service, "properties", properties);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.triggerPreReviewAsync(1L, true));
        assertEquals(AI_REVIEW_DISABLED.getCode(), exception.getCode());
    }
}
