package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.copy.EduHomeworkCopyTargetRespVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterOptionRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.edu.service.semester.EduSemesterOptionService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EduHomeworkCopyServiceTest extends BaseMockitoUnitTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, EduClassCourseDO.class);
    }

    @InjectMocks
    private EduHomeworkCopyService service;
    @Mock
    private EduClassCourseMapper classCourseMapper;
    @Mock
    private EduClassMapper classMapper;
    @Mock
    private CapacityCourseApi courseMapper;
    @Mock
    private EduDataScopeService dataScopeService;
    @Mock
    private EduSemesterOptionService semesterOptionService;

    @Test
    void listTargetsOnlyReturnsAccessibleCourses() {
        EduSemesterOptionRespVO semester = new EduSemesterOptionRespVO();
        semester.setSemesterName("2026-2027 上学期");
        when(semesterOptionService.resolve(null)).thenReturn(semester);
        EduClassCourseDO mine = link(20L, 3L, 9L, 0);
        EduClassCourseDO others = link(21L, 3L, 10L, 0);
        when(classCourseMapper.selectList(any())).thenReturn(List.of(mine, others));
        when(courseMapper.getCourseList(any())).thenReturn(List.of(course(9L, 0, 11L), course(10L, 0, 11L)));
        when(classMapper.selectByIds(any())).thenReturn(List.of(clazz(3L, 0)));
        lenient().doThrow(exception(FORBIDDEN)).when(dataScopeService).validateCourseAccess(10L);

        List<EduHomeworkCopyTargetRespVO> targets = service.listTargets(null);

        assertEquals(1, targets.size());
        assertEquals(20L, targets.get(0).getClassCourseId());
    }

    @Test
    void publicCopyTargetsOnlyContainAssignableClasses() {
        var semester = new EduSemesterOptionRespVO(); semester.setSemesterName("2026-2027 上学期");
        when(semesterOptionService.resolve(null)).thenReturn(semester);
        when(classCourseMapper.selectList(any())).thenReturn(List.of(link(20L, 3L, 9L, 0), link(21L, 4L, 9L, 0)));
        when(courseMapper.getCourseList(any())).thenReturn(List.of(course(9L, 0, 11L)));
        when(classMapper.selectByIds(any())).thenReturn(List.of(clazz(3L, 0), clazz(4L, 0)));
        when(dataScopeService.isPublicCourse(9L)).thenReturn(true);
        when(dataScopeService.canAssignToPublicClass(9L, 3L)).thenReturn(true);
        assertEquals(List.of(20L), service.listTargets(null).stream()
                .map(EduHomeworkCopyTargetRespVO::getClassCourseId).toList());
        verify(dataScopeService).canAssignToPublicClass(9L, 4L);
    }

    @Test
    void backfillSharedNamingStillUsesLargestSuffix() {
        assertEquals("任务(3)", service.uniqueName("任务", List.of("任务", "任务(2)", "任务(备用)")));
        assertEquals("任务", service.uniqueName("任务", List.of("任务(2)")));
    }

    private EduClassCourseDO link(Long id, Long classId, Long courseId, int status) {
        EduClassCourseDO link = new EduClassCourseDO();
        link.setId(id);
        link.setClassId(classId);
        link.setCourseId(courseId);
        link.setStatus(status);
        link.setSemesterName(" 2026-2027 上学期 ");
        return link;
    }

    private EduClassDO clazz(Long id, int status) {
        EduClassDO clazz = new EduClassDO();
        clazz.setId(id);
        clazz.setClassName("软件 1 班");
        clazz.setStatus(status);
        return clazz;
    }

    private CapacityCourseRespDTO course(Long id, int status, Long indicatorId) {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(id);
        course.setCourseName("课程" + id);
        course.setStatus(status);
        course.setIndicatorId(indicatorId);
        return course;
    }
}
