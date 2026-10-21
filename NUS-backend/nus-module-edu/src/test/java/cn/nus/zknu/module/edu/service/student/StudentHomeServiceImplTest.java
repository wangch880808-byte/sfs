package cn.nus.zknu.module.edu.service.student;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkRecordDO;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StudentHomeServiceImplTest extends BaseMockitoUnitTest {
    @InjectMocks private StudentHomeServiceImpl service;
    @Mock private EduClassStudentMapper classStudentMapper;
    @Mock private EduHomeworkMapper homeworkMapper;
    @Mock private EduHomeworkRecordMapper homeworkRecordMapper;

    @BeforeAll
    static void initializeTables() {
        for (var type : List.of(EduHomeworkDO.class, EduHomeworkRecordDO.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }

    @Test
    void overviewKeepsStatusCountsRecentOrderAndAverageWithBatchNames() {
        var membership = new EduClassStudentDO(); membership.setClassId(10L);
        when(classStudentMapper.selectListByStudentIds(List.of(1L))).thenReturn(List.of(membership));
        List<EduHomeworkDO> published = new ArrayList<>();
        for (long id = 1; id <= 7; id++) { var task = new EduHomeworkDO(); task.setId(id); published.add(task); }
        var recentTask = new EduHomeworkDO(); recentTask.setId(3L); recentTask.setHomeworkName("历史成绩任务");
        var homeworkQueries = new ArrayList<String>();
        when(homeworkMapper.selectList(any(Wrapper.class))).thenAnswer(call -> {
            var query = (Wrapper<?>) call.getArgument(0);
            homeworkQueries.add(query.getSqlSelect());
            return homeworkQueries.size() == 1 ? published : List.of(recentTask);
        });
        var correctedAt = LocalDateTime.of(2026,10,1,12,0);
        var recent = List.of(record(3L,2,80), record(9L,2,73), record(3L,2,null));
        recent.get(0).setCorrectTime(correctedAt);
        var recordQueries = new ArrayList<String>();
        when(homeworkRecordMapper.selectList(any(Wrapper.class))).thenAnswer(call -> {
            var query = (Wrapper<?>) call.getArgument(0);
            recordQueries.add(query.getSqlSelect());
            return recordQueries.size() == 1 ? List.of(record(1L,0,null),record(2L,3,40),record(3L,2,80),
                    record(4L,1,null),record(5L,4,null),record(6L,null,null)) : recent;
        });
        var response = service.getOverview(1L);
        assertEquals(3, response.getPendingCount());
        assertEquals(3, response.getPendingTaskCount());
        assertEquals(3, response.getSubmittedCount());
        assertEquals(76.5, response.getAverageScore());
        assertEquals(List.of(3L,9L,3L), response.getRecentScores().stream().map(row -> row.getHomeworkId()).toList());
        assertEquals("历史成绩任务", response.getRecentScores().get(0).getHomeworkName());
        assertNull(response.getRecentScores().get(1).getHomeworkName());
        assertEquals(correctedAt, response.getRecentScores().get(0).getCorrectTime());
        assertEquals(List.of("id","id,homework_name"), homeworkQueries);
        assertEquals(List.of("homework_id,status","homework_id,score,status,correct_time"), recordQueries);
        verify(homeworkMapper, never()).selectById(any());
        verify(homeworkMapper, times(2)).selectList(any(Wrapper.class));
    }

    @Test
    void noClassesReturnsOriginalEmptyOverviewWithoutHomeworkQueries() {
        when(classStudentMapper.selectListByStudentIds(List.of(1L))).thenReturn(List.of());
        var response = service.getOverview(1L);
        assertEquals(0, response.getPendingCount());
        assertEquals(0D, response.getAverageScore());
        assertTrue(response.getRecentScores().isEmpty());
        verifyNoInteractions(homeworkMapper, homeworkRecordMapper);
    }

    @Test
    void noPublishedOrRecentTasksSkipsEmptyInQueries() {
        var membership = new EduClassStudentDO(); membership.setClassId(10L);
        when(classStudentMapper.selectListByStudentIds(List.of(1L))).thenReturn(List.of(membership));
        when(homeworkMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(homeworkRecordMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        var response = service.getOverview(1L);
        assertEquals(0, response.getSubmittedCount());
        assertTrue(response.getRecentScores().isEmpty());
        verify(homeworkMapper).selectList(any(Wrapper.class));
        verify(homeworkRecordMapper).selectList(any(Wrapper.class));
    }

    private EduHomeworkRecordDO record(Long id, Integer status, Integer score) {
        var row = new EduHomeworkRecordDO(); row.setHomeworkId(id); row.setStatus(status); row.setScore(score); return row;
    }
}
