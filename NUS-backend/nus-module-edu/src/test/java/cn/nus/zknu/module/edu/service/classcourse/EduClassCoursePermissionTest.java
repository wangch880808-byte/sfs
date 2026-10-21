package cn.nus.zknu.module.edu.service.classcourse;

import cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.CapacityCourseApi;
import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.edu.controller.admin.classcourse.vo.EduClassCourseBindReqVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterOptionRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.classcourse.EduClassCourseDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.mysql.classcourse.EduClassCourseMapper;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataScopeService;
import cn.nus.zknu.module.edu.service.notification.EduBusinessNotifyService;
import cn.nus.zknu.module.edu.service.semester.EduSemesterOptionService;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 使用共享的数据范围服务验证班课写操作的完整角色矩阵。 */
class EduClassCoursePermissionTest extends BaseMockitoUnitTest {

    enum Role {
        OWNER, MEMBER, UNRELATED_TEACHER, PUBLIC_UNRELATED_TEACHER, ADMIN
    }

    @InjectMocks
    private EduClassCourseServiceImpl service;
    @Mock
    private EduClassCourseMapper classCourseMapper;
    @Mock
    private EduClassMapper classMapper;
    @Mock
    private EduClassStudentMapper classStudentMapper;
    @Mock
    private CapacityCourseApi courseApi;
    @Mock
    private EduRoleApi roleApi;
    @Mock
    private EduSemesterOptionService semesterOptionService;
    @Mock
    private EduClassCourseAssistantService assistantService;
    @Mock
    private EduBusinessNotifyService businessNotifyService;
    @Mock
    private EduPublicCourseBackfillService backfillService;

    private final EduDataScopeService dataScopeService = new EduDataScopeService();

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                EduClassCourseDO.class);
    }

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(dataScopeService, "eduRoleApi", roleApi);
        ReflectionTestUtils.setField(dataScopeService, "capacityCourseApi", courseApi);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        // 绑定通知只注册提交回调，本测试不提交事务。
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void courseDimensionBindingChecksManagePermission(Role role) {
        checkBinding(role, true);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void legacyClassDimensionBindingChecksManagePermission(Role role) {
        checkBinding(role, false);
    }

    private void checkBinding(Role role, boolean courseDimension) {
        configureRole(role);
        EduClassDO clazz = new EduClassDO();
        clazz.setId(1L);
        clazz.setStatus(0);
        lenient().when(classMapper.selectById(1L)).thenReturn(clazz);
        EduSemesterOptionRespVO semester = new EduSemesterOptionRespVO();
        semester.setSemesterName("2026-2027 上学期");
        lenient().when(semesterOptionService.resolve(null)).thenReturn(semester);
        EduClassCourseBindReqVO request = new EduClassCourseBindReqVO();
        if (courseDimension) {
            request.setCourseId(2L);
            request.setClassIds(List.of(1L));
        } else {
            request.setClassId(1L);
            request.setCourseIds(List.of(2L));
        }

        runAsTeacher(() -> {
            if (canManage(role) || role == Role.PUBLIC_UNRELATED_TEACHER) {
                service.bindClassCourse(request);
                verify(classCourseMapper).insert(any(EduClassCourseDO.class));
            } else {
                assertServiceException(() -> service.bindClassCourse(request), FORBIDDEN);
                verify(classCourseMapper, never()).insert(any(EduClassCourseDO.class));
                verifyNoInteractions(assistantService, semesterOptionService);
            }
        });
        verifyNoInteractions(businessNotifyService);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void unbindChecksRelationCourseManagePermission(Role role) {
        checkRelationWrite(role, false);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void restoreChecksRelationCourseManagePermission(Role role) {
        checkRelationWrite(role, true);
    }

    private void checkRelationWrite(Role role, boolean restore) {
        configureRole(role);
        EduClassCourseDO relation = new EduClassCourseDO();
        relation.setId(5L);
        relation.setClassId(1L);
        relation.setCourseId(2L);
        relation.setStatus(restore ? 1 : 0);
        when(classCourseMapper.selectById(5L)).thenReturn(relation);
        if (role == Role.PUBLIC_UNRELATED_TEACHER) {
            when(classCourseMapper.selectClaimById(5L)).thenReturn(relation);
        }
        Runnable action = () -> {
            if (restore) {
                service.restoreClassCourse(5L);
            } else {
                service.unbindClassCourse(5L);
            }
        };

        runAsTeacher(() -> {
            if (canManage(role)) {
                action.run();
                verify(classCourseMapper).updateById(argThat((EduClassCourseDO item) ->
                        item.getId().equals(5L) && item.getStatus().equals(restore ? 0 : 1)));
            } else {
                assertServiceException(action::run, FORBIDDEN);
                verify(classCourseMapper, never()).updateById(any(EduClassCourseDO.class));
            }
        });
    }

    private void configureRole(Role role) {
        CapacityCourseRespDTO course = new CapacityCourseRespDTO();
        course.setId(2L);
        course.setTeacherId(role == Role.OWNER ? 7L : 99L);
        course.setIsPublicCourse(role == Role.PUBLIC_UNRELATED_TEACHER ? 0 : 1);
        course.setPublicAuditStatus(2);
        course.setStatus(0);
        lenient().when(courseApi.getCourse(2L)).thenReturn(course);
        lenient().when(courseApi.isCourseMember(2L, 7L)).thenReturn(role == Role.MEMBER);
        when(roleApi.isCurrentUserAdmin()).thenReturn(role == Role.ADMIN);
        lenient().when(roleApi.isCurrentUserTeacher()).thenReturn(true);
        if (role == Role.PUBLIC_UNRELATED_TEACHER) {
            // 任意教师可认领，未认领的行仍不能归档或恢复。
            runAsTeacher(() -> dataScopeService.validateCourseAccess(2L));
        }
    }

    private static boolean canManage(Role role) {
        return role == Role.OWNER || role == Role.MEMBER || role == Role.ADMIN;
    }

    private static void runAsTeacher(Runnable action) {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(7L);
            action.run();
        }
    }

}
