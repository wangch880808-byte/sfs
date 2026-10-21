package cn.nus.zknu.module.edu.service.statistics;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.mysql.statistics.StatisticsMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 任务顺序来自平均分查询，双向名单来自一次批量查询。 */
class EduStatisticsCourseAnalysisTest extends BaseMockitoUnitTest {
    @InjectMocks private EduStatisticsServiceImpl service;
    @Mock private StatisticsMapper mapper;
    @Mock private EduDataScopeService dataScopeService;
    private MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach
    void setUp() {
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
        when(dataScopeService.isGlobalAdmin()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    @Test
    void batchesAllTasksAndSeparatelyOrdersOverlappingLists() {
        when(mapper.selectCourseAnalysisAvg(8L, 9L, null)).thenReturn(List.of(
                Map.of("homeworkId", 20L, "homeworkName", "先发布", "avgScore", 55.5),
                Map.of("homeworkId", 10L, "homeworkName", "后发布", "avgScore", 40)));
        when(mapper.selectHomeworkStudentExtremes(List.of(20L, 10L))).thenReturn(List.of(
                score(20L, 2L, 40, 2, 1), score(20L, 1L, 80, 1, 2),
                score(10L, 4L, 90, 1, 7), score(10L, 3L, 10, 7, 1)));
        var tasks = service.getCourseAnalysis(8L, 9L).getHomeworks();
        assertEquals(List.of(20L, 10L), tasks.stream().map(t -> t.getHomeworkId()).toList());
        assertEquals("先发布", tasks.get(0).getHomeworkName());
        assertEquals(55.5, tasks.get(0).getAvgScore());
        assertEquals(List.of(1L, 2L), tasks.get(0).getTop5().stream().map(s -> s.getStudentId()).toList());
        assertEquals(List.of(2L, 1L), tasks.get(0).getLast5().stream().map(s -> s.getStudentId()).toList());
        assertEquals(List.of(4L), tasks.get(1).getTop5().stream().map(s -> s.getStudentId()).toList());
        assertEquals(List.of(3L), tasks.get(1).getLast5().stream().map(s -> s.getStudentId()).toList());
        verify(mapper).selectCourseAnalysisAvg(8L, 9L, null);
        verify(mapper).selectHomeworkStudentExtremes(List.of(20L, 10L));
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void noTasksSkipsWindowQueryAndMissingVisibleStudentsKeepEmptyLists() {
        when(mapper.selectCourseAnalysisAvg(8L, null, null)).thenReturn(List.of());
        assertTrue(service.getCourseAnalysis(8L, null).getHomeworks().isEmpty());
        verify(mapper).selectCourseAnalysisAvg(8L, null, null);
        verifyNoMoreInteractions(mapper);
        when(mapper.selectCourseAnalysisAvg(9L, null, null)).thenReturn(
                List.of(Map.of("homeworkId", 1L, "homeworkName", "无可见学生")));
        when(mapper.selectHomeworkStudentExtremes(List.of(1L))).thenReturn(List.of());
        var task = service.getCourseAnalysis(9L, null).getHomeworks().get(0);
        assertEquals(0.0, task.getAvgScore());
        assertTrue(task.getTop5().isEmpty());
        assertTrue(task.getLast5().isEmpty());
    }

    private Map<String, Object> score(long homework, long student, int score, int top, int last) {
        return Map.of("homeworkId", homework, "studentId", student, "studentName", "学生" + student,
                "score", score, "topRank", top, "lastRank", last);
    }
}
