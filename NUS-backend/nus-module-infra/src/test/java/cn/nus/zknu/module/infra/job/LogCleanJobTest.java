package cn.nus.zknu.module.infra.job;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.infra.job.job.JobLogCleanJob;
import cn.nus.zknu.module.infra.job.logger.AccessLogCleanJob;
import cn.nus.zknu.module.infra.job.logger.ErrorLogCleanJob;
import cn.nus.zknu.module.infra.service.job.JobLogService;
import cn.nus.zknu.module.infra.service.logger.ApiAccessLogService;
import cn.nus.zknu.module.infra.service.logger.ApiErrorLogService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class LogCleanJobTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AccessLogCleanJob accessLogCleanJob;
    @InjectMocks
    private ErrorLogCleanJob errorLogCleanJob;
    @InjectMocks
    private JobLogCleanJob jobLogCleanJob;
    @Mock
    private ApiAccessLogService apiAccessLogService;
    @Mock
    private ApiErrorLogService apiErrorLogService;
    @Mock
    private JobLogService jobLogService;

    @Test
    void parseRetainDays_usesDefaultForBlankOrInvalidParam() {
        assertEquals(180, LogCleanJobUtils.parseRetainDays(null, 180));
        assertEquals(180, LogCleanJobUtils.parseRetainDays("  ", 180));
        assertEquals(180, LogCleanJobUtils.parseRetainDays("abc", 180));
        assertEquals(180, LogCleanJobUtils.parseRetainDays("0", 180));
        assertEquals(180, LogCleanJobUtils.parseRetainDays("-7", 180));
        assertEquals(90, LogCleanJobUtils.parseRetainDays(" 90 ", 180));
    }

    @Test
    void jobsUseRetainDaysFromHandlerParam() {
        accessLogCleanJob.execute("200");
        errorLogCleanJob.execute("190");
        jobLogCleanJob.execute("45");

        verify(apiAccessLogService).cleanAccessLog(eq(200), anyInt());
        verify(apiErrorLogService).cleanErrorLog(eq(190), anyInt());
        verify(jobLogService).cleanJobLog(eq(45), anyInt());
    }

    @Test
    void jobsFallBackToDefaultRetainDays() {
        accessLogCleanJob.execute("");
        errorLogCleanJob.execute("invalid");
        jobLogCleanJob.execute(null);

        verify(apiAccessLogService).cleanAccessLog(eq(180), anyInt());
        verify(apiErrorLogService).cleanErrorLog(eq(180), anyInt());
        verify(jobLogService).cleanJobLog(eq(30), anyInt());
    }

}
