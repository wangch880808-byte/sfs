package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.ai.api.edu.AiEduReviewApi;
import cn.nus.zknu.module.ai.api.edu.dto.AiEduReviewReqDTO;
import cn.nus.zknu.module.ai.api.edu.dto.AiEduReviewRespDTO;
import cn.nus.zknu.module.edu.controller.admin.aireview.vo.AiReviewCalibrationRespVO;
import cn.nus.zknu.module.capacity.api.indicator.dto.CapacityIndicatorRespDTO;
import cn.nus.zknu.module.capacity.api.indicator.CapacityIndicatorApi;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.EduHomeworkAiReviewMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.service.aireview.support.ReviewEvidence;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class EduHomeworkAiReviewServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduHomeworkAiReviewServiceImpl service;
    @Mock
    private EduHomeworkAiReviewMapper aiReviewMapper;
    @Mock
    private EduHomeworkRecordMapper homeworkRecordMapper;
    @Mock
    private EduHomeworkMapper homeworkMapper;
    @Mock
    private HomeworkReviewEvidenceService reviewEvidenceService;
    @Mock
    private AiEduReviewApi aiEduReviewApi;
    @Mock
    private EduAiBusinessAccessService accessService;
    @Mock
    private CapacityIndicatorApi capacityIndicatorMapper;
    @Mock
    private AiReviewCalibrationService calibrationService;
    @Mock
    private cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy metricPolicy;
    @Mock
    private HomeworkAiReviewFailureNotifier failureNotifier;

    @Test
    void latestReadUsesViewingPermissionAndMutationStillUsesCorrectionPermission() {
        EduHomeworkAiReviewDO review = new EduHomeworkAiReviewDO();
        when(aiReviewMapper.selectLatestByRecordId(1L)).thenReturn(review);
        assertEquals(review, service.getLatestByRecordId(1L));
        verify(accessService).requireRecordViewAccess(1L);
        verify(accessService, never()).requireRecordAccess(1L);
        when(aiReviewMapper.selectLatestByRecordId(1L)).thenReturn(null);
        service.rejectLatestReview(1L);
        verify(accessService).requireRecordAccess(1L);
    }

    @Test
    void generatePreReview_sendsRealFirstAndSecondIndicatorNames() {
        EduHomeworkRecordDO record = new EduHomeworkRecordDO();
        record.setId(1L);
        record.setHomeworkId(100L);
        record.setStatus(1);
        EduHomeworkDO homework = homework(10L, 20L);
        when(homeworkRecordMapper.selectById(1L)).thenReturn(record);
        when(homeworkMapper.selectById(100L)).thenReturn(homework);
        when(calibrationService.calculate(nullable(Long.class), eq(homework)))
                .thenReturn(new AiReviewCalibrationRespVO());
        when(metricPolicy.passScore(homework)).thenReturn(60);
        when(capacityIndicatorMapper.getIndicator(10L)).thenReturn(indicator(10L, "教学设计能力", null));
        when(capacityIndicatorMapper.getIndicator(20L)).thenReturn(indicator(20L, "教案编写", 10L));
        ReviewEvidence evidence = new ReviewEvidence();
        when(reviewEvidenceService.build(homework, record)).thenReturn(evidence);
        AiEduReviewRespDTO response = new AiEduReviewRespDTO();
        response.setScore(80);
        response.setConfidence(0.9D);
        when(aiEduReviewApi.correctHomework(any())).thenReturn(response);
        when(aiReviewMapper.selectById(any())).thenReturn(new EduHomeworkAiReviewDO());

        service.generatePreReview(1L, true);

        ArgumentCaptor<AiEduReviewReqDTO> captor = ArgumentCaptor.forClass(AiEduReviewReqDTO.class);
        verify(aiEduReviewApi).correctHomework(captor.capture());
        assertEquals("教学设计能力 / 教案编写", captor.getValue().getIndicatorName());
        verify(accessService).requireRecordAccess(1L);
    }

    @Test
    void modelTimeoutMarksDraftFailed() {
        EduHomeworkRecordDO record = new EduHomeworkRecordDO();
        record.setId(1L);
        record.setHomeworkId(100L);
        record.setStatus(1);
        EduHomeworkDO homework = homework(null, null);
        when(homeworkRecordMapper.selectById(1L)).thenReturn(record);
        when(homeworkMapper.selectById(100L)).thenReturn(homework);
        when(reviewEvidenceService.build(homework, record)).thenReturn(new ReviewEvidence());
        when(calibrationService.calculate(nullable(Long.class), eq(homework)))
                .thenReturn(new AiReviewCalibrationRespVO());
        when(aiEduReviewApi.correctHomework(any())).thenThrow(new IllegalStateException("AI 服务响应超时"));

        assertThrows(IllegalStateException.class, () -> service.generatePreReview(1L, true));

        ArgumentCaptor<EduHomeworkAiReviewDO> captured = ArgumentCaptor.forClass(EduHomeworkAiReviewDO.class);
        verify(aiReviewMapper).updateIfProcessing(captured.capture(), any(), any());
        assertEquals(3, captured.getValue().getStatus());
        assertEquals(HomeworkAiReviewFailures.failedMessage(HomeworkAiReviewFailures.MODEL_TIMEOUT_CAUSE, true),
                captured.getValue().getErrorMessage());
        // 教师当面发起的生成直接看到报错，不另发通知。
        verifyNoInteractions(failureNotifier);
    }

    @Test
    void resolveIndicatorName_usesFirstLevelNameWhenOnlyFirstLevelExists() {
        when(capacityIndicatorMapper.getIndicator(10L)).thenReturn(indicator(10L, "教学设计能力", null));

        assertEquals("教学设计能力", resolveIndicatorName(homework(10L, null)));
    }

    @Test
    void resolveIndicatorName_derivesFirstLevelFromSecondLevelParent() {
        when(capacityIndicatorMapper.getIndicator(20L)).thenReturn(indicator(20L, "教案编写", 10L));
        when(capacityIndicatorMapper.getIndicator(10L)).thenReturn(indicator(10L, "教学设计能力", null));

        assertEquals("教学设计能力 / 教案编写", resolveIndicatorName(homework(null, 20L)));
    }

    @Test
    void resolveIndicatorName_returnsNullWithoutIndicators() {
        assertNull(resolveIndicatorName(homework(null, null)));
        verify(capacityIndicatorMapper, never()).getIndicator(anyLong());
    }

    @Test
    void resolveIndicatorName_returnsNullWhenIndicatorsWereDeleted() {
        when(capacityIndicatorMapper.getIndicator(anyLong())).thenReturn(null);

        assertNull(resolveIndicatorName(homework(10L, 20L)));
    }

    private String resolveIndicatorName(EduHomeworkDO homework) {
        return ReflectionTestUtils.invokeMethod(service, "resolveIndicatorName", homework);
    }

    private static EduHomeworkDO homework(Long firstId, Long secondId) {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(100L);
        homework.setIndicatorFirstId(firstId);
        homework.setIndicatorSecondId(secondId);
        homework.setSubmissionMode("text");
        return homework;
    }

    private static CapacityIndicatorRespDTO indicator(Long id, String name, Long parentId) {
        CapacityIndicatorRespDTO indicator = new CapacityIndicatorRespDTO();
        indicator.setId(id);
        indicator.setIndicatorName(name);
        indicator.setParentIndicatorId(parentId);
        indicator.setIndicatorLevel(parentId == null ? 1 : 2);
        return indicator;
    }

}
