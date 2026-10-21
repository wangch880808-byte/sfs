package cn.nus.zknu.module.edu.service.classstudent;

import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.classstudent.vo.EduClassStudentPageReqVO;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.dataobject.homework.*;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.*;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.*;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.system.dal.dataobject.user.*;
import cn.nus.zknu.module.system.dal.mysql.user.*;
import cn.nus.zknu.module.system.service.dept.DeptService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.*;
import java.util.stream.LongStream;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EduClassStudentServiceImplTest extends BaseMockitoUnitTest {
    @InjectMocks private EduClassStudentServiceImpl service;
    @Mock private EduDeptScopeService deptScopeService;
    @Mock private EduClassMapper classMapper;
    @Mock private EduClassStudentMapper classStudentMapper;
    @Mock private EduClassStudentBatchMapper batchMapper;
    @Mock private AdminUserMapper adminUserMapper;
    @Mock private DeptService deptService;
    @Mock private UserProfileMapper userProfileMapper;
    @Mock private EduBusinessNotifyService businessNotifyService;
    @Mock private EduHomeworkMapper homeworkMapper;
    @Mock private EduHomeworkAssigneeMapper assigneeMapper;
    @Mock private EduHomeworkRecordMapper recordMapper;

    @BeforeAll
    static void initTables() {
        for (Class<?> type : List.of(AdminUserDO.class, EduHomeworkDO.class, EduHomeworkRecordDO.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }

    @Test
    void addFiftyStudentsToThreeTasksUsesConstantQueriesAndWrites() {
        List<Long> ids = LongStream.rangeClosed(1, 50).boxed().toList();
        prepareClass();
        when(adminUserMapper.selectByIds(ids)).thenReturn(ids.stream().map(this::student).toList());
        when(userProfileMapper.selectListByUserIds(ids)).thenReturn(ids.stream().map(this::profile).toList());
        when(classStudentMapper.selectListIncludingDeleted(10L, ids))
                .thenReturn(List.of(relation(1L, false), relation(2L, true)));
        when(batchMapper.restoreRelations(any(), eq(0), any(), eq("备注"))).thenReturn(1);
        when(homeworkMapper.selectList(any())).thenReturn(tasks());
        when(batchMapper.selectAssigneesIncludingDeleted(any(), any()))
                .thenReturn(List.of(assignment(100L, 40L, 2L, true), assignment(101L, 40L, 3L, false)));
        when(classStudentMapper.selectListByStudentIds(ids)).thenReturn(ids.stream().map(id -> relation(id, false)).toList());

        service.addStudents(10L, ids, "备注");

        verify(adminUserMapper, times(2)).selectByIds(ids);
        verify(deptScopeService, never()).validateUserInScope(any());
        verify(homeworkMapper).selectList(any());
        verify(batchMapper).selectAssigneesIncludingDeleted(eq(Set.of(40L, 41L, 42L)), any());
        verify(batchMapper).restoreAssignees(List.of(100L));
        ArgumentCaptor<List<EduHomeworkAssigneeDO>> assignments = ArgumentCaptor.forClass(List.class);
        verify(batchMapper).insertAssignees(assignments.capture(), any());
        assertEquals(145, assignments.getValue().size());
        assertTrue(assignments.getValue().stream().allMatch(row -> row.getClassId().equals(10L) && !row.getStudentId().equals(1L)));
        verifyNoInteractions(assigneeMapper, recordMapper);
        ArgumentCaptor<List<EduClassStudentDO>> relations = ArgumentCaptor.forClass(List.class);
        verify(batchMapper).insertRelations(relations.capture(), any());
        assertEquals(48, relations.getValue().size());
        ArgumentCaptor<List<UserProfileDO>> profiles = ArgumentCaptor.forClass(List.class);
        verify(batchMapper).updateProfiles(profiles.capture(), any());
        assertEquals(50, profiles.getValue().size());
        assertTrue(profiles.getValue().stream().allMatch(row -> row.getClassIds().equals(Set.of(10L)) && row.getGradeYear() == null));
        verify(userProfileMapper, never()).updateById(any(UserProfileDO.class));
        verify(businessNotifyService).sendToUsers(eq(new LinkedHashSet<>(ids.subList(1, 50))),
                eq(EduBusinessNotifyService.CLASS_JOINED), eq(Map.of("className", "软件班")), eq("/student/course"));
    }

    @Test
    void removeFiftyStudentsKeepsEveryRecordedAssignmentAndUsesConstantQueries() {
        List<Long> ids = LongStream.rangeClosed(1, 50).boxed().toList();
        prepareClass();
        when(homeworkMapper.selectList(any())).thenReturn(tasks());
        List<EduHomeworkAssigneeDO> assignments = new ArrayList<>();
        for (long task = 40; task <= 42; task++) {
            for (Long id : ids) assignments.add(assignment(task * 100 + id, task, id, false));
        }
        when(batchMapper.selectAssigneesIncludingDeleted(any(), eq(ids))).thenReturn(assignments);
        EduHomeworkRecordDO submitted = new EduHomeworkRecordDO();
        submitted.setHomeworkId(40L); submitted.setStudentId(2L); submitted.setStatus(2);
        EduHomeworkRecordDO notSubmitted = new EduHomeworkRecordDO();
        notSubmitted.setHomeworkId(41L); notSubmitted.setStudentId(3L); notSubmitted.setStatus(0);
        when(recordMapper.selectList(any())).thenReturn(List.of(submitted, notSubmitted));
        when(userProfileMapper.selectListByUserIds(ids)).thenReturn(ids.stream().map(this::profile).toList());

        service.removeStudents(10L, ids);

        verify(adminUserMapper).selectByIds(ids);
        verify(recordMapper).selectList(any());
        verify(recordMapper, never()).selectCount(any());
        verify(homeworkMapper).selectList(any());
        ArgumentCaptor<List<Long>> removed = ArgumentCaptor.forClass(List.class);
        verify(batchMapper).removeAssignees(removed.capture());
        assertEquals(148, removed.getValue().size());
        assertFalse(removed.getValue().contains(4002L));
        assertFalse(removed.getValue().contains(4103L));
        verify(classStudentMapper).deleteByClassIdAndStudentIds(10L, ids);
        ArgumentCaptor<List<UserProfileDO>> profiles = ArgumentCaptor.forClass(List.class);
        verify(batchMapper).updateProfiles(profiles.capture(), any());
        assertTrue(profiles.getValue().stream().allMatch(row -> row.getClassIds().isEmpty()));
        verifyNoInteractions(assigneeMapper, businessNotifyService);
    }

    @Test
    void missingProfileUsesClassGradeAndNoProfileIsCreatedAfterRemoval() {
        prepareClass();
        when(adminUserMapper.selectByIds(List.of(3L))).thenReturn(List.of(student(3L)));
        when(classStudentMapper.selectListByStudentIds(List.of(3L))).thenReturn(List.of(relation(3L, false)));
        service.addStudents(10L, List.of(3L), "备注");
        ArgumentCaptor<List<UserProfileDO>> profiles = ArgumentCaptor.forClass(List.class);
        verify(batchMapper).insertProfiles(profiles.capture(), any());
        assertEquals(2026, profiles.getValue().get(0).getGradeYear());
        assertEquals(Set.of(10L), profiles.getValue().get(0).getClassIds());
        clearInvocations(batchMapper);
        when(classStudentMapper.selectListByStudentIds(List.of(3L))).thenReturn(List.of());
        service.removeStudents(10L, List.of(3L));
        verify(batchMapper, never()).insertProfiles(any(), any());
    }

    @Test
    void restrictedMissingOrForeignStudentIsRejectedBeforeWrite() {
        prepareClass();
        when(deptScopeService.isRestricted()).thenReturn(true);
        assertServiceException(() -> service.removeStudents(10L, List.of(99L)), FORBIDDEN);
        AdminUserDO foreign = student(99L); foreign.setDeptId(190L);
        when(adminUserMapper.selectByIds(List.of(99L))).thenReturn(List.of(foreign));
        doNothing().when(deptScopeService).validateDeptInScope(180L);
        doThrow(new ServiceException(FORBIDDEN)).when(deptScopeService).validateDeptInScope(190L);
        assertServiceException(() -> service.addStudents(10L, List.of(99L), "备注"), FORBIDDEN);
        verifyNoInteractions(classStudentMapper, batchMapper);
    }

    @Test
    void studentTypeStatusRemarkAndClassScopeStillValidated() {
        prepareClass();
        assertServiceException(() -> service.addStudents(10L, List.of(99L), "备注"), CLASS_STUDENT_NOT_EXISTS);
        AdminUserDO student = student(3L); student.setUserType(1);
        when(adminUserMapper.selectByIds(List.of(3L))).thenReturn(List.of(student));
        assertServiceException(() -> service.addStudents(10L, List.of(3L), "备注"), CLASS_STUDENT_NOT_STUDENT);
        student.setUserType(2); student.setStatus(1); student.setNickname("学生");
        assertServiceException(() -> service.addStudents(10L, List.of(3L), "备注"), CLASS_STUDENT_IS_DISABLED, "学生");
        student.setStatus(0);
        assertServiceException(() -> service.addStudents(10L, List.of(3L), " "), CLASS_STUDENT_REMARK_REQUIRED);
        doThrow(new ServiceException(FORBIDDEN)).when(deptScopeService).validateClassInScope(99L);
        assertServiceException(() -> service.addStudents(99L, List.of(3L), "备注"), FORBIDDEN);
        verifyNoInteractions(batchMapper);
    }

    @Test
    void availablePageUsesProfileExistsAndCachedDepartmentSubtreeWithContainsSearch() {
        prepareClass();
        when(deptService.getChildDeptIdListFromCache(180L)).thenReturn(Set.of(181L));
        EduClassStudentPageReqVO req = new EduClassStudentPageReqVO();
        req.setClassId(10L); req.setDeptId(180L); req.setGradeYear(2026); req.setKeyword("123");
        service.getAvailableStudentPage(req);
        ArgumentCaptor<cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX<AdminUserDO>> query = ArgumentCaptor.forClass(cn.nus.zknu.framework.mybatis.core.query.LambdaQueryWrapperX.class);
        verify(adminUserMapper).selectPage(eq(req), query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("EXISTS (SELECT 1 FROM system_user_profile"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("%123%"));
        verify(deptService).getChildDeptIdListFromCache(180L);
        verifyNoInteractions(userProfileMapper);
    }

    @Test
    void membershipJoinReusesSnapshotAndNotificationWithoutRewritingRelationsOrProfile() {
        prepareClass();
        when(homeworkMapper.selectList(any())).thenReturn(tasks());

        service.syncAssigneesForMembershipChange(10L, List.of(3L), true);

        ArgumentCaptor<List<EduHomeworkAssigneeDO>> assignments = ArgumentCaptor.forClass(List.class);
        verify(batchMapper).insertAssignees(assignments.capture(), any());
        assertEquals(3, assignments.getValue().size());
        assertTrue(assignments.getValue().stream().allMatch(row -> row.getStudentId().equals(3L)
                && row.getClassId().equals(10L)));
        verify(businessNotifyService).sendToUsers(List.of(3L), EduBusinessNotifyService.CLASS_JOINED,
                Map.of("className", "软件班"), "/student/course");
        verifyNoInteractions(classStudentMapper, userProfileMapper, adminUserMapper, recordMapper);
        verify(batchMapper, never()).insertProfiles(any(), any());
        verify(batchMapper, never()).updateProfiles(any(), any());
    }

    @Test
    void membershipJoinWithoutOpenTasksStillSendsClassJoinedNotification() {
        prepareClass();

        service.syncAssigneesForMembershipChange(10L, List.of(3L), true);

        verify(businessNotifyService).sendToUsers(List.of(3L), EduBusinessNotifyService.CLASS_JOINED,
                Map.of("className", "软件班"), "/student/course");
        verifyNoInteractions(batchMapper, classStudentMapper, userProfileMapper);
    }

    @Test
    void membershipRemovalRetainsRecordedAssignmentsWithoutSendingNotifications() {
        when(homeworkMapper.selectList(any())).thenReturn(tasks());
        when(batchMapper.selectAssigneesIncludingDeleted(any(), any())).thenReturn(List.of(
                assignment(1L, 40L, 3L, false), assignment(2L, 41L, 3L, false)));
        EduHomeworkRecordDO record = new EduHomeworkRecordDO();
        record.setHomeworkId(40L); record.setStudentId(3L); record.setStatus(0);
        when(recordMapper.selectList(any())).thenReturn(List.of(record));

        service.syncAssigneesForMembershipChange(10L, List.of(3L), false);

        verify(batchMapper).removeAssignees(List.of(2L));
        verifyNoInteractions(classMapper, classStudentMapper, userProfileMapper, businessNotifyService);
    }

    @Test
    void emptyMembershipChangeDoesNotQueryOrNotify() {
        service.syncAssigneesForMembershipChange(10L, List.of(), true);
        service.syncAssigneesForMembershipChange(10L, null, false);

        verifyNoInteractions(homeworkMapper, batchMapper, classMapper, classStudentMapper,
                userProfileMapper, businessNotifyService);
    }

    private void prepareClass() {
        EduClassDO clazz = new EduClassDO(); clazz.setId(10L); clazz.setDeptId(180L);
        clazz.setGradeYear(2026); clazz.setClassName("软件班");
        when(classMapper.selectById(10L)).thenReturn(clazz);
    }
    private AdminUserDO student(Long id) {
        AdminUserDO user = new AdminUserDO(); user.setId(id); user.setDeptId(180L); user.setUserType(2); user.setStatus(0);
        return user;
    }
    private UserProfileDO profile(Long id) {
        UserProfileDO profile = new UserProfileDO(); profile.setId(id + 1000); profile.setUserId(id); profile.setGradeYear(2026);
        return profile;
    }
    private EduClassStudentDO relation(Long studentId, boolean deleted) {
        EduClassStudentDO relation = new EduClassStudentDO(); relation.setId(studentId); relation.setClassId(10L);
        relation.setStudentId(studentId); relation.setDeleted(deleted); return relation;
    }
    private EduHomeworkAssigneeDO assignment(Long id, Long task, Long student, boolean deleted) {
        EduHomeworkAssigneeDO row = new EduHomeworkAssigneeDO(); row.setId(id); row.setHomeworkId(task);
        row.setStudentId(student); row.setDeleted(deleted); return row;
    }
    private List<EduHomeworkDO> tasks() {
        return LongStream.rangeClosed(40, 42).mapToObj(id -> { EduHomeworkDO task = new EduHomeworkDO(); task.setId(id); return task; }).toList();
    }
}
