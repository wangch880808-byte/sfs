package cn.nus.zknu.module.edu.service.classcourse;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.capacity.api.course.PublicCourseChangedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class EduPublicCourseSyncListenerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduPublicCourseSyncListener listener;
    @Mock
    private EduPublicCourseSyncScheduler syncScheduler;

    @Test
    void syncFailureDoesNotAffectCourseOperation() throws Exception {
        doThrow(new IllegalStateException("数据库暂不可用")).when(syncScheduler).submitCourse(2L);

        listener.onCourseChanged(new PublicCourseChangedEvent(2L));

        verify(syncScheduler).submitCourse(2L);
        TransactionalEventListener annotation = EduPublicCourseSyncListener.class
                .getMethod("onCourseChanged", PublicCourseChangedEvent.class)
                .getAnnotation(TransactionalEventListener.class);
        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase());
        assertTrue(annotation.fallbackExecution());
    }
}
