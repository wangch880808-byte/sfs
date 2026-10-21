package cn.nus.zknu.module.edu.service.classcourse;

import cn.nus.zknu.module.infra.api.config.ConfigApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class EduPublicCourseSyncExecutorConfigurationTest {

    @Test
    void capacitiesComeFromInfraConfig() {
        ConfigApi configApi = mock(ConfigApi.class);
        when(configApi.getConfigValueByKey(EduPublicCourseSyncExecutorConfiguration.KEY_POOL_SIZE)).thenReturn("3");
        when(configApi.getConfigValueByKey(EduPublicCourseSyncExecutorConfiguration.KEY_QUEUE_CAPACITY)).thenReturn("12");

        var executor = new EduPublicCourseSyncExecutorConfiguration().eduPublicCourseSyncExecutor(configApi);

        assertEquals(3, executor.getCorePoolSize());
        assertEquals(3, executor.getMaxPoolSize());
        assertEquals(12, executor.getQueueCapacity());
    }

    @Test
    void invalidAndUnavailableConfigKeepsExecutorBounded() {
        ConfigApi configApi = mock(ConfigApi.class);
        when(configApi.getConfigValueByKey(EduPublicCourseSyncExecutorConfiguration.KEY_POOL_SIZE)).thenReturn("0");
        when(configApi.getConfigValueByKey(EduPublicCourseSyncExecutorConfiguration.KEY_QUEUE_CAPACITY))
                .thenThrow(new IllegalStateException("配置服务不可用"));

        var executor = new EduPublicCourseSyncExecutorConfiguration().eduPublicCourseSyncExecutor(configApi);

        assertEquals(2, executor.getCorePoolSize());
        assertEquals(2, executor.getMaxPoolSize());
        assertEquals(64, executor.getQueueCapacity());
    }
}
