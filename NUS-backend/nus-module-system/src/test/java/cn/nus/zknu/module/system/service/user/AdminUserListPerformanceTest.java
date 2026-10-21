package cn.nus.zknu.module.system.service.user;

import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.controller.admin.user.UserController;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserPageReqVO;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserSimpleRespVO;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.mysql.user.AdminUserMapper;
import cn.nus.zknu.module.system.service.dept.DeptService;
import cn.nus.zknu.module.system.service.permission.PermissionService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminUserListPerformanceTest extends BaseMockitoUnitTest {
    @InjectMocks private AdminUserServiceImpl service;
    @Mock private AdminUserMapper userMapper;
    @Mock private DeptService deptService;
    @Mock private PermissionService permissionService;

    @Test
    void pageAndListUseCachedDescendantsWithoutMutatingCache() {
        Set<Long> cached = Set.of(11L,12L);
        when(deptService.getChildDeptIdListFromCache(10L)).thenReturn(cached);
        var req = new UserPageReqVO(); req.setDeptId(10L);
        when(userMapper.selectPage(req, Set.of(10L,11L,12L), null)).thenReturn(PageResult.empty());
        service.getUserPage(req);
        service.getUserList(req);
        verify(deptService, times(2)).getChildDeptIdListFromCache(10L);
        verify(deptService, never()).getChildDeptList(anyLong());
        verify(userMapper).selectPage(req, Set.of(10L,11L,12L), null);
        verify(userMapper).selectList(req, Set.of(10L,11L,12L), null);
        assertEquals(Set.of(11L,12L), cached);
    }

    @Test
    void noDepartmentDoesNotReadCacheAndEmptyRoleStillReturnsEmpty() {
        var req = new UserPageReqVO();
        service.getUserPage(req);
        verify(userMapper).selectPage(req, Set.<Long>of(), null);
        req.setRoleId(5L); req.setDeptId(10L);
        when(permissionService.getUserRoleIdListByRoleId(Set.of(5L))).thenReturn(Set.of());
        assertEquals(0L, service.getUserPage(req).getTotal());
        verifyNoInteractions(deptService);
    }

    @Test
    void simpleServicePassesOptionalTypeAndEnabledStatusToMapper() {
        var users = List.of(new AdminUserDO().setId(2L));
        when(userMapper.selectSimpleList(0,2)).thenReturn(users);
        assertSame(users, service.getSimpleUserList(2));
        service.getSimpleUserList(null);
        verify(userMapper).selectSimpleList(0,null);
        verify(userMapper, never()).selectListByStatus(any());
    }

    @Test
    void simpleControllerKeepsFieldsDepartmentsAndEncounterOrder() {
        var api = mock(AdminUserService.class);
        var controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", api);
        ReflectionTestUtils.setField(controller, "deptService", deptService);
        var users = List.of(new AdminUserDO().setId(8L).setNickname("李同学").setDeptId(10L).setUserType(2),
                new AdminUserDO().setId(2L).setNickname("王同学").setDeptId(20L).setUserType(2));
        when(api.getSimpleUserList(2)).thenReturn(users);
        var dept = new DeptDO(); dept.setId(10L); dept.setName("学院");
        when(deptService.getDeptMap(List.of(10L,20L))).thenReturn(Map.of(10L,dept));
        assertEquals(List.of(new UserSimpleRespVO(8L,"李同学",2,10L,"学院"),
                new UserSimpleRespVO(2L,"王同学",2,20L,null)), controller.getSimpleUserList(2).getData());
        verify(api).getSimpleUserList(2);
        verify(api, never()).getUserListByStatus(any());
    }
}
