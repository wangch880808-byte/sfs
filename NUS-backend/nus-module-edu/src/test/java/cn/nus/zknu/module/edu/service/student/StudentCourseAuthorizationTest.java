package cn.nus.zknu.module.edu.service.student;

import org.junit.jupiter.api.BeforeAll;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import cn.nus.zknu.module.edu.dal.dataobject.student.StudentCourseHomeworkRow;
import cn.nus.zknu.module.edu.dal.dataobject.student.StudentCourseProgressRow;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentCourseBatchMapper;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentCourseDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentCourseRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentUnlockStatusRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.courseresource.EduCourseResourceDO;
import cn.nus.zknu.module.edu.dal.mysql.courseresource.EduCourseResourceMapper;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentCourseMapper;
import cn.nus.zknu.module.infra.api.file.FileApi;
import cn.nus.zknu.module.infra.api.file.dto.FileContentRespDTO;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import cn.nus.zknu.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_COURSE_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_COURSE_RESOURCE_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_IDENTITY_REQUIRED;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_COURSE_LEVEL_LOCKED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

class StudentCourseAuthorizationTest extends BaseMockitoUnitTest {

    @BeforeAll
    static void initializeResourceTable() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                EduCourseResourceDO.class);
    }

    @InjectMocks
    private StudentCourseServiceImpl service;
    @Mock
    private StudentCourseMapper studentCourseMapper;
    @Mock
    private StudentCourseBatchMapper studentCourseBatchMapper;
    @Mock
    private EduCourseResourceMapper courseResourceMapper;
    @Mock
    private AdminUserApi adminUserApi;
    @Mock
    private FileApi fileApi;
    @Mock
    private StudentLevelUnlockService levelUnlockService;

    @Test
    void unlockStatusDelegatesToTheLevelRuleForCurrentCourses() {
        when(adminUserApi.getUser(10L)).thenReturn(user(2));
        when(studentCourseMapper.existsStudentCourse(10L, 20L)).thenReturn(true);
        AppStudentUnlockStatusRespVO.Level level = new AppStudentUnlockStatusRespVO.Level();
        level.setLevel(1);
        when(levelUnlockService.getLevels(10L, 20L, false)).thenReturn(List.of(level));

        var status = service.getUnlockStatus(10L, 20L);

        assertEquals(List.of(level), status.getLevels());
        assertEquals(false, status.getArchived());
    }

    @Test
    void archivedCourseShowsReadOnlyUnlockStatus() {
        when(adminUserApi.getUser(10L)).thenReturn(user(2));
        when(studentCourseMapper.existsStudentCourse(10L, 20L)).thenReturn(false);
        when(studentCourseMapper.existsStudentCourseHistory(10L, 20L)).thenReturn(true);
        when(levelUnlockService.getLevels(10L, 20L, true)).thenReturn(List.of());

        var status = service.getUnlockStatus(10L, 20L);

        assertEquals(true, status.getArchived());
        verify(levelUnlockService).getLevels(10L, 20L, true);
    }

    @Test
    void lockedLevelValidationIsDelegated() {
        doThrow(cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception(STUDENT_COURSE_LEVEL_LOCKED))
                .when(levelUnlockService).validateLevelUnlocked(10L, 20L, 2, 0L);
        assertServiceException(() -> service.validateLevelUnlocked(10L, 20L, 2, 0L), STUDENT_COURSE_LEVEL_LOCKED);
    }

    @Test
    void archivedCourseDetailIsReadOnlyForStudentsWithHistory() {
        when(adminUserApi.getUser(10L)).thenReturn(user(2));
        when(studentCourseMapper.existsStudentCourse(10L, 20L)).thenReturn(false);
        when(studentCourseMapper.existsStudentCourseHistory(10L, 20L)).thenReturn(true);
        AppStudentCourseDetailRespVO detail = new AppStudentCourseDetailRespVO();
        detail.setCourseId(20L);
        when(studentCourseMapper.selectCourseDetail(20L, true)).thenReturn(detail);
        when(studentCourseMapper.selectCourseHomeworks(10L, 20L, true)).thenReturn(List.of());

        AppStudentCourseDetailRespVO result = service.getCourseDetail(10L, 20L);

        assertEquals(true, result.getArchived());
        verify(studentCourseMapper).selectCourseProgress(10L, 20L, true);
    }

    @Test
    void courseWithoutCurrentOrHistoricalTasksIsRejected() {
        when(adminUserApi.getUser(10L)).thenReturn(user(2));
        when(studentCourseMapper.existsStudentCourse(10L, 20L)).thenReturn(false);
        when(studentCourseMapper.existsStudentCourseHistory(10L, 20L)).thenReturn(false);

        assertServiceException(() -> service.getCourseDetail(10L, 20L), STUDENT_COURSE_NOT_EXISTS);
        assertServiceException(() -> service.getUnlockStatus(10L, 20L), STUDENT_COURSE_NOT_EXISTS);
        verify(levelUnlockService, never()).getLevels(any(), any(), anyBoolean());
    }

    @Test
    void getCourseDetail_nonStudentIsRejected() {
        when(adminUserApi.getUser(10L)).thenReturn(user(1));

        assertServiceException(() -> service.getCourseDetail(10L, 20L), STUDENT_IDENTITY_REQUIRED);

        verify(studentCourseMapper, never()).existsStudentCourse(any(), any());
    }

    @Test
    void getCourseDetail_studentWithoutClassCourseRelationIsRejected() {
        when(adminUserApi.getUser(10L)).thenReturn(user(2));
        when(studentCourseMapper.existsStudentCourse(10L, 20L)).thenReturn(false);
        when(studentCourseMapper.existsStudentCourseHistory(10L, 20L)).thenReturn(false);

        assertServiceException(() -> service.getCourseDetail(10L, 20L), STUDENT_COURSE_NOT_EXISTS);

        verify(studentCourseMapper, never()).selectCourseDetail(any(), anyBoolean());
    }

    @Test
    void getCourseResourceFile_usesTheSameCourseAuthorization() {
        when(adminUserApi.getUser(10L)).thenReturn(user(2));
        EduCourseResourceDO resource = new EduCourseResourceDO();
        resource.setId(30L);
        resource.setCourseId(20L);
        resource.setFileUrl("http://files/internal.pdf");
        when(courseResourceMapper.selectById(30L)).thenReturn(resource);
        when(studentCourseMapper.existsStudentCourse(10L, 20L)).thenReturn(false);

        assertServiceException(() -> service.getCourseResourceFile(10L, 30L),
                STUDENT_COURSE_RESOURCE_NOT_EXISTS);

        verify(fileApi, never()).getFile(any());
        verify(fileApi, never()).getFileContent(any(), any());
    }

    @Test
    void getCourseResourceFile_authorizedStudentReceivesRegisteredFileMetadataOnly() {
        when(adminUserApi.getUser(10L)).thenReturn(user(2));
        EduCourseResourceDO resource = new EduCourseResourceDO();
        resource.setId(30L);
        resource.setCourseId(20L);
        resource.setFileUrl("http://files/internal.pdf");
        when(courseResourceMapper.selectById(30L)).thenReturn(resource);
        when(studentCourseMapper.existsStudentCourse(10L, 20L)).thenReturn(true);
        FileContentRespDTO expected = new FileContentRespDTO();
        expected.setName("课程资料.pdf");
        when(fileApi.getFile(resource.getFileUrl())).thenReturn(expected);

        assertSame(expected, service.getCourseResourceFile(10L, 30L));
        // 内容由控制器流式输出，服务层不再整体读取文件
        verify(fileApi, never()).getFileContent(any(), any());
    }

    @Test
    void getCourseResourceFile_unregisteredFileIsTreatedAsMissing() {
        when(adminUserApi.getUser(10L)).thenReturn(user(2));
        EduCourseResourceDO resource = new EduCourseResourceDO();
        resource.setId(30L);
        resource.setCourseId(20L);
        resource.setResourceName("课程资料.pdf");
        resource.setFileUrl("http://169.254.169.254/latest");
        when(courseResourceMapper.selectById(30L)).thenReturn(resource);
        when(studentCourseMapper.existsStudentCourse(10L, 20L)).thenReturn(true);
        when(fileApi.getFile(resource.getFileUrl())).thenThrow(new IllegalArgumentException("not registered"));

        assertServiceException(() -> service.getCourseResourceFile(10L, 30L),
                STUDENT_COURSE_RESOURCE_NOT_EXISTS);
    }

    @Test
    void getCourseList_returnsTeacherIndicatorTasksAndResourcesTogether() {
        when(adminUserApi.getUser(10L)).thenReturn(user(2));
        when(studentCourseMapper.selectClassIdsByStudentId(10L)).thenReturn(List.of(5L));
        AppStudentCourseRespVO course = new AppStudentCourseRespVO();
        course.setCourseId(20L);
        course.setTeacherName("张老师");
        course.setIndicatorId(40L);
        course.setIndicatorName("教学实践能力");
        when(studentCourseMapper.selectCoursesByClassIds(List.of(5L))).thenReturn(List.of(course));
        var progress = new StudentCourseProgressRow();
        progress.setCourseId(20L);
        progress.setTotalHomework(1);
        progress.setCompletedHomework(0);
        when(studentCourseBatchMapper.selectCourseProgressBatch(10L, List.of(20L))).thenReturn(List.of(progress));
        AppStudentCourseDetailRespVO.Homework homework = new AppStudentCourseDetailRespVO.Homework();
        homework.setHomeworkId(50L);
        var row = new StudentCourseHomeworkRow();
        row.setCourseId(20L);
        row.setHomework(homework);
        when(studentCourseBatchMapper.selectCourseHomeworksBatch(10L, List.of(20L))).thenReturn(List.of(row));
        EduCourseResourceDO resource = new EduCourseResourceDO();
        resource.setId(60L);
        resource.setCourseId(20L);
        resource.setResourceName("课程资料.pdf");
        when(courseResourceMapper.selectList(any())).thenReturn(List.of(resource));

        AppStudentCourseRespVO result = service.getCourseList(10L).get(0);

        assertEquals("张老师", result.getTeacherName());
        assertEquals("教学实践能力", result.getIndicatorName());
        assertEquals(50L, result.getHomeworkList().get(0).getHomeworkId());
        assertEquals(60L, result.getResources().get(0).getId());
    }

    private AdminUserRespDTO user(Integer userType) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(10L);
        user.setUserType(userType);
        return user;
    }

}
