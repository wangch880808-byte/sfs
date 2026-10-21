package cn.nus.zknu.module.edu.service.aireview;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.AiEduReviewTaskDO;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.AiEduReviewTaskMapper;
import cn.nus.zknu.module.edu.dal.mysql.aireview.EduHomeworkAiReviewMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.enums.homework.EduHomeworkRecordStatusEnum;
import cn.nus.zknu.module.edu.service.aireview.support.HomeworkAiReviewStatusEnum;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 「批量生成 AI 初评」只补缺：没有初评、初评失败或被跳过、对应旧提交的才重新生成，可作为失败后的重试入口。 */
class AiEduReviewBatchCoverageTest extends BaseMockitoUnitTest {

    private static final LocalDateTime SUBMIT_TIME = LocalDateTime.of(2026, 10, 7, 9, 0);

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                EduHomeworkRecordDO.class);
    }

    @InjectMocks
    private AiEduReviewServiceImpl service;
    @Mock
    private AiEduReviewTaskMapper reviewTaskMapper;
    @Mock
    private EduHomeworkRecordMapper homeworkRecordMapper;
    @Mock
    private EduHomeworkAiReviewMapper aiReviewMapper;
    @Mock
    private EduHomeworkAiReviewService homeworkAiReviewService;

    @Test
    void batchCoversMissingFailedSkippedAndOutdatedReviewsOnly() {
        EduHomeworkRecordDO noReview = record(1L);
        EduHomeworkRecordDO failed = record(2L);
        EduHomeworkRecordDO ready = record(3L);
        EduHomeworkRecordDO outdated = record(4L);
        EduHomeworkRecordDO queued = record(5L);
        EduHomeworkRecordDO ignored = record(6L);
        EduHomeworkRecordDO skipped = record(7L);
        List<EduHomeworkRecordDO> records = List.of(noReview, failed, ready, outdated, queued, ignored, skipped);
        AiEduReviewTaskDO task = new AiEduReviewTaskDO();
        task.setId(9L);
        task.setHomeworkId(10L);
        task.setTeacherId(7L);
        task.setStatus(0);
        task.setTotalCount(records.size());
        when(reviewTaskMapper.selectById(9L)).thenReturn(task);
        when(reviewTaskMapper.claim(eq(9L), any())).thenReturn(1);
        when(homeworkRecordMapper.selectList(any())).thenReturn(records);
        when(aiReviewMapper.selectLatestByRecordId(1L)).thenReturn(null);
        when(aiReviewMapper.selectLatestByRecordId(2L)).thenReturn(review(20L, HomeworkAiReviewStatusEnum.FAILED, SUBMIT_TIME));
        when(aiReviewMapper.selectLatestByRecordId(3L)).thenReturn(review(30L, HomeworkAiReviewStatusEnum.READY, SUBMIT_TIME));
        when(aiReviewMapper.selectLatestByRecordId(4L)).thenReturn(review(40L, HomeworkAiReviewStatusEnum.READY,
                SUBMIT_TIME.minusDays(1)));
        when(aiReviewMapper.selectLatestByRecordId(5L)).thenReturn(review(50L, HomeworkAiReviewStatusEnum.QUEUED, SUBMIT_TIME));
        when(aiReviewMapper.selectLatestByRecordId(6L)).thenReturn(review(60L, HomeworkAiReviewStatusEnum.REJECTED, SUBMIT_TIME));
        when(aiReviewMapper.selectLatestByRecordId(7L)).thenReturn(review(70L, HomeworkAiReviewStatusEnum.SKIPPED, SUBMIT_TIME));
        when(homeworkAiReviewService.generatePreReviewForTeacher(anyLong(), eq(7L)))
                .thenReturn(review(99L, HomeworkAiReviewStatusEnum.READY, SUBMIT_TIME));
        when(homeworkAiReviewService.processQueuedReview(50L))
                .thenReturn(review(50L, HomeworkAiReviewStatusEnum.READY, SUBMIT_TIME));

        service.generateDrafts(9L);

        verify(homeworkAiReviewService).generatePreReviewForTeacher(1L, 7L);
        verify(homeworkAiReviewService).generatePreReviewForTeacher(2L, 7L);
        verify(homeworkAiReviewService).generatePreReviewForTeacher(4L, 7L);
        verify(homeworkAiReviewService).generatePreReviewForTeacher(7L, 7L);
        verify(homeworkAiReviewService).processQueuedReview(50L);
        verify(homeworkAiReviewService, never()).generatePreReviewForTeacher(eq(3L), any());
        verify(homeworkAiReviewService, never()).generatePreReviewForTeacher(eq(5L), any());
        verify(homeworkAiReviewService, never()).generatePreReviewForTeacher(eq(6L), any());
        ArgumentCaptor<AiEduReviewTaskDO> progress = ArgumentCaptor.forClass(AiEduReviewTaskDO.class);
        verify(reviewTaskMapper, atLeastOnce()).updateIfClaimed(progress.capture(), eq(9L), any());
        AiEduReviewTaskDO last = progress.getValue();
        assertEquals(7, last.getSuccessCount());
        assertEquals(0, last.getFailCount());
        assertEquals(1, last.getStatus());
    }

    @Test
    void retryThatFailsAgainIsCountedAsFailure() {
        EduHomeworkRecordDO failed = record(2L);
        AiEduReviewTaskDO task = new AiEduReviewTaskDO();
        task.setId(9L);
        task.setHomeworkId(10L);
        task.setTeacherId(7L);
        task.setStatus(0);
        task.setTotalCount(1);
        when(reviewTaskMapper.selectById(9L)).thenReturn(task);
        when(reviewTaskMapper.claim(eq(9L), any())).thenReturn(1);
        when(homeworkRecordMapper.selectList(any())).thenReturn(List.of(failed));
        when(aiReviewMapper.selectLatestByRecordId(2L)).thenReturn(review(20L, HomeworkAiReviewStatusEnum.FAILED, SUBMIT_TIME));
        when(homeworkAiReviewService.generatePreReviewForTeacher(2L, 7L)).thenThrow(new IllegalStateException("模型不可用"));

        service.generateDrafts(9L);

        ArgumentCaptor<AiEduReviewTaskDO> progress = ArgumentCaptor.forClass(AiEduReviewTaskDO.class);
        verify(reviewTaskMapper, atLeastOnce()).updateIfClaimed(progress.capture(), eq(9L), any());
        assertEquals(1, progress.getValue().getFailCount());
        assertEquals(2, progress.getValue().getStatus());
    }

    private static EduHomeworkRecordDO record(Long id) {
        EduHomeworkRecordDO record = new EduHomeworkRecordDO();
        record.setId(id);
        record.setHomeworkId(10L);
        record.setStatus(EduHomeworkRecordStatusEnum.SUBMITTED);
        record.setSubmitTime(SUBMIT_TIME);
        return record;
    }

    private static EduHomeworkAiReviewDO review(Long id, int status, LocalDateTime sourceSubmitTime) {
        EduHomeworkAiReviewDO review = new EduHomeworkAiReviewDO();
        review.setId(id);
        review.setStatus(status);
        review.setSourceSubmitTime(sourceSubmitTime);
        return review;
    }
}
