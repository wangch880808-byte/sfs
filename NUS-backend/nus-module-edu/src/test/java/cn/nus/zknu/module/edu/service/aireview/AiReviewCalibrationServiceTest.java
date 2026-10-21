package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.aireview.vo.AiReviewCalibrationRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.AiCalibrationSample;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.AiCalibrationSampleMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiReviewCalibrationServiceTest extends BaseMockitoUnitTest {

    @InjectMocks private AiReviewCalibrationService service;
    @Mock private AiCalibrationSampleMapper sampleMapper;

    @Test
    void deviationChoosesStricterAndHintHasNoStudentData() {
        AiReviewCalibrationRespVO result = AiReviewCalibrationService.summarize(List.of(
                deviation(-5), deviation(-4), deviation(-6), feedback("loose"), feedback("normal")), "TASK", 100);
        assertEquals("STRICTER", result.getDirection());
        assertTrue(result.getHint().contains("当前任务"));
        assertTrue(result.getHint().contains("加严反馈0次，放宽反馈1次"));
        assertTrue(result.getHint().length() <= 300);
        assertFalse(result.getHint().contains("学生"));
    }

    @Test
    void positiveDeviationChoosesLooser() {
        AiReviewCalibrationRespVO result = AiReviewCalibrationService.summarize(List.of(
                deviation(4), deviation(5), deviation(5), feedback("strict"), feedback("normal")), "TASK", 100);
        assertEquals("LOOSER", result.getDirection());
    }

    @Test
    void insufficientAndConflictingSignalsHaveNoHint() {
        AiReviewCalibrationRespVO insufficient = AiReviewCalibrationService.summarize(
                List.of(deviation(0), feedback("strict")), "COURSE", 100);
        assertEquals("INSUFFICIENT", insufficient.getDirection());
        assertNull(insufficient.getHint());
        AiReviewCalibrationRespVO conflict = AiReviewCalibrationService.summarize(List.of(
                deviation(1), feedback("strict"), feedback("strict"), feedback("loose"), feedback("loose")), "TASK", 100);
        assertEquals("NONE", conflict.getDirection());
        assertNull(conflict.getHint());
    }

    @Test
    void fallsBackFromTaskToCourse() {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(10L);
        homework.setCourseId(20L);
        homework.setScore(100);
        when(sampleMapper.selectRecent(eq(1L), eq(20L), eq(10L), eq(true), any(LocalDateTime.class)))
                .thenReturn(List.of(feedback("strict")));
        when(sampleMapper.selectRecent(eq(1L), eq(20L), eq(10L), eq(false), any(LocalDateTime.class)))
                .thenReturn(List.of(feedback("strict"), feedback("strict"), feedback("strict"),
                        feedback("normal"), feedback("normal")));

        AiReviewCalibrationRespVO result = service.calculate(1L, homework);

        assertEquals("COURSE", result.getScope());
        assertEquals("STRICTER", result.getDirection());
        assertTrue(result.getHint().contains("同一课程"));
        verify(sampleMapper).selectRecent(eq(1L), eq(20L), eq(10L), eq(false), any(LocalDateTime.class));
    }

    private static AiCalibrationSample deviation(int value) {
        AiCalibrationSample sample = new AiCalibrationSample();
        sample.setDeviation(value);
        sample.setFullScore(100);
        return sample;
    }

    private static AiCalibrationSample feedback(String type) {
        AiCalibrationSample sample = new AiCalibrationSample();
        sample.setFeedbackType(type);
        return sample;
    }
}
