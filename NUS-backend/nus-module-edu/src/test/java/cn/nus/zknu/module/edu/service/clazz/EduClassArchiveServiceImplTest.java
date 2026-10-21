package cn.nus.zknu.module.edu.service.clazz;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.service.dataexchange.EduDeptScopeService;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.service.classstudent.EduClassStudentService;
import cn.nus.zknu.module.edu.service.classcourse.EduPublicCourseSyncService;
import cn.nus.zknu.module.edu.service.classcourse.EduPublicCourseSyncScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EduClassArchiveServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduClassServiceImpl service;
    @Mock
    private EduDeptScopeService deptScopeService;

    @Mock
    private EduClassMapper classMapper;
    @Mock
    private EduClassStudentService classStudentService;
    @Mock
    private EduPublicCourseSyncService publicCourseSyncService;

    @Test
    void archiveAndRestoreKeepStudentRelations() {
        EduClassDO clazz = new EduClassDO();
        clazz.setId(1L);
        when(classMapper.selectById(1L)).thenReturn(clazz);

        ReflectionTestUtils.setField(service, "publicCourseSyncScheduler", mock(EduPublicCourseSyncScheduler.class));
        service.deleteClass(1L);
        service.restoreClass(1L);

        verify(classMapper).updateById(argThat((EduClassDO item) -> item.getId().equals(1L) && item.getStatus().equals(1)));
        verify(classMapper).updateById(argThat((EduClassDO item) -> item.getId().equals(1L) && item.getStatus().equals(0)));
        verify(classStudentService, never()).deleteByClassIds(any());
        verify(classMapper, never()).deleteById(1L);
    }

    @Test
    void restoreSyncRunsAfterCommitAndFailureIsIgnored() {
        EduClassDO clazz = new EduClassDO();
        clazz.setId(1L);
        when(classMapper.selectById(1L)).thenReturn(clazz);
        doThrow(new IllegalStateException("同步失败")).when(publicCourseSyncService).syncClass(1L);
        EduPublicCourseSyncScheduler scheduler = new EduPublicCourseSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "syncService", publicCourseSyncService);
        ReflectionTestUtils.setField(scheduler, "executor", (java.util.concurrent.Executor) Runnable::run);
        ReflectionTestUtils.setField(service, "publicCourseSyncScheduler", scheduler);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.restoreClass(1L);
            verify(publicCourseSyncService, never()).syncClass(1L);
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(publicCourseSyncService).syncClass(1L);
            verify(classMapper).updateById(any(EduClassDO.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
