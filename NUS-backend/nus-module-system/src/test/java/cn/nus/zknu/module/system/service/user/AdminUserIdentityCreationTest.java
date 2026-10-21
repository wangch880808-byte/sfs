package cn.nus.zknu.module.system.service.user;

import cn.nus.zknu.framework.common.enums.CommonStatusEnum;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserProfileExtReqVO;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import cn.nus.zknu.module.system.dal.dataobject.permission.RoleDO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import cn.nus.zknu.module.system.service.dept.DeptService;
import cn.nus.zknu.module.system.service.dept.PostService;
import cn.nus.zknu.module.system.service.permission.PermissionService;
import cn.nus.zknu.module.system.service.permission.RoleService;
import cn.nus.zknu.module.system.service.user.classstudent.UserClassStudentService;
import cn.nus.zknu.module.system.service.user.profile.UserProfileExtService;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.nus.zknu.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.nus.zknu.module.system.enums.ErrorCodeConstants.STUDENT_DEPT_REQUIRED;
import static cn.nus.zknu.module.system.enums.ErrorCodeConstants.STUDENT_GRADE_YEAR_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminUserIdentityCreationTest extends BaseMockitoUnitTest {

    @Mock
    private UserDeptScopeService deptScopeService;

    @Mock
    private RoleService roleService;
    @Mock private AdminUserMapper userMapper;
    @Mock private DeptService deptService;
    @Mock private PostService postService;
    @Mock private PermissionService permissionService;
    @Mock private UserProfileExtService userProfileExtService;
    @Mock private UserClassStudentService userClassStudentService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EduRoleApi eduRoleApi;

    private AdminUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new AdminUserServiceImpl());
        ReflectionTestUtils.setField(service, "deptScopeService", deptScopeService);
        ReflectionTestUtils.setField(service, "roleService", roleService);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "deptService", deptService);
        ReflectionTestUtils.setField(service, "postService", postService);
        ReflectionTestUtils.setField(service, "permissionService", permissionService);
        ReflectionTestUtils.setField(service, "userProfileExtService", userProfileExtService);
        ReflectionTestUtils.setField(service, "userClassStudentService", userClassStudentService);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "eduRoleApi", eduRoleApi);
    }

    @Test
    void createTeacherUser_forcesTypeAndMergesIdentityRole() {
        when(eduRoleApi.getTeacherRoleCode()).thenReturn("teacher");
        when(roleService.getRoleList()).thenReturn(List.of(role(20L, "teacher")));
        doReturn(101L).when(service).createUser(any());
        UserSaveReqVO request = new UserSaveReqVO();
        request.setUserType(2);
        request.setRoleIds(Set.of(30L));

        assertEquals(101L, service.createTeacherUser(request));
        assertEquals(1, request.getUserType());
        assertEquals(Set.of(20L, 30L), request.getRoleIds());
        verify(service).createUser(request);
    }

    @Test
    void createStudentUser_forcesTypeAndMergesIdentityRole() {
        when(eduRoleApi.getStudentRoleCode()).thenReturn("student");
        when(roleService.getRoleList()).thenReturn(List.of(role(21L, "student")));
        doReturn(102L).when(service).createUser(any());
        UserSaveReqVO request = new UserSaveReqVO();
        request.setUserType(1);
        request.setRoleIds(Set.of(31L));

        assertEquals(102L, service.createStudentUser(request));
        assertEquals(2, request.getUserType());
        assertEquals(Set.of(21L, 31L), request.getRoleIds());
        verify(service).createUser(request);
    }

    @Test
    void createStudentUser_usesConfiguredStudentRoleCode() {
        when(eduRoleApi.getStudentRoleCode()).thenReturn("xs");
        when(roleService.getRoleList()).thenReturn(List.of(role(21L, "student"), role(22L, "xs")));
        doReturn(103L).when(service).createUser(any());
        UserSaveReqVO request = new UserSaveReqVO();

        service.createStudentUser(request);

        assertEquals(Set.of(22L), request.getRoleIds());
    }

    @Test
    void createUser_studentRequiresOrganizationAndGradeYear() {
        AdminUserServiceImpl realService = new AdminUserServiceImpl();
        ReflectionTestUtils.setField(realService, "deptScopeService", deptScopeService);
        UserSaveReqVO request = new UserSaveReqVO();
        request.setUserType(2);

        assertServiceException(() -> realService.createUser(request), STUDENT_DEPT_REQUIRED);

        request.setDeptId(9L);
        assertServiceException(() -> realService.createUser(request), STUDENT_GRADE_YEAR_REQUIRED);
    }

    @Test
    void createUser_studentStartsWithPasswordChangeRequired() {
        UserSaveReqVO request = new UserSaveReqVO();
        request.setUsername("20260001");
        request.setPassword("startPass1");
        request.setUserType(2);
        request.setDeptId(9L);
        UserProfileExtReqVO profile = new UserProfileExtReqVO();
        profile.setGradeYear(2026);
        request.setProfile(profile);
        doAnswer(invocation -> {
            invocation.<AdminUserDO>getArgument(0).setId(8L);
            return 1;
        }).when(userMapper).insert(any(AdminUserDO.class));

        service.createUser(request);

        ArgumentCaptor<AdminUserDO> inserted = ArgumentCaptor.forClass(AdminUserDO.class);
        verify(userMapper).insert(inserted.capture());
        assertTrue(inserted.getValue().getPasswordChangeRequired());
    }

    @Test
    void createUser_usesPreEncodedPasswordOnlyInsideScopeAndForSamePassword() {
        when(passwordEncoder.encode(any())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0)
                + ":" + Thread.currentThread().getName());
        ArgumentCaptor<AdminUserDO> inserted = ArgumentCaptor.forClass(AdminUserDO.class);

        try (var ignored = service.preEncodePasswords(Map.of("20260001", "startPass1", "20260002", "startPass2"))) {
            service.createUser(student("20260001", "startPass1"));
            service.createUser(student("20260002", "changedPass"));
        }
        service.createUser(student("20260001", "startPass1"));

        verify(userMapper, times(3)).insert(inserted.capture());
        // 作用域内、明文一致：用预先并行算好的哈希（在加密线程里算的）
        assertEquals("hash:startPass1:password-encode", inserted.getAllValues().get(0).getPassword());
        // 明文与预先计算的不一致、或作用域已关闭：在当前线程现算
        String current = Thread.currentThread().getName();
        assertEquals("hash:changedPass:" + current, inserted.getAllValues().get(1).getPassword());
        assertEquals("hash:startPass1:" + current, inserted.getAllValues().get(2).getPassword());
        verify(passwordEncoder, times(4)).encode(any());
    }

    private UserSaveReqVO student(String username, String password) {
        UserSaveReqVO request = new UserSaveReqVO();
        request.setUsername(username);
        request.setPassword(password);
        request.setUserType(2);
        request.setDeptId(9L);
        UserProfileExtReqVO profile = new UserProfileExtReqVO();
        profile.setGradeYear(2026);
        request.setProfile(profile);
        return request;
    }

    private RoleDO role(Long id, String code) {
        RoleDO role = new RoleDO();
        role.setId(id);
        role.setCode(code);
        role.setStatus(CommonStatusEnum.ENABLE.getStatus());
        return role;
    }

}
