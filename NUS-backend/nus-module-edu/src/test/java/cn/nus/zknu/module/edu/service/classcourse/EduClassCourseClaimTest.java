package cn.nus.zknu.module.edu.service.classcourse;

import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.security.core.LoginUser;
import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.controller.admin.classcourse.vo.*;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterOptionRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.edu.service.semester.EduSemesterOptionService;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import cn.nus.zknu.module.system.api.user.dto.AdminUserRespDTO;
import cn.nus.zknu.module.system.api.dept.DeptApi;
import cn.nus.zknu.module.system.api.dept.dto.DeptRespDTO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;
import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EduClassCourseClaimTest extends BaseMockitoUnitTest {
    @InjectMocks private EduClassCourseServiceImpl service;
    @Mock private EduClassCourseMapper eduClassCourseMapper;
    @Mock private EduClassMapper eduClassMapper;
    @Mock private CapacityCourseApi capacityCourseApi;
    @Mock private EduDataScopeService dataScopeService;
    @Mock private EduSemesterOptionService semesterOptionService;
    @Mock private EduRoleApi eduRoleApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private EduPublicCourseBackfillService backfillService;
    @Mock private PlatformTransactionManager transactionManager;

    @BeforeAll static void tables() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, EduClassCourseDO.class);
        TableInfoHelper.initTableInfo(assistant, EduClassDO.class);
    }
    @BeforeEach void setup() {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setUserType(2), new MockHttpServletRequest());
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setIsPublicCourse(0); course.setPublicAuditStatus(2); course.setStatus(0);
        lenient().when(capacityCourseApi.getCourse(2L)).thenReturn(course);
        lenient().when(eduRoleApi.isCurrentUserTeacher()).thenReturn(true);
        lenient().when(dataScopeService.isPublicCourse(2L)).thenReturn(true);
        EduSemesterOptionRespVO option = new EduSemesterOptionRespVO();
        option.setSemesterName(" S "); option.setSemesterStartDate(LocalDate.of(2026, 9, 1));
        lenient().when(semesterOptionService.resolve(null)).thenReturn(option);
        lenient().when(eduClassMapper.selectById(1L)).thenReturn(clazz(1L, "一班"));
        lenient().when(eduClassMapper.selectById(3L)).thenReturn(clazz(3L, "三班"));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private EduClassCourseClaimReqVO request(Long... ids) {
        var req = new EduClassCourseClaimReqVO(); req.setCourseId(2L); req.setClassIds(List.of(ids)); return req;
    }
    private EduClassDO clazz(Long id, String name) {
        var c = new EduClassDO(); c.setId(id); c.setClassName(name); c.setStatus(0); return c;
    }
    private EduClassCourseDO link(Long id, Long classId, Long owner) {
        var row = new EduClassCourseDO(); row.setId(id); row.setCourseId(2L); row.setClassId(classId);
        row.setClaimTeacherId(owner); row.setStatus(0); return row;
    }
    @Test void unclaimedRowUsesConditionalUpdateAndBackfills() {
        when(eduClassCourseMapper.selectClaimRelation(2L, 1L, "S")).thenReturn(link(11L, 1L, null));
        when(eduClassCourseMapper.claim(11L, 7L)).thenReturn(1);
        service.claimClassCourse(request(1L, 1L));
        var order = inOrder(eduClassCourseMapper, backfillService);
        order.verify(eduClassCourseMapper).lockPublicCourse(2L);
        order.verify(eduClassCourseMapper).selectClaimRelation(2L, 1L, "S");
        order.verify(eduClassCourseMapper).claim(11L, 7L);
        order.verify(backfillService).backfillForClaim(2L, "S", 7L, 1L, Set.of(1L));
        verify(eduClassCourseMapper, never()).updateById(any(EduClassCourseDO.class));
    }
    @Test void missingRowIsSystemCreatedWithConfiguredDates() {
        service.claimClassCourse(request(1L));
        verify(eduClassCourseMapper).insert(argThat((EduClassCourseDO row) ->
                row.getClaimTeacherId().equals(7L) && row.getClaimTime() != null && row.getStatus() == 0
                && row.getSemesterName().equals("S") && row.getSemesterStartDate().equals(LocalDate.of(2026, 9, 1))
                && !row.getBackfillPending() && row.getCreator().equals("system") && row.getUpdater().equals("system")));
        verify(backfillService).backfillForClaim(2L, "S", 7L, 1L, Set.of(1L));
    }
    @Test void alreadyMineSkipsUpdateAndBackfillEvenWhenArchived() {
        var row = link(11L, 1L, 7L); row.setStatus(1);
        when(eduClassCourseMapper.selectClaimRelation(2L, 1L, "S")).thenReturn(row);
        service.claimClassCourse(request(1L));
        verify(eduClassCourseMapper, never()).claim(any(), any()); verifyNoInteractions(backfillService);
    }
    @Test void archivedUnclaimedRowGoesThroughRestoringSql() {
        var row = link(11L, 1L, null); row.setStatus(1); row.setUpdater("system-sync");
        when(eduClassCourseMapper.selectClaimRelation(2L, 1L, "S")).thenReturn(row);
        when(eduClassCourseMapper.claimArchived(11L, 7L)).thenReturn(1);
        service.claimClassCourse(request(1L)); verify(eduClassCourseMapper).claimArchived(11L, 7L);
    }
    @Test void duplicateInsertRechecksAndClaimsExistingRow() {
        when(eduClassCourseMapper.selectClaimRelation(2L, 1L, "S")).thenReturn(null, link(11L, 1L, null));
        when(eduClassCourseMapper.insert(any(EduClassCourseDO.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("重复"));
        when(eduClassCourseMapper.claim(11L, 7L)).thenReturn(1);
        service.claimClassCourse(request(1L)); verify(eduClassCourseMapper).claim(11L, 7L);
    }
    @Test void zeroAffectedRowsReportsConcurrentOwner() {
        when(eduClassCourseMapper.selectClaimRelation(2L, 1L, "S"))
                .thenReturn(link(11L, 1L, null), link(11L, 1L, 8L));
        var teacher = new AdminUserRespDTO(); teacher.setNickname("张"); when(adminUserApi.getUser(8L)).thenReturn(teacher);
        var ex = assertThrows(ServiceException.class, () -> service.claimClassCourse(request(1L)));
        assertEquals("班级「一班」已被张老师认领", ex.getMessage()); verifyNoInteractions(backfillService);
    }
    @Test void conflictRollsBackWholeBatchBeforeBackfill() {
        when(eduClassCourseMapper.selectClaimRelation(2L, 1L, "S")).thenReturn(link(11L, 1L, null));
        when(eduClassCourseMapper.claim(11L, 7L)).thenReturn(1);
        when(eduClassCourseMapper.selectClaimRelation(2L, 3L, "S")).thenReturn(link(13L, 3L, 8L));
        var status = mock(TransactionStatus.class); when(transactionManager.getTransaction(any())).thenReturn(status);
        ProxyFactory proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        var transactional = (EduClassCourseService) proxy.getProxy();
        assertThrows(ServiceException.class, () -> transactional.claimClassCourse(request(1L, 3L)));
        verify(transactionManager).rollback(status); verify(transactionManager, never()).commit(any());
        verifyNoInteractions(backfillService);
    }
    @Test void offlineAndStudentRejectedBeforeLock() {
        when(capacityCourseApi.getCourse(2L)).thenReturn(new CapacityCourseRespDTO());
        assertEquals(CLASS_COURSE_CLAIM_OFFLINE.getCode(), assertThrows(ServiceException.class,
                () -> service.claimClassCourse(request(1L))).getCode());
        verifyNoInteractions(eduClassCourseMapper);
    }
    @Test void studentRejectedAndAdminCanClaim() {
        when(eduRoleApi.isCurrentUserTeacher()).thenReturn(false);
        assertEquals(CLASS_COURSE_CLAIM_TEACHER_REQUIRED.getCode(), assertThrows(ServiceException.class,
                () -> service.claimClassCourse(request(1L))).getCode());
        when(dataScopeService.isGlobalAdmin()).thenReturn(true);
        service.claimClassCourse(request(1L)); verify(eduClassCourseMapper).insert(any(EduClassCourseDO.class));
    }
    @Test void missingAndArchivedClassesRejected() {
        when(eduClassMapper.selectById(1L)).thenReturn(null);
        assertEquals(CLASS_NOT_EXISTS.getCode(), assertThrows(ServiceException.class,
                () -> service.claimClassCourse(request(1L))).getCode());
        var c = clazz(1L, "一班"); c.setStatus(1); when(eduClassMapper.selectById(1L)).thenReturn(c);
        assertEquals(CLASS_ARCHIVED.getCode(), assertThrows(ServiceException.class,
                () -> service.claimClassCourse(request(1L))).getCode());
        verify(eduClassCourseMapper, never()).insert(any(EduClassCourseDO.class));
    }
    @Test void releaseAllowsOwnerAdminApproverAndDeniesOthers() {
        var row = link(11L, 1L, 7L); row.setUpdater("system");
        when(eduClassCourseMapper.selectById(11L)).thenReturn(row);
        when(eduClassCourseMapper.selectClaimById(11L)).thenReturn(row);
        service.releaseClassCourse(11L); verify(eduClassCourseMapper).release(11L, 7L);
        row.setClaimTeacherId(8L);
        assertEquals(FORBIDDEN.getCode(), assertThrows(ServiceException.class, () -> service.releaseClassCourse(11L)).getCode());
        when(dataScopeService.isGlobalAdmin()).thenReturn(true);
        service.releaseClassCourse(11L);
        when(dataScopeService.isGlobalAdmin()).thenReturn(false); when(dataScopeService.isCourseApprover()).thenReturn(true);
        service.releaseClassCourse(11L); verify(eduClassCourseMapper, times(2)).release(11L, 8L);
        verify(eduClassCourseMapper, never()).updateById(any(EduClassCourseDO.class)); verifyNoInteractions(backfillService);
    }
    @Test void publicArchiveRestoreUseRowOwnerRatherThanCourseOwner() {
        var row = link(11L, 1L, 8L);
        when(eduClassCourseMapper.selectById(11L)).thenReturn(row);
        when(eduClassCourseMapper.selectClaimById(11L)).thenReturn(row);
        assertThrows(ServiceException.class, () -> service.unbindClassCourse(11L));
        assertThrows(ServiceException.class, () -> service.restoreClassCourse(11L));
        row.setClaimTeacherId(7L); service.unbindClassCourse(11L); service.restoreClassCourse(11L);
        verify(eduClassCourseMapper, times(2)).updateById(any(EduClassCourseDO.class));
        verify(dataScopeService, never()).validateCourseManage(any());
    }
    @Test void publicBindInBothDimensionsClaimsInsteadOfManageValidation() {
        var row = link(11L, 1L, 7L); when(eduClassCourseMapper.selectClaimRelation(2L, 1L, "S")).thenReturn(row);
        var request = new EduClassCourseBindReqVO(); request.setCourseId(2L); request.setClassIds(List.of(1L));
        service.bindClassCourse(request);
        request = new EduClassCourseBindReqVO(); request.setClassId(1L); request.setCourseIds(List.of(2L));
        service.bindClassCourse(request); verify(eduClassCourseMapper, times(3)).lockPublicCourse(2L);
        verify(dataScopeService, never()).validateCourseManage(any());
    }
    @Test void optionsAreBatchedSortedAndDistinguishMissingMineAndTaken() {
        var a = clazz(1L, "B"); a.setDeptId(10L); var b = clazz(3L, "A"); var c = clazz(4L, "C"); var d = clazz(5L, "A");
        when(eduClassMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(c, a, b, d));
        when(eduClassCourseMapper.selectList(any())).thenReturn(List.of(link(13L, 3L, 7L), link(14L, 4L, 8L)));
        var teacher = new AdminUserRespDTO(); teacher.setId(8L); teacher.setNickname("张");
        when(adminUserApi.getUserList(Set.of(7L, 8L))).thenReturn(List.of(teacher));
        var dept = new DeptRespDTO(); dept.setId(10L); dept.setName("学院"); when(deptApi.getDeptList(Set.of(10L))).thenReturn(List.of(dept));
        var rows = service.claimOptions(2L, null);
        assertEquals(List.of(5L, 1L, 3L, 4L), rows.stream().map(EduClassCourseClaimOptionRespVO::getClassId).toList());
        assertEquals(List.of("AVAILABLE", "AVAILABLE", "MINE", "TAKEN"), rows.stream().map(EduClassCourseClaimOptionRespVO::getState).toList());
        assertNull(rows.get(0).getClassCourseId()); assertEquals("学院", rows.get(1).getDeptName());
        assertEquals("张", rows.get(3).getClaimTeacherName());
        verify(eduClassMapper).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)); verify(eduClassCourseMapper).selectList(any());
        verify(adminUserApi).getUserList(any()); verify(adminUserApi, never()).getUser(any()); verify(deptApi).getDeptList(any());
    }
    @Test void archivedOwnClaimIsMineAndNotAvailable() {
        var archived = link(11L, 1L, 7L); archived.setStatus(1);
        when(eduClassMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(clazz(1L, "一班")));
        when(eduClassCourseMapper.selectList(any())).thenReturn(List.of(archived));
        var rows = service.claimOptions(2L, null);
        assertEquals(1, rows.size());
        assertEquals("MINE", rows.get(0).getState());
        assertEquals(11L, rows.get(0).getClassCourseId());
        assertEquals(7L, rows.get(0).getClaimTeacherId());
    }
    @Test void existingOwnClaimInBatchRemainsPartOfPreviousCoverage() {
        when(eduClassCourseMapper.selectClaimRelation(2L, 1L, "S")).thenReturn(link(11L, 1L, 7L));
        service.claimClassCourse(request(1L, 3L));
        verify(backfillService).backfillForClaim(2L, "S", 7L, 3L, Set.of(3L));
        verifyNoMoreInteractions(backfillService);
    }
    @Test void offlineOptionsRejectedBeforeBatchQueries() {
        when(capacityCourseApi.getCourse(2L)).thenReturn(new CapacityCourseRespDTO());
        assertEquals(CLASS_COURSE_CLAIM_OFFLINE.getCode(), assertThrows(ServiceException.class,
                () -> service.claimOptions(2L, null)).getCode());
        verifyNoInteractions(eduClassCourseMapper, eduClassMapper, deptApi, adminUserApi);
    }
    @Test void wholeSuccessfulBatchCallsBackfillForEachNewClaim() {
        service.claimClassCourse(request(1L, 3L));
        verify(backfillService).backfillForClaim(2L, "S", 7L, 1L, Set.of(1L, 3L));
        verify(backfillService).backfillForClaim(2L, "S", 7L, 3L, Set.of(1L, 3L));
        verify(eduClassCourseMapper, times(2)).insert(any(EduClassCourseDO.class));
    }
    @Test void publicApproverMayArchiveAndRestoreWithoutBeingClaimant() {
        var row = link(11L, 1L, 8L);
        when(eduClassCourseMapper.selectById(11L)).thenReturn(row);
        when(eduClassCourseMapper.selectClaimById(11L)).thenReturn(row);
        when(dataScopeService.isCourseApprover()).thenReturn(true);
        service.unbindClassCourse(11L); service.restoreClassCourse(11L);
        verify(eduClassCourseMapper, times(2)).updateById(any(EduClassCourseDO.class));
    }

    @Test void courseListFiltersClaimIncludingArchivedRowsAndKeepsOrdering() {
        when(dataScopeService.canViewAllPublic(2L)).thenReturn(false);
        when(eduClassCourseMapper.selectList(any())).thenReturn(List.of(link(11L, 1L, 7L)));
        service.listByCourse(2L, true);
        verify(eduClassCourseMapper).selectList(argThat((com.baomidou.mybatisplus.core.conditions.Wrapper<EduClassCourseDO> wrapper) ->
                wrapper.getSqlSegment().contains("claim_teacher_id =") && !wrapper.getSqlSegment().contains("status =")));
    }
    @Test void classListNarrowsPublicRowsButKeepsExclusiveAndAllViewRows() {
        var exclusive = link(14L, 1L, null); exclusive.setCourseId(4L);
        when(eduClassCourseMapper.selectList(any())).thenReturn(List.of(link(11L, 1L, 7L), link(12L, 1L, 8L), exclusive));
        assertEquals(List.of(11L, 14L), service.listByClass(1L, true).stream().map(EduClassCourseDO::getId).toList());
        when(dataScopeService.canViewAllPublic(2L)).thenReturn(true);
        assertEquals(3, service.listByClass(1L, true).size());
    }
}
