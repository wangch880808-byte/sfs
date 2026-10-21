package cn.nus.zknu.module.edu.service.message;

import cn.nus.zknu.framework.common.enums.UserTypeEnum;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.dal.dataobject.notify.NotifyMessageDO;
import cn.nus.zknu.module.system.dal.mysql.notify.NotifyMessageMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EduMessageNotifyReadServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduMessageNotifyReadService service;
    @Mock
    private NotifyMessageMapper notifyMessageMapper;

    @BeforeAll
    static void initializeTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                NotifyMessageDO.class);
    }

    @Test
    void matchesExplicitConversationParams() {
        Map<String, Object> params = Map.of("classId", 7, "courseId", 5L, "studentId", "16");

        assertTrue(EduMessageNotifyReadService.matchesConversation(params, 7L, 5L, 16L));
        assertTrue(EduMessageNotifyReadService.matchesConversation(params, 7L, 5L, null));
        assertFalse(EduMessageNotifyReadService.matchesConversation(params, 7L, 5L, 17L));
        assertFalse(EduMessageNotifyReadService.matchesConversation(params, 7L, 6L, 16L));
        assertFalse(EduMessageNotifyReadService.matchesConversation(params, 8L, 5L, 16L));
    }

    @Test
    void matchesLegacyNotificationsByTargetUrl() {
        Map<String, Object> teacher = Map.of("targetUrl", "/edu/message?classId=7&courseId=5&studentId=16");
        Map<String, Object> student = Map.of("targetUrl", "/student/message?classId=7&courseId=5");

        assertTrue(EduMessageNotifyReadService.matchesConversation(teacher, 7L, 5L, 16L));
        assertFalse(EduMessageNotifyReadService.matchesConversation(teacher, 7L, 5L, 18L));
        assertTrue(EduMessageNotifyReadService.matchesConversation(student, 7L, 5L, null));
        // 学生通知不带 studentId，教师口径（要比对学生）不能误配
        assertFalse(EduMessageNotifyReadService.matchesConversation(student, 7L, 5L, 16L));
        // 前缀相同的另一门课程不能误配
        assertFalse(EduMessageNotifyReadService.matchesConversation(
                Map.of("targetUrl", "/student/message?classId=7&courseId=55"), 7L, 5L, null));
    }

    @Test
    void ignoresMissingOrMalformedParams() {
        assertFalse(EduMessageNotifyReadService.matchesConversation(null, 7L, 5L, null));
        assertFalse(EduMessageNotifyReadService.matchesConversation(Map.of(), 7L, 5L, null));
        assertFalse(EduMessageNotifyReadService.matchesConversation(
                Map.of("targetUrl", "/student/message?classId=%zz&courseId=5"), 7L, 5L, null));
        assertFalse(EduMessageNotifyReadService.matchesConversation(
                Map.of("classId", "seven", "courseId", "5"), 7L, 5L, null));
        Map<String, Object> nullValues = new HashMap<>();
        nullValues.put("classId", null);
        nullValues.put("targetUrl", null);
        assertFalse(EduMessageNotifyReadService.matchesConversation(nullValues, 7L, 5L, null));
    }

    @Test
    void teacherOpeningConversationMarksOnlyThatConversationsNotificationsRead() {
        when(notifyMessageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                notify(1L, Map.of("targetUrl", "/edu/message?classId=7&courseId=5&studentId=16")),
                notify(2L, Map.of("classId", 7, "courseId", 5, "studentId", 16)),
                notify(3L, Map.of("targetUrl", "/edu/message?classId=7&courseId=5&studentId=99")),
                notify(4L, null)));
        when(notifyMessageMapper.updateListRead(anyCollection(), eq(15L), eq(UserTypeEnum.ADMIN.getValue())))
                .thenReturn(2);

        assertEquals(2, service.markTeacherConversationRead(15L, 7L, 5L, 16L));

        verify(notifyMessageMapper).updateListRead(eq(List.of(1L, 2L)), eq(15L), eq(UserTypeEnum.ADMIN.getValue()));
    }

    @Test
    void studentOpeningConversationMarksReplyNotificationsRead() {
        when(notifyMessageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                notify(5L, Map.of("targetUrl", "/student/message?classId=7&courseId=5"))));

        service.markStudentConversationRead(16L, 7L, 5L);

        verify(notifyMessageMapper).updateListRead(eq(List.of(5L)), eq(16L), eq(UserTypeEnum.ADMIN.getValue()));
    }

    @Test
    void nothingToMarkSkipsUpdate() {
        when(notifyMessageMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertEquals(0, service.markStudentConversationRead(16L, 7L, 5L));

        verify(notifyMessageMapper, never()).updateListRead(anyCollection(), any(), any());
    }

    @Test
    void failuresDoNotBreakOpeningTheConversation() {
        when(notifyMessageMapper.selectList(any(Wrapper.class))).thenThrow(new IllegalStateException("db down"));

        assertEquals(0, service.markTeacherConversationRead(15L, 7L, 5L, 16L));
        assertEquals(0, service.markTeacherConversationRead(null, 7L, 5L, 16L));
    }

    private static NotifyMessageDO notify(Long id, Map<String, Object> params) {
        NotifyMessageDO message = new NotifyMessageDO();
        message.setId(id);
        message.setTemplateParams(params);
        message.setReadStatus(false);
        return message;
    }

}
