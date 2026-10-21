package cn.nus.zknu.module.edu.service.student;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentCourseDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentCourseRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.courseresource.EduCourseResourceDO;
import cn.nus.zknu.module.edu.dal.dataobject.student.StudentCourseHomeworkRow;
import cn.nus.zknu.module.edu.dal.dataobject.student.StudentCourseProgressRow;
import cn.nus.zknu.module.edu.dal.mysql.courseresource.EduCourseResourceMapper;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentCourseBatchMapper;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentCourseMapper;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import cn.nus.zknu.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StudentCourseBatchServiceTest extends BaseMockitoUnitTest {
    @InjectMocks private StudentCourseServiceImpl service;
    @Mock private StudentCourseMapper studentCourseMapper;
    @Mock private StudentCourseBatchMapper studentCourseBatchMapper;
    @Mock private EduCourseResourceMapper courseResourceMapper;
    @Mock private AdminUserApi adminUserApi;

    @BeforeAll
    static void initTable() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EduCourseResourceDO.class);
    }

    @BeforeEach
    void studentIdentity() {
        var user = new AdminUserRespDTO(); user.setUserType(2);
        when(adminUserApi.getUser(1L)).thenReturn(user);
    }

    @Test
    void manyCoursesUseThreeBatchQueriesAndKeepPerCourseOrderAndDefaults() {
        when(studentCourseMapper.selectClassIdsByStudentId(1L)).thenReturn(List.of(100L));
        var courses = List.of(course(20L),course(10L),course(30L));
        when(studentCourseMapper.selectCoursesByClassIds(List.of(100L))).thenReturn(courses);
        var ids = List.of(20L,10L,30L);
        var progress = new StudentCourseProgressRow(); progress.setCourseId(10L);
        progress.setTotalHomework(2); progress.setCompletedHomework(1); progress.setAvgScore(76.65);
        when(studentCourseBatchMapper.selectCourseProgressBatch(1L,ids)).thenReturn(List.of(progress));
        when(studentCourseBatchMapper.selectCourseHomeworksBatch(1L,ids))
                .thenReturn(List.of(task(10L,9L),task(20L,8L),task(10L,7L)));
        var r1 = resource(10L,2L); var r2 = resource(20L,1L); var r3 = resource(10L,3L);
        when(courseResourceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(r1,r2,r3));
        var result = service.getCourseList(1L);
        assertEquals(List.of(20L,10L,30L), result.stream().map(row -> row.getCourseId()).toList());
        assertEquals(List.of(9L,7L), result.get(1).getHomeworkList().stream().map(row -> row.getHomeworkId()).toList());
        assertEquals(List.of(2L,3L), result.get(1).getResources().stream().map(row -> row.getId()).toList());
        assertEquals("资料", result.get(1).getResources().get(0).getResourceName());
        assertEquals(new BigDecimal("76.7"), result.get(1).getAvgScore());
        assertEquals(1, result.get(1).getCompletedHomework());
        assertEquals(0, result.get(2).getTotalHomework());
        assertEquals(new BigDecimal("0.0"), result.get(2).getAvgScore());
        assertTrue(result.get(2).getResources().isEmpty());
        assertTrue(result.get(2).getHomeworkList().isEmpty());
        verify(studentCourseBatchMapper).selectCourseProgressBatch(1L,ids);
        verify(studentCourseBatchMapper).selectCourseHomeworksBatch(1L,ids);
        verify(courseResourceMapper).selectList(any(Wrapper.class));
        verify(studentCourseMapper, never()).selectCourseProgress(any(),any(),anyBoolean());
        verify(studentCourseMapper, never()).selectCourseHomeworks(any(),any(),anyBoolean());
    }

    @Test
    void noCoursesDoesNotGenerateEmptyInQueries() {
        when(studentCourseMapper.selectClassIdsByStudentId(1L)).thenReturn(List.of(100L));
        when(studentCourseMapper.selectCoursesByClassIds(List.of(100L))).thenReturn(List.of());
        assertTrue(service.getCourseList(1L).isEmpty());
        verifyNoInteractions(studentCourseBatchMapper,courseResourceMapper);
    }

    @Test
    void noClassesDoesNotQueryCourses() {
        when(studentCourseMapper.selectClassIdsByStudentId(1L)).thenReturn(List.of());
        assertTrue(service.getCourseList(1L).isEmpty());
        verify(studentCourseMapper, never()).selectCoursesByClassIds(any());
        verifyNoInteractions(studentCourseBatchMapper,courseResourceMapper);
    }

    private AppStudentCourseRespVO course(Long id) {
        var row = new AppStudentCourseRespVO(); row.setCourseId(id); return row;
    }
    private StudentCourseHomeworkRow task(Long courseId,Long id) {
        var row = new StudentCourseHomeworkRow(); row.setCourseId(courseId);
        var task = new AppStudentCourseDetailRespVO.Homework(); task.setHomeworkId(id); row.setHomework(task); return row;
    }
    private EduCourseResourceDO resource(Long courseId,Long id) {
        var row = new EduCourseResourceDO(); row.setCourseId(courseId); row.setId(id); row.setResourceName("资料"); return row;
    }
}
