package cn.nus.zknu.module.edu.service.clazz;

import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.clazz.vo.EduClassSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.service.classcourse.EduPublicCourseSyncScheduler;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.system.api.dept.DeptApi;
import cn.nus.zknu.module.system.service.user.UserDeptScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EduClassDeptScopeTest extends BaseMockitoUnitTest {

    @Mock private EduClassMapper classMapper;
    @Mock private UserDeptScopeService userScope;
    @Mock private DeptApi deptApi;
    @Mock private EduPublicCourseSyncScheduler scheduler;
    private EduClassServiceImpl service;

    @BeforeEach
    void setUp() {
        EduDeptScopeService scope = new EduDeptScopeService();
        ReflectionTestUtils.setField(scope, "userDeptScopeService", userScope);
        ReflectionTestUtils.setField(scope, "classMapper", classMapper);
        service = new EduClassServiceImpl();
        ReflectionTestUtils.setField(service, "deptScopeService", scope);
        ReflectionTestUtils.setField(service, "eduClassMapper", classMapper);
        ReflectionTestUtils.setField(service, "deptApi", deptApi);
        ReflectionTestUtils.setField(service, "publicCourseSyncScheduler", scheduler);
    }

    @Test
    void modifyingAnotherCollegeClassReturnsForbiddenBeforeWrite() {
        when(userScope.isRestricted()).thenReturn(true);
        EduClassDO foreign = clazz(99L, 20L);
        when(classMapper.selectById(99L)).thenReturn(foreign);
        EduClassSaveReqVO req = new EduClassSaveReqVO();
        req.setId(99L);
        req.setDeptId(10L);

        assertForbidden(() -> service.updateClass(req));
        assertForbidden(() -> service.deleteClass(99L));
        assertForbidden(() -> service.restoreClass(99L));
        verify(classMapper, never()).updateById(any(EduClassDO.class));
    }

    @Test
    void classHiddenByDeptRuleReturnsForbidden() {
        when(userScope.isRestricted()).thenReturn(true);
        EduClassSaveReqVO req = new EduClassSaveReqVO();
        req.setId(99L);
        assertForbidden(() -> service.updateClass(req));
    }

    @Test
    void movingOwnClassToAnotherCollegeIsForbidden() {
        when(userScope.isRestricted()).thenReturn(true);
        when(userScope.isDeptInScope(10L)).thenReturn(true);
        when(classMapper.selectById(1L)).thenReturn(clazz(1L, 10L));
        doNothing().when(userScope).validateDeptInScope(10L);
        doThrow(new ServiceException(FORBIDDEN)).when(userScope).validateDeptInScope(20L);
        EduClassSaveReqVO req = new EduClassSaveReqVO();
        req.setId(1L);
        req.setDeptId(20L);
        assertForbidden(() -> service.updateClass(req));
        verify(classMapper, never()).updateById(any(EduClassDO.class));
    }

    @Test
    void zeroRowUpdateReturnsForbiddenAndDoesNotScheduleSync() {
        when(classMapper.selectById(1L)).thenReturn(clazz(1L, 10L));
        doCallRealMethod().when(userScope).validateAffectedRows(0);
        EduClassSaveReqVO req = new EduClassSaveReqVO();
        req.setId(1L);
        req.setDeptId(10L);
        assertForbidden(() -> service.updateClass(req));
        verifyNoInteractions(scheduler);
    }

    @Test
    void allScopeAdminKeepsCrossCollegeUpdate() {
        when(classMapper.selectById(99L)).thenReturn(clazz(99L, 20L));
        when(classMapper.updateById(any(EduClassDO.class))).thenReturn(1);
        EduClassSaveReqVO req = new EduClassSaveReqVO();
        req.setId(99L);
        req.setDeptId(30L);
        service.updateClass(req);
        verify(classMapper).updateById(any(EduClassDO.class));
        verify(scheduler).requestClass(99L);
    }

    private EduClassDO clazz(Long id, Long deptId) {
        EduClassDO clazz = new EduClassDO();
        clazz.setId(id);
        clazz.setDeptId(deptId);
        return clazz;
    }

    private void assertForbidden(org.junit.jupiter.api.function.Executable action) {
        assertEquals(FORBIDDEN.getCode(), assertThrows(ServiceException.class, action).getCode());
    }
}
