package cn.nus.zknu.module.edu.service.classcourse;

import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.classcourse.EduClassCourseController;
import cn.nus.zknu.module.edu.controller.admin.clazz.EduClassController;
import cn.nus.zknu.module.edu.controller.admin.classcourse.vo.EduClassCourseBindReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterOptionRespVO;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.edu.service.semester.EduSemesterOptionService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EduClassCourseServiceImplTest extends BaseMockitoUnitTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                EduClassCourseDO.class);
    }

    @InjectMocks
    private EduClassCourseServiceImpl service;
    @Mock
    private EduClassCourseMapper eduClassCourseMapper;
    @Mock
    private EduDataScopeService dataScopeService;
    @Mock
    private CapacityCourseApi capacityCourseLiteMapper;
    @Mock
    private EduSemesterOptionService semesterOptionService;
    @Mock
    private EduClassMapper eduClassMapper;
    @Mock
    private EduClassCourseAssistantService assistantService;
    @Mock
    private EduClassStudentMapper eduClassStudentMapper;
    @Mock
    private EduBusinessNotifyService businessNotifyService;
    @Mock
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void allowExistingClassAndCourse() {
        EduClassDO clazz = new EduClassDO();
        clazz.setId(1L);
        clazz.setStatus(0);
        lenient().when(eduClassMapper.selectById(1L)).thenReturn(clazz);
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(2L);
        lenient().when(capacityCourseLiteMapper.getCourse(2L)).thenReturn(course);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void bindRollsBackLinkAndAssistantsOnAnyException() throws Exception {
        Transactional transactional = EduClassCourseServiceImpl.class
                .getMethod("bindClassCourse", EduClassCourseBindReqVO.class).getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertArrayEquals(new Class<?>[]{Exception.class}, transactional.rollbackFor());
        EduClassCourseBindReqVO request = newBindingRequest();
        doThrow(new IllegalStateException("assistant insert failed"))
                .when(assistantService).replaceAssistants(88L, java.util.List.of(12L));
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(status);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        interceptor.afterPropertiesSet();
        ProxyFactory proxy = new ProxyFactory(service);
        proxy.addAdvice(interceptor);
        EduClassCourseService transactionalService = (EduClassCourseService) proxy.getProxy();

        assertThrows(IllegalStateException.class, () -> transactionalService.bindClassCourse(request));

        verify(eduClassCourseMapper).insert(any(EduClassCourseDO.class));
        verify(transactionManager).rollback(status);
        verify(transactionManager, never()).commit(any());
        verifyNoInteractions(businessNotifyService);
    }

    @Test
    void bindNotifiesOnlyAfterCommitUsingANewTransaction() {
        EduClassCourseBindReqVO request = bindingWithStudents();
        TransactionStatus notifyStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(notifyStatus);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        service.bindClassCourse(request);

        verify(assistantService).replaceAssistants(88L, java.util.List.of(12L));
        verifyNoInteractions(businessNotifyService, transactionManager);
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        var order = inOrder(transactionManager, businessNotifyService);
        order.verify(transactionManager).getTransaction(definition.capture());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, definition.getValue().getPropagationBehavior());
        order.verify(businessNotifyService).sendToUsers(eq(java.util.List.of(10L)),
                eq(EduBusinessNotifyService.COURSE_ASSIGNED),
                eq(java.util.Map.of("className", "班级一", "courseName", "课程一")),
                eq("/student/course/detail/2"));
        order.verify(transactionManager).commit(notifyStatus);
    }

    @Test
    void bindRolledBackTransactionSendsNoNotification() {
        EduClassCourseBindReqVO request = bindingWithStudents();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        service.bindClassCourse(request);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync ->
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(businessNotifyService, transactionManager);
    }

    private EduClassCourseBindReqVO newBindingRequest() {
        EduSemesterOptionRespVO option = semesterOption("2026-2027 上学期");
        when(semesterOptionService.resolve(null)).thenReturn(option);
        when(eduClassCourseMapper.selectList(any())).thenReturn(java.util.List.of());
        doAnswer(invocation -> {
            invocation.<EduClassCourseDO>getArgument(0).setId(88L);
            return 1;
        }).when(eduClassCourseMapper).insert(any(EduClassCourseDO.class));
        EduClassCourseBindReqVO request = new EduClassCourseBindReqVO();
        request.setCourseId(2L);
        request.setClassIds(java.util.List.of(1L));
        request.setAssistantTeacherIds("12");
        return request;
    }

    private EduClassCourseBindReqVO bindingWithStudents() {
        EduClassCourseBindReqVO request = newBindingRequest();
        EduClassDO clazz = new EduClassDO();
        clazz.setClassName("班级一");
        when(eduClassMapper.selectById(1L)).thenReturn(clazz);
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setCourseName("课程一");
        when(capacityCourseLiteMapper.getCourse(2L)).thenReturn(course);
        EduClassStudentDO student = new EduClassStudentDO();
        student.setStudentId(10L);
        when(eduClassStudentMapper.selectListByClassId(1L)).thenReturn(java.util.List.of(student));
        return request;
    }

    @Test
    void listByCourse_validatesCourseDataScopeBeforeQuery() {
        doThrow(exception(FORBIDDEN)).when(dataScopeService).validateCourseAccess(5L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.listByCourse(5L));

        assertEquals(FORBIDDEN.getCode(), ex.getCode());
        verify(eduClassCourseMapper, never()).selectList(any());
    }

    @Test
    void listByCourse_accessibleCourseReturnsRelations() {
        when(eduClassCourseMapper.selectList(any())).thenReturn(java.util.List.of());

        service.listByCourse(5L);

        verify(dataScopeService).validateCourseAccess(5L);
    }

    @Test
    void bindAgainRestoresArchivedRelation() {
        semesterOption("2026-2027-1");
        EduClassCourseDO relation = new EduClassCourseDO();
        relation.setId(7L);
        relation.setStatus(1);
        when(eduClassCourseMapper.selectList(any())).thenReturn(java.util.List.of(relation));
        EduClassCourseBindReqVO request = new EduClassCourseBindReqVO();
        request.setClassId(1L);
        request.setCourseIds(java.util.List.of(2L));
        request.setSemesterName("2026-2027-1");

        service.bindClassCourse(request);

        verify(eduClassCourseMapper).updateById(argThat((EduClassCourseDO item) -> item.getId().equals(7L)
                && item.getStatus().equals(0)));
        verify(eduClassCourseMapper, never()).insert(any(EduClassCourseDO.class));
    }

    @Test
    void bindUsesConfiguredSemesterDatesInsteadOfRequestDates() {
        EduSemesterOptionRespVO option = semesterOption("2026-2027 上学期");
        option.setSemesterStartDate(java.time.LocalDate.of(2026, 8, 1));
        option.setSemesterEndDate(java.time.LocalDate.of(2027, 1, 31));
        when(semesterOptionService.resolve(null)).thenReturn(option);
        when(eduClassCourseMapper.selectList(any())).thenReturn(java.util.List.of());
        EduClassCourseBindReqVO request = new EduClassCourseBindReqVO();
        request.setCourseId(2L);
        request.setClassIds(java.util.List.of(1L));
        request.setSemesterStartDate(java.time.LocalDate.of(2000, 1, 1));

        service.bindClassCourse(request);

        verify(eduClassCourseMapper).insert(argThat((EduClassCourseDO item) ->
                item.getSemesterName().equals("2026-2027 上学期")
                        && item.getSemesterStartDate().equals(java.time.LocalDate.of(2026, 8, 1))
                        && item.getSemesterEndDate().equals(java.time.LocalDate.of(2027, 1, 31))));
    }

    @Test
    void bindWritesAssistantsToTableWithoutWritingLegacyCsv() {
        EduSemesterOptionRespVO option = semesterOption("2026-2027 上学期");
        when(semesterOptionService.resolve(null)).thenReturn(option);
        when(eduClassCourseMapper.selectList(any())).thenReturn(java.util.List.of());
        doAnswer(invocation -> {
            invocation.<EduClassCourseDO>getArgument(0).setId(88L);
            return 1;
        }).when(eduClassCourseMapper).insert(any(EduClassCourseDO.class));
        EduClassCourseBindReqVO request = new EduClassCourseBindReqVO();
        request.setCourseId(2L);
        request.setClassIds(java.util.List.of(1L));
        // 历史前端可能带空格、全角逗号、重复和非数字片段
        request.setAssistantTeacherIds(" 12 ,x，13,12,,0");

        service.bindClassCourse(request);

        verify(eduClassCourseMapper).insert(argThat((EduClassCourseDO item) -> item.getAssistantTeacherIds() == null));
        verify(assistantService).replaceAssistants(88L, java.util.List.of(12L, 13L));
    }

    @Test
    void bindAgainDoesNotTouchExistingAssistants() {
        EduClassCourseDO existing = new EduClassCourseDO();
        existing.setId(7L);
        existing.setStatus(0);
        EduSemesterOptionRespVO option = semesterOption("2026-2027 上学期");
        when(semesterOptionService.resolve(null)).thenReturn(option);
        when(eduClassCourseMapper.selectList(any())).thenReturn(java.util.List.of(existing));
        EduClassCourseBindReqVO request = new EduClassCourseBindReqVO();
        request.setCourseId(2L);
        request.setClassIds(java.util.List.of(1L));
        request.setAssistantTeacherIds("12");

        service.bindClassCourse(request);

        verify(eduClassCourseMapper, never()).insert(any(EduClassCourseDO.class));
        verifyNoInteractions(assistantService);
    }

    @Test
    void bindRejectsSemesterOutsideConfiguredList() {
        when(semesterOptionService.resolve("随便写的学期"))
                .thenThrow(exception(SEMESTER_NOT_CONFIGURED, "随便写的学期"));
        EduClassCourseBindReqVO request = new EduClassCourseBindReqVO();
        request.setCourseId(2L);
        request.setClassIds(java.util.List.of(1L));
        request.setSemesterName("随便写的学期");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.bindClassCourse(request));

        assertEquals(SEMESTER_NOT_CONFIGURED.getCode(), ex.getCode());
        verify(eduClassCourseMapper, never()).insert(any(EduClassCourseDO.class));
    }

    @Test
    void bindRejectsMissingClassBeforeInsert() {
        EduSemesterOptionRespVO option = semesterOption("2026-2027 上学期");
        when(semesterOptionService.resolve(null)).thenReturn(option);
        when(eduClassMapper.selectById(1L)).thenReturn(null);

        assertBindingRejected(CLASS_NOT_EXISTS);
    }

    @Test
    void bindRejectsArchivedClassBeforeInsertOrRestore() {
        EduSemesterOptionRespVO option = semesterOption("2026-2027 上学期");
        when(semesterOptionService.resolve(null)).thenReturn(option);
        EduClassDO clazz = new EduClassDO();
        clazz.setStatus(1);
        when(eduClassMapper.selectById(1L)).thenReturn(clazz);

        assertBindingRejected(CLASS_ARCHIVED);
    }

    @Test
    void bindRejectsMissingCourseBeforeInsert() {
        EduSemesterOptionRespVO option = semesterOption("2026-2027 上学期");
        when(semesterOptionService.resolve(null)).thenReturn(option);
        when(capacityCourseLiteMapper.getCourse(2L)).thenReturn(null);

        assertBindingRejected(COURSE_NOT_EXISTS);
    }

    private void assertBindingRejected(cn.nus.zknu.framework.common.exception.ErrorCode code) {
        EduClassCourseBindReqVO request = new EduClassCourseBindReqVO();
        request.setCourseId(2L);
        request.setClassIds(java.util.List.of(1L));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.bindClassCourse(request));
        assertEquals(code.getCode(), ex.getCode());
        verify(eduClassCourseMapper, never()).insert(any(EduClassCourseDO.class));
        verify(eduClassCourseMapper, never()).updateById(any(EduClassCourseDO.class));
        verify(eduClassCourseMapper, never()).selectList(any());
        verifyNoInteractions(assistantService, businessNotifyService);
    }

    @Test
    void legacyBindingChecksAllCoursesBeforeAnyWrite() {
        doNothing().when(dataScopeService).validateCourseManage(2L);
        doThrow(exception(FORBIDDEN)).when(dataScopeService).validateCourseManage(3L);
        EduClassCourseBindReqVO request = new EduClassCourseBindReqVO();
        request.setClassId(1L);
        request.setCourseIds(java.util.List.of(2L, 3L));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.bindClassCourse(request));

        assertEquals(FORBIDDEN.getCode(), ex.getCode());
        verify(dataScopeService).validateCourseManage(2L);
        verify(dataScopeService).validateCourseManage(3L);
        verifyNoInteractions(eduClassCourseMapper, semesterOptionService);
    }

    @Test
    void listByClassDeniesAccessBeforeReadingEvenWhenIncludingArchived() {
        doThrow(exception(FORBIDDEN)).when(dataScopeService).validateClassAccess(5L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.listByClass(5L, true));

        assertEquals(FORBIDDEN.getCode(), ex.getCode());
        verify(eduClassMapper, never()).selectById(5L);
        verifyNoInteractions(eduClassCourseMapper);
    }

    @Test
    void listByClassValidatesTheDefaultOverload() {
        when(eduClassCourseMapper.selectList(any())).thenReturn(java.util.List.of());

        service.listByClass(1L);

        verify(dataScopeService).validateClassAccess(1L);
    }

    @Test
    void unbindAndRestoreRejectMissingRelation() {
        assertEquals(CLASS_COURSE_NOT_EXISTS.getCode(), assertThrows(ServiceException.class,
                () -> service.unbindClassCourse(7L)).getCode());
        assertEquals(CLASS_COURSE_NOT_EXISTS.getCode(), assertThrows(ServiceException.class,
                () -> service.restoreClassCourse(7L)).getCode());
        verifyNoInteractions(dataScopeService);
        verify(eduClassCourseMapper, never()).updateById(any(EduClassCourseDO.class));
    }

    private EduSemesterOptionRespVO semesterOption(String name) {
        EduSemesterOptionRespVO option = new EduSemesterOptionRespVO();
        option.setSemesterName(name);
        lenient().when(semesterOptionService.resolve(name)).thenReturn(option);
        return option;
    }

    @Test
    void taskPageReadonlyEndpointsAcceptHomeworkQueryPermission() throws Exception {
        assertEquals("@ss.hasAnyPermissions('system:user:query', 'edu:homework:query')",
                EduClassController.class.getMethod("getSimpleClassList", Long.class, Integer.class)
                        .getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasAnyPermissions('edu:class-course:query', 'edu:homework:query')",
                EduClassCourseController.class.getMethod("listByCourse", Long.class, boolean.class)
                        .getAnnotation(PreAuthorize.class).value());
    }

}
