package cn.nus.zknu.module.system.service.user.classstudent;

import cn.nus.zknu.framework.common.exception.ServiceException;
import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.api.user.StudentClassesChangedEvent;
import cn.nus.zknu.module.system.dal.dataobject.user.EduClassStudentRelDO;
import cn.nus.zknu.module.system.dal.mysql.user.EduClassStudentRelMapper;
import cn.nus.zknu.module.system.service.user.UserDeptScopeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Set;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserClassStudentServiceImplTest extends BaseMockitoUnitTest {

    @Mock private EduClassStudentRelMapper mapper;
    @Mock private UserDeptScopeService scope;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private UserClassStudentServiceImpl service;

    @Test
    void newStudentPublishesAddedClassesAfterRelationshipsAreInserted() {
        service.syncStudentClasses(1L, 2, Set.of(10L, 20L));

        var order = inOrder(mapper, publisher);
        ArgumentCaptor<List<EduClassStudentRelDO>> rows = ArgumentCaptor.forClass(List.class);
        order.verify(mapper).insertBatch(rows.capture());
        assertEquals(Set.of(10L, 20L), rows.getValue().stream().map(EduClassStudentRelDO::getClassId)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(rows.getValue().stream().allMatch(row -> row.getStudentId().equals(1L)
                && row.getStatus().equals(0) && row.getJoinTime() != null));
        order.verify(publisher).publishEvent(new StudentClassesChangedEvent(1L, Set.of(10L, 20L), Set.of()));
        verify(mapper, never()).deleteByStudentIdAndClassIds(anyLong(), anyCollection());
    }

    @Test
    void removedClassesArePublishedAfterDeletion() {
        when(mapper.selectListByStudentId(1L)).thenReturn(List.of(relation(10L), relation(20L)));

        service.syncStudentClasses(1L, 2, Set.of(20L));

        var order = inOrder(mapper, publisher);
        order.verify(mapper).deleteByStudentIdAndClassIds(eq(1L), argThat(ids -> Set.copyOf(ids).equals(Set.of(10L))));
        order.verify(publisher).publishEvent(new StudentClassesChangedEvent(1L, Set.of(), Set.of(10L)));
        verify(mapper, never()).insertBatch(any());
    }

    @Test
    void classTransferPublishesOnlyActualChangesOnce() {
        when(mapper.selectListByStudentId(1L)).thenReturn(List.of(relation(10L), relation(20L)));

        service.syncStudentClasses(1L, 2, Set.of(20L, 30L));

        var order = inOrder(mapper, publisher);
        order.verify(mapper).insertBatch(any());
        order.verify(mapper).deleteByStudentIdAndClassIds(eq(1L), anyCollection());
        order.verify(publisher).publishEvent(new StudentClassesChangedEvent(1L, Set.of(30L), Set.of(10L)));
        verifyNoMoreInteractions(publisher);
        verify(scope, never()).validateClassInScope(20L);
    }

    @Test
    void unchangedMembershipAndNullUserDoNotPublish() {
        when(mapper.selectListByStudentId(1L)).thenReturn(List.of(relation(10L)));

        service.syncStudentClasses(1L, 2, Set.of(10L));
        service.syncStudentClasses(null, 2, Set.of(20L));

        verifyNoInteractions(publisher);
        verify(mapper, never()).insertBatch(any());
        verify(mapper, never()).deleteByStudentIdAndClassIds(anyLong(), anyCollection());
    }

    @Test
    void nullTargetStillRemovesAllStudentClasses() {
        when(mapper.selectListByStudentId(1L)).thenReturn(List.of(relation(10L)));

        service.syncStudentClasses(1L, 2, null);

        verify(publisher).publishEvent(new StudentClassesChangedEvent(1L, Set.of(), Set.of(10L)));
    }

    @Test
    void changingToNonStudentPublishesAllRemovedClasses() {
        when(mapper.selectListByStudentId(1L)).thenReturn(List.of(relation(10L), relation(20L)));
        when(scope.isRestricted()).thenReturn(true);

        service.syncStudentClasses(1L, 1, Set.of(30L));

        verify(scope).validateClassInScope(10L);
        verify(scope).validateClassInScope(20L);
        verify(scope, never()).validateClassInScope(30L);
        var order = inOrder(mapper, publisher);
        order.verify(mapper).deleteByStudentId(1L);
        order.verify(publisher).publishEvent(new StudentClassesChangedEvent(1L, Set.of(), Set.of(10L, 20L)));
    }

    @Test
    void nonStudentWithoutMembershipDoesNotPublish() {
        service.syncStudentClasses(1L, 0, Set.of(10L));

        verify(mapper).deleteByStudentId(1L);
        verifyNoInteractions(publisher);
    }

    @Test
    void scopeFailureDoesNotWriteOrPublish() {
        doThrow(new ServiceException(FORBIDDEN)).when(scope).validateClassInScope(10L);

        assertThrows(ServiceException.class, () -> service.syncStudentClasses(1L, 2, Set.of(10L)));

        verify(mapper, never()).insertBatch(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void synchronousListenerFailurePropagatesToTheCallingTransaction() {
        doThrow(new IllegalStateException("模拟快照同步失败")).when(publisher).publishEvent(any(Object.class));

        assertThrows(IllegalStateException.class, () -> service.syncStudentClasses(1L, 2, Set.of(10L)));
    }

    private EduClassStudentRelDO relation(Long classId) {
        EduClassStudentRelDO row = new EduClassStudentRelDO();
        row.setStudentId(1L);
        row.setClassId(classId);
        return row;
    }
}
