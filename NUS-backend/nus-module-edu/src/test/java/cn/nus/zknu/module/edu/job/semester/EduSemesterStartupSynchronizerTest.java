package cn.nus.zknu.module.edu.job.semester;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.service.semester.EduSemesterService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EduSemesterStartupSynchronizerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduSemesterStartupSynchronizer synchronizer;
    @Mock
    private EduSemesterService eduSemesterService;

    @Test
    void synchronizeUpdatesSemester() {
        synchronizer.synchronize();

        verify(eduSemesterService).autoUpdateCurrentSemester();
    }

    @Test
    void synchronizeDoesNotBlockStartupOnFailure() {
        when(eduSemesterService.autoUpdateCurrentSemester()).thenThrow(new IllegalStateException("database unavailable"));

        assertDoesNotThrow(() -> synchronizer.synchronize());
    }
}
