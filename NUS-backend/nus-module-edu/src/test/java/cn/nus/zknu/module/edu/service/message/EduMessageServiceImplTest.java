package cn.nus.zknu.module.edu.service.message;

import cn.nus.zknu.module.system.api.user.dto.AdminUserRespDTO;
import cn.nus.zknu.module.edu.controller.admin.message.vo.MessageStudentListRespVO;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.dal.dataobject.message.EduMessageDO;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.dal.mysql.message.EduMessageMapper;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EduMessageServiceImplTest extends BaseMockitoUnitTest {
    @InjectMocks private EduMessageServiceImpl service;
    @Mock private EduMessageMapper messageMapper;
    @Mock private cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper classCourseMapper;
    @Mock private cn.nus.zknu.module.edu.service.classcourse.EduClassCourseAssistantService assistantService;
    @Mock private CapacityCourseApi courseMapper;
    @Mock private EduClassStudentMapper classStudentMapper;
    @Mock private EduDataScopeService dataScopeService;
    @Mock private AdminUserApi adminUserApi;
    @Mock private EduBusinessNotifyService businessNotifyService;
    @Mock private EduMessageRecipientResolver recipientResolver;
    @Mock private EduMessageNotifyReadService notifyReadService;
    @Mock private cn.nus.zknu.module.edu.service.homework.EduHomeworkArchiveAccessService archiveAccessService;

    @BeforeAll
    static void initializeTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                EduMessageDO.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"true,false,true", "false,true,true", "false,false,false"})
    void publicConversationWritesRequireClaimantOrAssistant(boolean mine, boolean assistant, boolean allowed) {
        when(dataScopeService.isPublicCourse(2L)).thenReturn(true);
        when(dataScopeService.isClaimedByCurrentUser(2L, 1L)).thenReturn(mine);
        if (!mine) {
            var relation = new cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO(); relation.setId(5L);
            when(classCourseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(relation));
            when(assistantService.isAssistant(List.of(5L), 7L)).thenReturn(assistant);
        }
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            // 只读查看已经通过班课授权，写操作必须独立判断。
            service.getStudentList(1L, 2L, null);
            if (allowed) {
                when(classStudentMapper.selectListByClassIdAndStudentIds(eq(1L), any())).thenReturn(List.of(new EduClassStudentDO()));
                service.reply(1L, 2L, 3L, "回复");
                verify(messageMapper).insert(any(EduMessageDO.class));
            } else {
                assertServiceException(() -> service.reply(1L, 2L, 3L, "回复"), FORBIDDEN);
                assertServiceException(() -> service.closeConversation(1L, 2L, 3L), FORBIDDEN);
                assertServiceException(() -> service.reopenConversation(1L, 2L, 3L), FORBIDDEN);
                verify(messageMapper, never()).insert(any(EduMessageDO.class));
                verify(messageMapper, never()).update(isNull(), any(Wrapper.class));
            }
        }
    }

    @Test
    void globalAdminCanReplyCloseAndReopenPublicConversationWithoutClaim() {
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new EduMessageDO()));
        when(dataScopeService.isPublicCourse(2L)).thenReturn(true);
        when(dataScopeService.isGlobalAdmin()).thenReturn(true);
        when(classStudentMapper.selectListByClassIdAndStudentIds(eq(1L), any()))
                .thenReturn(List.of(new EduClassStudentDO()));
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            service.reply(1L, 2L, 3L, "回复");
            service.closeConversation(1L, 2L, 3L);
            service.reopenConversation(1L, 2L, 3L);
            verify(messageMapper).insert(any(EduMessageDO.class));
            verify(messageMapper, times(2)).update(isNull(), any(Wrapper.class));
            verifyNoInteractions(classCourseMapper, assistantService);
        }
    }

    @Test
    void unrelatedTeacherCannotReadOrWrite() {
        doThrow(exception(FORBIDDEN)).when(dataScopeService).validateClassCourseAccess(1L, 2L);
        assertServiceException(() -> service.getStudentList(1L, 2L, null), FORBIDDEN);
        assertServiceException(() -> service.getDetail(1L, 2L, 3L), FORBIDDEN);
        assertServiceException(() -> service.reply(1L, 2L, 3L, "回复"), FORBIDDEN);
        assertServiceException(() -> service.closeConversation(1L, 2L, 3L), FORBIDDEN);
        verifyNoInteractions(messageMapper);
    }

    @Test
    void teacherCannotReplyInArchivedClassCourse() {
        doThrow(exception(cn.nus.zknu.module.edu.enums.ErrorCodeConstants.MESSAGE_SCOPE_ARCHIVED, "班课", "班课"))
                .when(archiveAccessService).validateMessageScopeActive(1L, 2L);

        assertServiceException(() -> service.reply(1L, 2L, 3L, "回复"),
                cn.nus.zknu.module.edu.enums.ErrorCodeConstants.MESSAGE_SCOPE_ARCHIVED, "班课", "班课");

        verify(messageMapper, never()).insert(any(EduMessageDO.class));
        verifyNoInteractions(businessNotifyService);
    }

    @Test
    void studentCannotReadAnotherOrArchivedConversation() {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(9L);
            when(messageMapper.existsStudentConversation(9L, 1L, 2L)).thenReturn(false);
            assertServiceException(() -> service.getMyDetail(1L, 2L), MESSAGE_STUDENT_COURSE_FORBIDDEN);
            assertServiceException(() -> service.studentAdd(1L, 2L, "留言"), MESSAGE_STUDENT_COURSE_FORBIDDEN);
            verify(messageMapper, never()).insert(any(EduMessageDO.class));
        }
    }

    @Test
    void closedConversationRejectsBothSendersAndTeacherCanReopen() {
        when(classStudentMapper.selectListByClassIdAndStudentIds(eq(1L), any()))
                .thenReturn(List.of(new EduClassStudentDO()));
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(message(1L, 0, 1, 0)));
        assertServiceException(() -> service.reply(1L, 2L, 3L, "回复"), MESSAGE_CONVERSATION_CLOSED);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(3L);
            when(messageMapper.existsStudentConversation(3L, 1L, 2L)).thenReturn(true);
            assertServiceException(() -> service.studentAdd(1L, 2L, "留言"), MESSAGE_CONVERSATION_CLOSED);
        }
        service.reopenConversation(1L, 2L, 3L);
        verify(messageMapper).update(isNull(), any(Wrapper.class));
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(message(1L, 0, 0, 0)));
        verify(messageMapper, never()).insert(any(EduMessageDO.class));
        service.reply(1L, 2L, 3L, "回复");
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(3L);
            service.studentAdd(1L, 2L, "留言");
        }
        verify(messageMapper, times(2)).insert(any(EduMessageDO.class));
    }

    @Test
    void unreadCountUsesMessageRowsAndDetailMarksStudentMessagesRead() {
        List<EduMessageDO> messages = new java.util.ArrayList<>(List.of(
                message(3L, 0, 0, 0), message(2L, 0, 0, 1), message(1L, 0, 0, 0)));
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(messages);
        when(messageMapper.selectTeacherStudentSummaries(1L, 2L)).thenAnswer(invocation -> summaries(messages));
        when(messageMapper.update(isNull(), any(Wrapper.class))).thenAnswer(invocation -> {
            messages.forEach(item -> item.setReadStatus(1));
            return 2;
        });
        assertEquals(2, service.getStudentList(1L, 2L, null).get(0).getUnreadCount());
        service.getDetail(1L, 2L, 3L);
        assertEquals(0, service.getStudentList(1L, 2L, null).get(0).getUnreadCount());
        verify(messageMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    void studentSendIncreasesTeacherUnreadMessageCount() {
        List<EduMessageDO> messages = new ArrayList<>();
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(messages);
        when(messageMapper.selectTeacherStudentSummaries(1L, 2L)).thenAnswer(invocation -> summaries(messages));
        when(messageMapper.existsStudentConversation(3L, 1L, 2L)).thenReturn(true);
        when(messageMapper.insert(any(EduMessageDO.class))).thenAnswer(invocation -> {
            messages.add(invocation.getArgument(0));
            return 1;
        });
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(3L);
            assertEquals(0, service.getStudentList(1L, 2L, null).size());
            service.studentAdd(1L, 2L, "留言");
            assertEquals(1, service.getStudentList(1L, 2L, null).get(0).getUnreadCount());
        }
    }

    @Test
    void studentMessageNotifiesEveryTeacherOfTheClassCourseOnce() {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(2L);
        course.setCourseName("Java 实训");
        course.setTeacherId(10L);
        when(courseMapper.getCourse(2L)).thenReturn(course);
        when(messageMapper.existsStudentConversation(3L, 1L, 2L)).thenReturn(true);
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());
        // 解析器给出课程教师、团队成员与班课助教（已去重）；学生本人即便出现在助教串里也不收教师通知
        when(recipientResolver.resolveTeacherRecipients(1L, 2L, course))
                .thenReturn(new LinkedHashSet<>(List.of(10L, 20L, 30L, 3L)));
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(3L);
            service.studentAdd(1L, 2L, "第 3 题怎么做");
        }

        verify(businessNotifyService).sendToUsers(
                argThat((java.util.Collection<Long> ids) -> List.copyOf(ids).equals(List.of(10L, 20L, 30L))),
                eq(EduBusinessNotifyService.TEACHER_MESSAGE_CREATED),
                argThat((Map<String, Object> params) -> Long.valueOf(1L).equals(params.get("classId"))
                        && Long.valueOf(2L).equals(params.get("courseId"))
                        && Long.valueOf(3L).equals(params.get("studentId"))
                        && "第 3 题怎么做".equals(params.get("content"))),
                eq("/edu/message?classId=1&courseId=2&studentId=3"));
        verify(businessNotifyService, never()).sendToUser(any(), any(), any(), any());
    }

    @Test
    void studentMessageWithoutAnyTeacherSendsNothing() {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(2L);
        when(courseMapper.getCourse(2L)).thenReturn(course);
        when(messageMapper.existsStudentConversation(3L, 1L, 2L)).thenReturn(true);
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(recipientResolver.resolveTeacherRecipients(1L, 2L, course)).thenReturn(Set.of());
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(3L);
            service.studentAdd(1L, 2L, "留言");
        }

        verify(messageMapper).insert(any(EduMessageDO.class));
        verifyNoInteractions(businessNotifyService);
    }

    @Test
    void teacherReplyNotificationCarriesConversationParams() {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(2L);
        course.setCourseName("Java 实训");
        when(courseMapper.getCourse(2L)).thenReturn(course);
        when(classStudentMapper.selectListByClassIdAndStudentIds(eq(1L), any()))
                .thenReturn(List.of(new EduClassStudentDO()));
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());

        service.reply(1L, 2L, 3L, "看第 2 节");

        verify(businessNotifyService).sendToUser(eq(3L), eq(EduBusinessNotifyService.STUDENT_MESSAGE_REPLY),
                argThat(params -> Long.valueOf(1L).equals(params.get("classId"))
                        && Long.valueOf(2L).equals(params.get("courseId"))
                        && !params.containsKey("studentId")
                        && "Java 实训".equals(params.get("courseName"))),
                eq("/student/message?classId=1&courseId=2"));
    }

    @Test
    void openingConversationAlsoMarksRelatedNotificationsRead() {
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(messageMapper.existsStudentConversation(3L, 1L, 2L)).thenReturn(true);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(15L);
            service.getDetail(1L, 2L, 3L);
            verify(notifyReadService).markTeacherConversationRead(15L, 1L, 2L, 3L);

            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(3L);
            service.getMyDetail(1L, 2L);
            service.markMyRead(1L, 2L);
            verify(notifyReadService, times(2)).markStudentConversationRead(3L, 1L, 2L);
        }
    }

    @Test
    void forbiddenTeacherDoesNotTouchNotifications() {
        doThrow(exception(FORBIDDEN)).when(dataScopeService).validateClassCourseAccess(1L, 2L);

        assertServiceException(() -> service.getDetail(1L, 2L, 3L), FORBIDDEN);

        verifyNoInteractions(notifyReadService);
    }

    @Test
    void teacherStudentListBatchesNamesKeepsFallbackFilterAndSummaryOrder() {
        var first = new MessageStudentListRespVO();
        first.setStudentId(4L); first.setUnreadCount(2); first.setLastMessageContent("最新回复");
        var second = new MessageStudentListRespVO();
        second.setStudentId(3L); second.setUnreadCount(0);
        var third = new MessageStudentListRespVO();
        third.setStudentId(5L); third.setUnreadCount(1);
        when(messageMapper.selectTeacherStudentSummaries(1L,2L)).thenReturn(List.of(first,second,third));
        var named = new AdminUserRespDTO(); named.setNickname("张同学");
        var blank = new AdminUserRespDTO(); blank.setNickname("  ");
        when(adminUserApi.getUserMap(List.of(4L,3L,5L))).thenReturn(Map.of(4L,named,5L,blank));
        var result = service.getStudentList(1L,2L," ");
        assertEquals(List.of(4L,3L,5L), result.stream().map(row -> row.getStudentId()).toList());
        assertEquals(List.of("张同学","学生","学生"), result.stream().map(row -> row.getStudentName()).toList());
        assertEquals("最新回复", result.get(0).getLastMessageContent());
        assertEquals(List.of(first), service.getStudentList(1L,2L,"同学"));
        verify(adminUserApi, times(2)).getUserMap(List.of(4L,3L,5L));
        verify(adminUserApi, never()).getUser(any());
        verify(messageMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void emptyTeacherStudentListSkipsUserQuery() {
        when(messageMapper.selectTeacherStudentSummaries(1L,2L)).thenReturn(List.of());
        assertEquals(List.of(), service.getStudentList(1L,2L,null));
        verifyNoInteractions(adminUserApi);
    }

    private static List<MessageStudentListRespVO> summaries(
            List<EduMessageDO> messages) {
        if (messages.isEmpty()) return List.of();
        var row = new MessageStudentListRespVO();
        row.setStudentId(3L);
        row.setUnreadCount((int) messages.stream().filter(m -> m.getType() == 0
                && !Integer.valueOf(1).equals(m.getReadStatus())).count());
        return List.of(row);
    }

    private static EduMessageDO message(Long id, int type, int status, int readStatus) {
        EduMessageDO item = new EduMessageDO();
        item.setId(id);
        item.setStudentId(3L);
        item.setType(type);
        item.setStatus(status);
        item.setReadStatus(readStatus);
        return item;
    }
}
