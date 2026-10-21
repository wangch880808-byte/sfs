package cn.nus.zknu.module.edu.controller.admin.homework;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkRecordPageReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.aireview.EduHomeworkAiReviewDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.aireview.EduHomeworkAiReviewMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordPageMapper;
import cn.nus.zknu.module.edu.service.aireview.EduHomeworkAiReviewService;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkRecordService;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EduHomeworkRecordControllerTest extends BaseMockitoUnitTest {
    @InjectMocks private EduHomeworkRecordController controller;
    @Mock private EduHomeworkRecordService recordService;
    @Mock private EduHomeworkRecordPageMapper recordPageMapper;
    @Mock private EduHomeworkAiReviewMapper aiReviewMapper;
    @Mock private EduHomeworkAiReviewService aiReviewService;
    @Mock private AdminUserService userService;

    @ParameterizedTest
    @CsvSource({"false,1", "false,10", "false,50", "true,1", "true,10", "true,50"})
    void pageEnrichmentUsesThreeBatchCallsRegardlessOfRowCount(boolean correction, int count) {
        var req = new EduHomeworkRecordPageReqVO();
        var records = new ArrayList<EduHomeworkRecordDO>();
        for (long id = 1; id <= count; id++) {
            var record = new EduHomeworkRecordDO();
            record.setId(id);
            record.setHomeworkId(id % 2 + 100);
            record.setStudentId(id);
            record.setStatus(id == 2 ? 4 : 2);
            records.add(record);
        }
        var page = new PageResult<>(records, 100L);
        if (correction) when(recordService.getCorrectPage(req)).thenReturn(page);
        else when(recordService.getRecordPage(req)).thenReturn(page);
        var ids = records.stream().map(EduHomeworkRecordDO::getId).collect(Collectors.toSet());
        var homeworkIds = records.stream().map(EduHomeworkRecordDO::getHomeworkId).collect(Collectors.toSet());
        when(userService.getUserMap(ids)).thenReturn(Map.of(1L, new AdminUserDO().setId(1L).setUsername("S1").setNickname("学生")));
        var review = new EduHomeworkAiReviewDO();
        review.setHomeworkRecordId(1L);
        review.setStatus(2);
        review.setAiScore(90);
        review.setAiComment("评语");
        review.setAiConfidence(0.8);
        when(aiReviewMapper.selectLatestByRecordIds(ids)).thenReturn(List.of(review));
        var rank = new EduHomeworkRecordPageMapper.RankRow();
        rank.setHomeworkId(101L);
        rank.setStudentId(1L);
        rank.setRank(1);
        rank.setTotal(20);
        when(recordPageMapper.selectRanks(homeworkIds)).thenReturn(List.of(rank));
        var response = (correction ? controller.getCorrectPage(req) : controller.getRecordPage(req)).getData();
        assertEquals(100L, response.getTotal());
        assertEquals(count, response.getList().size());
        var first = response.getList().get(0);
        assertEquals("学生", first.getStudentName());
        assertEquals("S1", first.getStudentNo());
        assertEquals(2, first.getAiReviewStatus());
        assertEquals(90, first.getAiReviewScore());
        assertEquals("评语", first.getAiReviewComment());
        assertEquals(0.8, first.getAiReviewConfidence());
        assertEquals(1, first.getClassRank());
        assertEquals(20, first.getRankTotalStudents());
        if (count > 1) {
            assertNull(response.getList().get(1).getAiReviewStatus());
            assertNull(response.getList().get(1).getClassRank());
        }
        verify(userService).getUserMap(ids);
        verify(aiReviewMapper).selectLatestByRecordIds(ids);
        verify(recordPageMapper).selectRanks(homeworkIds);
        verifyNoMoreInteractions(userService, aiReviewMapper, recordPageMapper);
        verifyNoInteractions(aiReviewService);
    }

    @Test
    void emptyPageDoesNotReadEnrichment() {
        var req = new EduHomeworkRecordPageReqVO();
        when(recordService.getRecordPage(req)).thenReturn(PageResult.empty());
        assertTrue(controller.getRecordPage(req).getData().getList().isEmpty());
        verifyNoInteractions(userService, aiReviewMapper, recordPageMapper, aiReviewService);
    }

    @Test
    void detailStillReturnsAiFieldsAndRank() {
        var record = new EduHomeworkRecordDO();
        record.setId(1L);
        record.setStudentId(7L);
        record.setHomeworkId(100L);
        record.setStatus(2);
        when(recordService.getRecord(1L)).thenReturn(record);
        when(userService.getUserMap(List.of(7L))).thenReturn(Map.of());
        when(recordPageMapper.selectRanks(Set.of(100L))).thenReturn(List.of());
        var review = new EduHomeworkAiReviewDO();
        review.setStatus(3);
        when(aiReviewService.getLatestByRecordId(1L)).thenReturn(review);
        assertEquals(3, controller.getRecord(1L).getData().getAiReviewStatus());
        verifyNoInteractions(aiReviewMapper);
    }
}
