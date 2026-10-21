package cn.nus.zknu.module.system.service.user;

import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.dal.dataobject.user.EduClassStudentRelDO;
import cn.nus.zknu.module.system.dal.mysql.user.EduClassStudentRelMapper;
import cn.nus.zknu.module.system.service.user.classstudent.UserClassStudentServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Set;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserClassStudentDeptScopeTest extends BaseMockitoUnitTest {

    @Mock private EduClassStudentRelMapper mapper;
    @Mock private UserDeptScopeService scope;
    @InjectMocks private UserClassStudentServiceImpl service;

    @Test
    void cannotAttachStudentToForeignClassThroughUserForm() {
        when(mapper.selectListByStudentId(1L)).thenReturn(List.of());
        doThrow(new ServiceException(FORBIDDEN)).when(scope).validateClassInScope(99L);
        assertEquals(FORBIDDEN.getCode(), assertThrows(ServiceException.class,
                () -> service.syncStudentClasses(1L, 2, Set.of(99L))).getCode());
        verify(mapper, never()).insertBatch(any());
    }

    @Test
    void cannotRemoveStudentFromForeignClassThroughUserForm() {
        when(mapper.selectListByStudentId(1L)).thenReturn(List.of(relation(99L)));
        doThrow(new ServiceException(FORBIDDEN)).when(scope).validateClassInScope(99L);
        assertThrows(ServiceException.class, () -> service.syncStudentClasses(1L, 2, Set.of()));
        verify(mapper, never()).deleteByStudentIdAndClassIds(anyLong(), anyCollection());
    }

    @Test
    void preservingExistingCrossCollegeRelationDoesNotBlockAccountEdit() {
        when(mapper.selectListByStudentId(1L)).thenReturn(List.of(relation(99L)));
        assertDoesNotThrow(() -> service.syncStudentClasses(1L, 2, Set.of(99L)));
        verify(scope, never()).validateClassInScope(anyLong());
        verify(mapper, never()).insertBatch(any());
        verify(mapper, never()).deleteByStudentIdAndClassIds(anyLong(), anyCollection());
    }

    private EduClassStudentRelDO relation(Long classId) {
        EduClassStudentRelDO relation = new EduClassStudentRelDO();
        relation.setClassId(classId);
        relation.setStudentId(1L);
        return relation;
    }
}
