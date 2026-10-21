package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkExtensionSetReqVO;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.EduHomeworkExtensionStudentRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.*;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkExtensionMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkRecordMapper;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import cn.nus.zknu.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EduHomeworkExtensionServiceImplTest extends BaseMockitoUnitTest {

    private static final Long TEACHER_ID = 7L;
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @BeforeAll
    static void initTableInfo() {
        for (Class<?> type : List.of(EduHomeworkAssigneeDO.class, EduHomeworkRecordDO.class,
                EduHomeworkExtensionDO.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }

    @InjectMocks
    private EduHomeworkExtensionServiceImpl service;
    @Mock
    private EduHomeworkMapper homeworkMapper;
    @Mock
    private EduHomeworkExtensionMapper extensionMapper;
    @Mock
    private EduHomeworkAssigneeMapper assigneeMapper;
    @Mock
    private EduHomeworkRecordMapper recordMapper;
    @Mock
    private AdminUserApi adminUserApi;
    @Mock
    private EduBusinessNotifyService businessNotifyService;
    @Mock
    private cn.nus.zknu.module.edu.service.aireview.EduAiBusinessAccessService businessAccessService;

    private MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach
    void loginAsTeacher() {
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(TEACHER_ID);
    }

    @AfterEach
    void logout() {
        security.close();
    }

    @Test
    void setExtensions_insertsNewAndUpdatesExistingThenNotifiesChangedStudents() {
        LocalDateTime taskDeadline = LocalDateTime.now().minusHours(1);
        LocalDateTime deadline = LocalDateTime.now().plusDays(2).withSecond(0).withNano(0);
        when(homeworkMapper.selectById(1L)).thenReturn(homework(taskDeadline));
        when(assigneeMapper.selectList(any())).thenReturn(assignees(10L, 11L, 12L));
        when(recordMapper.selectList(any())).thenReturn(List.of());
        EduHomeworkExtensionDO existing = extension(5L, 11L, deadline.minusDays(1), "病假");
        EduHomeworkExtensionDO unchanged = extension(6L, 12L, deadline, "病假");
        when(extensionMapper.selectList(any())).thenReturn(List.of(existing, unchanged));

        int count = service.setExtensions(request(deadline.plusSeconds(42), " 病假 ", 10L, 11L, 12L, 10L));

        assertEquals(3, count);
        ArgumentCaptor<EduHomeworkExtensionDO> inserted = ArgumentCaptor.forClass(EduHomeworkExtensionDO.class);
        verify(extensionMapper).insert(inserted.capture());
        assertEquals(10L, inserted.getValue().getStudentId());
        assertEquals(deadline, inserted.getValue().getDeadline());
        assertEquals("病假", inserted.getValue().getReason());
        assertEquals(TEACHER_ID, inserted.getValue().getOperatorId());
        ArgumentCaptor<EduHomeworkExtensionDO> updated = ArgumentCaptor.forClass(EduHomeworkExtensionDO.class);
        verify(extensionMapper).updateById(updated.capture());
        assertEquals(5L, updated.getValue().getId());
        assertEquals(deadline, updated.getValue().getDeadline());
        verify(businessNotifyService).sendToUsers(eq(List.of(10L, 11L)), eq(EduBusinessNotifyService.HOMEWORK_EXTENDED),
                eq(Map.of("homeworkName", "朗读", "deadline", deadline.format(FORMAT), "reason", "病假")),
                eq("/student/homework/submit/1"));
    }

    @Test
    void setExtensions_concurrentInsertFallsBackToUpdate() {
        LocalDateTime deadline = LocalDateTime.now().plusDays(2).withSecond(0).withNano(0);
        when(homeworkMapper.selectById(1L)).thenReturn(homework(LocalDateTime.now().plusHours(1)));
        when(assigneeMapper.selectList(any())).thenReturn(assignees(10L));
        when(extensionMapper.insert(any(EduHomeworkExtensionDO.class))).thenThrow(new DuplicateKeyException("dup"));
        when(extensionMapper.selectByHomeworkIdAndStudentId(1L, 10L))
                .thenReturn(extension(8L, 10L, deadline.minusDays(1), ""));

        service.setExtensions(request(deadline, null, 10L));

        verify(extensionMapper).updateById(argThat((EduHomeworkExtensionDO item) -> item.getId().equals(8L)
                && item.getDeadline().equals(deadline) && item.getReason().isEmpty()));
        verify(businessNotifyService).sendToUsers(eq(List.of(10L)), any(),
                argThat(params -> "无".equals(params.get("reason"))), any());
    }

    @Test
    void setExtensions_deadlineMustBeLaterThanTaskDeadlineAndNow() {
        LocalDateTime taskDeadline = LocalDateTime.now().plusDays(1);
        when(homeworkMapper.selectById(1L)).thenReturn(homework(taskDeadline));

        assertServiceException(() -> service.setExtensions(request(taskDeadline.minusHours(1), null, 10L)),
                HOMEWORK_EXTENSION_DEADLINE_INVALID, taskDeadline.format(FORMAT));

        EduHomeworkDO past = homework(LocalDateTime.now().minusDays(3));
        when(homeworkMapper.selectById(1L)).thenReturn(past);
        assertServiceException(() -> service.setExtensions(request(LocalDateTime.now().minusDays(1), null, 10L)),
                HOMEWORK_EXTENSION_DEADLINE_INVALID, past.getDeadline().format(FORMAT));
        verify(extensionMapper, never()).insert(any(EduHomeworkExtensionDO.class));
    }

    @Test
    void setExtensions_closedHomeworkIsRejectedBeforeWritesOrNotifications() {
        EduHomeworkDO closed = homework(LocalDateTime.now().minusDays(1));
        closed.setStatus(cn.nus.zknu.module.edu.enums.homework.EduHomeworkStatusEnum.CLOSED);
        when(homeworkMapper.selectById(1L)).thenReturn(closed);

        assertServiceException(() -> service.setExtensions(request(LocalDateTime.now().plusDays(1), null, 10L)),
                HOMEWORK_EXTENSION_STATUS_INVALID);

        verifyNoInteractions(extensionMapper, assigneeMapper, recordMapper, businessNotifyService);
    }

    @Test
    void setExtensions_taskWithoutDeadlineOrNotPublishedIsRejected() {
        EduHomeworkDO unlimited = homework(null);
        when(homeworkMapper.selectById(1L)).thenReturn(unlimited);
        assertServiceException(() -> service.setExtensions(request(LocalDateTime.now().plusDays(1), null, 10L)),
                HOMEWORK_EXTENSION_NO_DEADLINE);

        EduHomeworkDO archived = homework(LocalDateTime.now().plusDays(1));
        archived.setStatus(3);
        when(homeworkMapper.selectById(1L)).thenReturn(archived);
        assertServiceException(() -> service.setExtensions(request(LocalDateTime.now().plusDays(2), null, 10L)),
                HOMEWORK_EXTENSION_STATUS_INVALID);
    }

    @Test
    void setExtensions_studentsOutsideTheTaskOrAlreadySubmittedAreRejected() {
        LocalDateTime deadline = LocalDateTime.now().plusDays(2);
        when(homeworkMapper.selectById(1L)).thenReturn(homework(LocalDateTime.now().plusHours(1)));
        when(assigneeMapper.selectList(any())).thenReturn(assignees(10L), assignees(10L, 11L));
        assertServiceException(() -> service.setExtensions(request(deadline, null, 10L, 11L, 12L)),
                HOMEWORK_EXTENSION_STUDENT_NOT_ASSIGNED, 2L);

        EduHomeworkRecordDO submitted = new EduHomeworkRecordDO();
        submitted.setStudentId(11L);
        submitted.setStatus(1);
        when(recordMapper.selectList(any())).thenReturn(List.of(submitted));
        assertServiceException(() -> service.setExtensions(request(deadline, null, 10L, 11L)),
                HOMEWORK_EXTENSION_STUDENT_SUBMITTED, 1);
        verify(extensionMapper, never()).insert(any(EduHomeworkExtensionDO.class));
        verifyNoInteractions(businessNotifyService);
    }

    @Test
    void otherTeacherCannotSeeOrChangeExtensions() {
        EduHomeworkDO homework = homework(LocalDateTime.now().plusDays(1));
        homework.setTeacherId(99L);
        when(homeworkMapper.selectById(1L)).thenReturn(homework);

        doThrow(cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception(
                cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN))
                .when(businessAccessService).requireHomeworkViewAccess(1L);
        assertServiceException(() -> service.listExtensions(1L),
                cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN);
        assertServiceException(() -> service.listStudents(1L), HOMEWORK_NOT_EXISTS);
        assertServiceException(() -> service.setExtensions(request(LocalDateTime.now().plusDays(2), null, 10L)),
                HOMEWORK_NOT_EXISTS);
        when(extensionMapper.selectById(5L)).thenReturn(extension(5L, 10L, LocalDateTime.now(), ""));
        assertServiceException(() -> service.deleteExtension(5L), HOMEWORK_NOT_EXISTS);
        verify(extensionMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void deleteExtension_softDeletesOwnedExtension() {
        when(extensionMapper.selectById(5L)).thenReturn(extension(5L, 10L, LocalDateTime.now(), ""));
        when(homeworkMapper.selectById(1L)).thenReturn(homework(LocalDateTime.now()));

        service.deleteExtension(5L);

        verify(extensionMapper).deleteById(5L);
        assertThrows(ServiceException.class, () -> service.deleteExtension(6L));
    }

    @Test
    void listStudents_marksSubmissionStatusAndPersonalDeadline() {
        LocalDateTime personal = LocalDateTime.now().plusDays(1);
        when(homeworkMapper.selectById(1L)).thenReturn(homework(LocalDateTime.now()));
        when(assigneeMapper.selectListByHomeworkId(1L)).thenReturn(assignees(10L, 11L));
        EduHomeworkRecordDO record = new EduHomeworkRecordDO();
        record.setStudentId(11L);
        record.setStatus(2);
        when(recordMapper.selectList(any())).thenReturn(List.of(record));
        when(extensionMapper.selectListByHomeworkId(1L)).thenReturn(List.of(extension(5L, 10L, personal, "")));
        when(adminUserApi.getUserMap(any())).thenReturn(Map.of(10L, user(10L, "2025002", "赵一鸣"),
                11L, user(11L, "2025001", "钱二")));

        List<EduHomeworkExtensionStudentRespVO> students = service.listStudents(1L);

        assertEquals(List.of(11L, 10L), students.stream().map(EduHomeworkExtensionStudentRespVO::getStudentId).toList());
        assertEquals(2, students.get(0).getRecordStatus());
        assertNull(students.get(0).getPersonalDeadline());
        assertEquals(0, students.get(1).getRecordStatus());
        assertEquals(personal, students.get(1).getPersonalDeadline());
        assertEquals("赵一鸣", students.get(1).getStudentName());
    }

    private static EduHomeworkExtensionSetReqVO request(LocalDateTime deadline, String reason, Long... studentIds) {
        EduHomeworkExtensionSetReqVO reqVO = new EduHomeworkExtensionSetReqVO();
        reqVO.setHomeworkId(1L);
        reqVO.setStudentIds(Arrays.asList(studentIds));
        reqVO.setDeadline(deadline);
        reqVO.setReason(reason);
        return reqVO;
    }

    private static EduHomeworkDO homework(LocalDateTime deadline) {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setId(1L);
        homework.setHomeworkName("朗读");
        homework.setTeacherId(TEACHER_ID);
        homework.setStatus(1);
        homework.setDeadline(deadline);
        return homework;
    }

    private static List<EduHomeworkAssigneeDO> assignees(Long... studentIds) {
        return Arrays.stream(studentIds).map(studentId -> {
            EduHomeworkAssigneeDO assignee = new EduHomeworkAssigneeDO();
            assignee.setHomeworkId(1L);
            assignee.setStudentId(studentId);
            return assignee;
        }).toList();
    }

    private static EduHomeworkExtensionDO extension(Long id, Long studentId, LocalDateTime deadline, String reason) {
        EduHomeworkExtensionDO extension = new EduHomeworkExtensionDO();
        extension.setId(id);
        extension.setHomeworkId(1L);
        extension.setStudentId(studentId);
        extension.setDeadline(deadline);
        extension.setReason(reason);
        return extension;
    }

    private static AdminUserRespDTO user(Long id, String username, String nickname) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(nickname);
        return user;
    }

}
