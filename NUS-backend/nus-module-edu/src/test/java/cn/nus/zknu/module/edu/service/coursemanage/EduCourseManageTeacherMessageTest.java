package cn.nus.zknu.module.edu.service.coursemanage;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.coursemanage.vo.CourseManageTeacherMessageReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.message.EduMessageMapper;
import cn.nus.zknu.module.edu.service.classcourse.EduClassCourseAssistantService;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.List;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.CLASS_COURSE_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.CLASS_STUDENT_NOT_EXISTS;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STUDENT_COURSE_NOT_EXISTS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 班级详情中教师给学生留言的归属校验
 */
class EduCourseManageTeacherMessageTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduCourseManageServiceImpl courseManageService;

    @Mock
    private CapacityCourseApi capacityCourseApi;
    @Mock
    private EduClassCourseAssistantService assistantService;
    @Mock
    private EduClassCourseMapper eduClassCourseMapper;
    @Mock
    private EduClassStudentMapper eduClassStudentMapper;
    @Mock
    private EduMessageMapper eduMessageMapper;
    @Mock
    private EduBusinessNotifyService businessNotifyService;
    @Mock
    private EduDataScopeService dataScopeService;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"true,false,true", "false,true,true", "false,false,false"})
    void publicTeacherMessageRequiresClaimOrAssistant(boolean mine, boolean assistant, boolean allowed) {
        mockCourse(7L, null);
        mockClassCourse(null);
        when(dataScopeService.isPublicCourse(10L)).thenReturn(true);
        when(dataScopeService.isClaimedByCurrentUser(10L, 20L)).thenReturn(mine);
        when(assistantService.isAssistant(anyCollection(), eq(7L))).thenReturn(assistant);
        if (allowed) when(eduClassStudentMapper.selectListByClassIdAndStudentIds(eq(20L), anyCollection()))
                .thenReturn(List.of(new EduClassStudentDO()));
        runAs(7L, () -> {
            if (allowed) courseManageService.sendTeacherMessage(request());
            else assertServiceException(() -> courseManageService.sendTeacherMessage(request()), STUDENT_COURSE_NOT_EXISTS);
        });
        verify(eduMessageMapper, org.mockito.Mockito.times(allowed ? 1 : 0))
                .insert(any(cn.nus.zknu.module.edu.dal.dataobject.message.EduMessageDO.class));
    }

    @Test
    void publicGlobalAdminCanMessageWithoutClaimOrAssistantRole() {
        mockCourse(8L, null); mockClassCourse(null);
        when(dataScopeService.isPublicCourse(10L)).thenReturn(true);
        when(dataScopeService.isGlobalAdmin()).thenReturn(true);
        when(eduClassStudentMapper.selectListByClassIdAndStudentIds(eq(20L), anyCollection()))
                .thenReturn(List.of(new EduClassStudentDO()));
        runAs(7L, () -> courseManageService.sendTeacherMessage(request()));
        verify(eduMessageMapper).insert(any(cn.nus.zknu.module.edu.dal.dataobject.message.EduMessageDO.class));
    }

    @Test
    void courseTeacher_canMessageStudentInBoundClass() {
        mockCourse(7L, null);
        mockClassCourse(null);
        when(eduClassStudentMapper.selectListByClassIdAndStudentIds(eq(20L), anyCollection()))
                .thenReturn(List.of(new EduClassStudentDO()));

        runAs(7L, () -> courseManageService.sendTeacherMessage(request()));

        verify(eduMessageMapper).insert(any(cn.nus.zknu.module.edu.dal.dataobject.message.EduMessageDO.class));
        verify(businessNotifyService).sendToUser(eq(30L), eq(EduBusinessNotifyService.STUDENT_MESSAGE_REPLY), any(), any());
    }

    @Test
    void otherTeacher_isRejected() {
        mockCourse(7L, List.of(8L));
        mockClassCourse(List.of(9L));

        runAs(99L, () -> assertServiceException(() -> courseManageService.sendTeacherMessage(request()),
                STUDENT_COURSE_NOT_EXISTS));

        verify(eduMessageMapper, never()).insert(any(cn.nus.zknu.module.edu.dal.dataobject.message.EduMessageDO.class));
    }

    @Test
    void classNotBoundToCourse_isRejected() {
        mockCourse(7L, null);
        when(eduClassCourseMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        runAs(7L, () -> assertServiceException(() -> courseManageService.sendTeacherMessage(request()),
                CLASS_COURSE_NOT_EXISTS));

        verify(eduMessageMapper, never()).insert(any(cn.nus.zknu.module.edu.dal.dataobject.message.EduMessageDO.class));
    }

    @Test
    void studentOutsideClass_isRejected() {
        mockCourse(7L, null);
        mockClassCourse(null);
        when(eduClassStudentMapper.selectListByClassIdAndStudentIds(eq(20L), anyCollection()))
                .thenReturn(Collections.emptyList());

        runAs(7L, () -> assertServiceException(() -> courseManageService.sendTeacherMessage(request()),
                CLASS_STUDENT_NOT_EXISTS));

        verify(eduMessageMapper, never()).insert(any(cn.nus.zknu.module.edu.dal.dataobject.message.EduMessageDO.class));
    }

    @Test
    void classLevelAssistant_canMessageStudent() {
        mockCourse(7L, null);
        mockClassCourse(List.of(12L, 15L));
        when(eduClassStudentMapper.selectListByClassIdAndStudentIds(eq(20L), anyCollection()))
                .thenReturn(List.of(new EduClassStudentDO()));

        runAs(15L, () -> courseManageService.sendTeacherMessage(request()));

        verify(eduMessageMapper).insert(any(cn.nus.zknu.module.edu.dal.dataobject.message.EduMessageDO.class));
    }

    @Test
    void courseTeamMember_canMessageStudent() {
        mockCourse(7L, List.of(8L));
        mockClassCourse(null);
        when(eduClassStudentMapper.selectListByClassIdAndStudentIds(eq(20L), anyCollection()))
                .thenReturn(List.of(new EduClassStudentDO()));

        runAs(8L, () -> courseManageService.sendTeacherMessage(request()));

        verify(eduMessageMapper).insert(any(cn.nus.zknu.module.edu.dal.dataobject.message.EduMessageDO.class));
    }

    @Test
    void publicCourseApprover_canMessageStudentAsManager() {
        mockCourse(7L, null);
        mockClassCourse(null);
        when(capacityCourseApi.isCourseAdmin(50L)).thenReturn(true);
        when(eduClassStudentMapper.selectListByClassIdAndStudentIds(eq(20L), anyCollection()))
                .thenReturn(List.of(new EduClassStudentDO()));

        runAs(50L, () -> courseManageService.sendTeacherMessage(request()));

        verify(eduMessageMapper).insert(any(cn.nus.zknu.module.edu.dal.dataobject.message.EduMessageDO.class));
    }

    /** 课程团队来自 capacity_course_member（经 CapacityCourseApi）。 */
    private void mockCourse(Long teacherId, List<Long> teamIds) {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(10L);
        course.setCourseName("书写技能");
        course.setTeacherId(teacherId);
        when(capacityCourseApi.getCourse(10L)).thenReturn(course);
        List<Long> team = teamIds == null ? List.of() : teamIds;
        lenient().when(capacityCourseApi.isCourseMember(eq(10L), any()))
                .thenAnswer(invocation -> team.contains(invocation.<Long>getArgument(1)));
    }

    /** 班课助教来自 edu_class_course_assistant；旧列故意写成无关的值，确认不再读取。 */
    private void mockClassCourse(List<Long> assistantIds) {
        EduClassCourseDO classCourse = new EduClassCourseDO();
        classCourse.setId(40L);
        classCourse.setClassId(20L);
        classCourse.setCourseId(10L);
        classCourse.setAssistantTeacherIds("8,9,12,15,99");
        when(eduClassCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(classCourse));
        List<Long> assistants = assistantIds == null ? List.of() : assistantIds;
        lenient().when(assistantService.isAssistant(eq(List.of(40L)), any()))
                .thenAnswer(invocation -> assistants.contains(invocation.<Long>getArgument(1)));
    }

    private CourseManageTeacherMessageReqVO request() {
        CourseManageTeacherMessageReqVO reqVO = new CourseManageTeacherMessageReqVO();
        reqVO.setCourseId(10L);
        reqVO.setClassId(20L);
        reqVO.setStudentId(30L);
        reqVO.setContent("请补充实训记录");
        return reqVO;
    }

    private void runAs(Long userId, Runnable action) {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(userId);
            action.run();
        }
    }

}
