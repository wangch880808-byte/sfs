package cn.nus.zknu.module.edu.service.classstudent;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.system.api.user.StudentClassesChangedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EduStudentClassesChangedListenerTest extends BaseMockitoUnitTest {

    @Mock private EduClassStudentService service;
    @InjectMocks private EduStudentClassesChangedListener listener;

    @Test
    void transfersRemoveOldAssignmentsAndAddNewAssignmentsForEachClass() {
        listener.onStudentClassesChanged(new StudentClassesChangedEvent(1L, Set.of(10L, 20L), Set.of(30L)));

        var order = inOrder(service);
        order.verify(service).syncAssigneesForMembershipChange(30L, List.of(1L), false);
        verify(service).syncAssigneesForMembershipChange(10L, List.of(1L), true);
        verify(service).syncAssigneesForMembershipChange(20L, List.of(1L), true);
        verifyNoMoreInteractions(service);
    }

    @Test
    void emptyChangeDoesNotCallService() {
        listener.onStudentClassesChanged(new StudentClassesChangedEvent(1L, Set.of(), Set.of()));

        verifyNoInteractions(service);
    }

    @Test
    void failureIsPropagatedAndListenerRunsSynchronously() throws Exception {
        doThrow(new IllegalStateException("模拟快照同步失败")).when(service)
                .syncAssigneesForMembershipChange(10L, List.of(1L), true);

        assertThrows(IllegalStateException.class, () -> listener.onStudentClassesChanged(
                new StudentClassesChangedEvent(1L, Set.of(10L), Set.of())));

        var method = EduStudentClassesChangedListener.class.getMethod("onStudentClassesChanged",
                StudentClassesChangedEvent.class);
        assertNotNull(method.getAnnotation(EventListener.class));
        assertNull(method.getAnnotation(TransactionalEventListener.class));
        assertNull(method.getAnnotation(Async.class));
    }
}
