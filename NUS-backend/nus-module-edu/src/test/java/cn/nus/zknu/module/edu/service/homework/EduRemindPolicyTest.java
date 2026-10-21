package cn.nus.zknu.module.edu.service.homework;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class EduRemindPolicyTest extends BaseMockitoUnitTest {

    @InjectMocks
    private EduRemindPolicy policy;
    @Mock
    private ConfigApi configApi;

    @Test
    void missingConfigFallsBackToDefaults() {
        assertTrue(policy.remindEnabled());
        assertEquals(24, policy.remindBeforeHours());
        assertEquals(60, policy.manualRemindIntervalMinutes());
        assertEquals(60, policy.teacherDigestMinutes());
    }

    @Test
    void validValuesAreUsed() {
        when(configApi.getConfigValueByKey(EduRemindPolicy.KEY_REMIND_ENABLED)).thenReturn(" FALSE ");
        when(configApi.getConfigValueByKey(EduRemindPolicy.KEY_REMIND_BEFORE_HOURS)).thenReturn("48");
        when(configApi.getConfigValueByKey(EduRemindPolicy.KEY_MANUAL_INTERVAL_MINUTES)).thenReturn("0");
        when(configApi.getConfigValueByKey(EduRemindPolicy.KEY_TEACHER_DIGEST_MINUTES)).thenReturn("0");

        assertFalse(policy.remindEnabled());
        assertEquals(48, policy.remindBeforeHours());
        assertEquals(0, policy.manualRemindIntervalMinutes());
        assertEquals(0, policy.teacherDigestMinutes());
    }

    @Test
    void invalidValuesFallBackToDefaults() {
        when(configApi.getConfigValueByKey(EduRemindPolicy.KEY_REMIND_ENABLED)).thenReturn("yes");
        when(configApi.getConfigValueByKey(EduRemindPolicy.KEY_REMIND_BEFORE_HOURS)).thenReturn("0");
        when(configApi.getConfigValueByKey(EduRemindPolicy.KEY_MANUAL_INTERVAL_MINUTES)).thenReturn("-5");
        when(configApi.getConfigValueByKey(EduRemindPolicy.KEY_TEACHER_DIGEST_MINUTES)).thenReturn("12.5");

        assertTrue(policy.remindEnabled());
        assertEquals(24, policy.remindBeforeHours());
        assertEquals(60, policy.manualRemindIntervalMinutes());
        assertEquals(60, policy.teacherDigestMinutes());
    }

    @Test
    void configReadFailureFallsBackToDefaults() {
        when(configApi.getConfigValueByKey(EduRemindPolicy.KEY_REMIND_BEFORE_HOURS))
                .thenThrow(new IllegalStateException("down"));

        assertEquals(24, policy.remindBeforeHours());
    }

}
