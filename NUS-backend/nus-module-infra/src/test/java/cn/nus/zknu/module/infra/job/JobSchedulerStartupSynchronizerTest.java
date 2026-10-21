package cn.nus.zknu.module.infra.job;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.infra.service.job.JobService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.quartz.SchedulerException;

import static org.mockito.Mockito.verify;

class JobSchedulerStartupSynchronizerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private JobSchedulerStartupSynchronizer synchronizer;
    @Mock
    private JobService jobService;

    @Test
    void synchronizeLoadsDatabaseTasksIntoQuartz() throws SchedulerException {
        synchronizer.synchronize();

        verify(jobService).syncJob();
    }
}
