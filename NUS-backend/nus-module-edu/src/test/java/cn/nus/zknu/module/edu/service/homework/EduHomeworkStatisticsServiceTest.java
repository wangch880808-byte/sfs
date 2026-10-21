package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.coursemanage.EduCourseStatMapper;
import cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EduHomeworkStatisticsServiceTest extends BaseMockitoUnitTest {
    @InjectMocks private EduHomeworkStatisticsService service;
    @Mock private EduCourseStatMapper courseStatMapper;
    @Mock private EduHomeworkArchiveAccessService archiveAccessService;
    @Mock private EduMetricPolicy metricPolicy;

    @Test
    void aggregateMapsEveryFieldWithoutRoundingAndKeepsActiveScope() {
        var homework = new EduHomeworkDO();
        homework.setStatus(1);
        when(archiveAccessService.isInActiveScope(homework)).thenReturn(true);
        when(metricPolicy.passScore(homework)).thenReturn(120);
        var stat = new EduCourseStatMapper.HomeworkStat();
        stat.setTotalCount(8);
        stat.setSubmittedCount(6);
        stat.setCorrectedCount(3);
        stat.setRedoCount(1);
        stat.setPassCount(4);
        stat.setScoreTotal(401L);
        stat.setScoreCount(3);
        stat.setMaxScore(200);
        stat.setMinScore(0);
        when(courseStatMapper.selectHomeworkStatistics(5L, 120)).thenReturn(stat);
        var vo = service.getHomeworkStatistics(5L, homework);
        assertEquals(5L, vo.getHomeworkId());
        assertFalse(vo.getReadOnly());
        assertEquals(8, vo.getTotalCount());
        assertEquals(6, vo.getSubmittedCount());
        assertEquals(3, vo.getCorrectedCount());
        assertEquals(1, vo.getRedoCount());
        assertEquals(4, vo.getPassCount());
        assertEquals(401D / 3, vo.getAvgScore());
        assertEquals(200, vo.getMaxScore());
        assertEquals(0, vo.getMinScore());
        verify(courseStatMapper).selectHomeworkStatistics(5L, 120);
        verifyNoMoreInteractions(courseStatMapper);
    }

    @Test
    void archivedEmptyStatisticsRemainReadOnlyAndScoresRemainNull() {
        var homework = new EduHomeworkDO();
        homework.setStatus(3);
        when(courseStatMapper.selectHomeworkStatistics(5L, 0)).thenReturn(new EduCourseStatMapper.HomeworkStat());
        var vo = service.getHomeworkStatistics(5L, homework);
        assertTrue(vo.getReadOnly());
        assertEquals(0, vo.getTotalCount());
        assertNull(vo.getAvgScore());
        assertNull(vo.getMaxScore());
        assertNull(vo.getMinScore());
        verifyNoInteractions(archiveAccessService);
    }
}
