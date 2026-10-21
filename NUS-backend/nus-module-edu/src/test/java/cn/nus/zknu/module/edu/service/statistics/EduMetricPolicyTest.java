package cn.nus.zknu.module.edu.service.statistics;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.homework.EduHomeworkDO;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import cn.nus.zknu.module.infra.api.config.ConfigApi;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class EduMetricPolicyTest extends BaseMockitoUnitTest {
    @InjectMocks private EduMetricPolicy policy;
    @Mock private ConfigApi configApi;
    @Mock private EduHomeworkAssigneeMapper assigneeMapper;

    @Test
    void expectedStudentsUsesPublishedAssignees() {
        when(assigneeMapper.selectCount(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class), eq(10L)))
                .thenReturn(3L);
        assertEquals(3L, policy.expectedStudents(10L));
    }

    @Test
    void boundariesAndHighPassScore() {
        assertEquals(EduMetricPolicy.ScoreBand.UNQUALIFIED, policy.scoreBand(59, 60, 100));
        assertEquals(EduMetricPolicy.ScoreBand.QUALIFIED, policy.scoreBand(60, 60, 100));
        assertEquals(EduMetricPolicy.ScoreBand.GOOD, policy.scoreBand(75, 60, 100));
        assertEquals(EduMetricPolicy.ScoreBand.EXCELLENT, policy.scoreBand(85, 60, 100));
        assertEquals(EduMetricPolicy.ScoreBand.UNQUALIFIED, policy.scoreBand(79, 80, 100));
        assertEquals(EduMetricPolicy.ScoreBand.GOOD, policy.scoreBand(80, 80, 100));
        assertEquals(EduMetricPolicy.ScoreBand.EXCELLENT, policy.scoreBand(85, 80, 100));
        assertEquals(EduMetricPolicy.ScoreBand.EXCELLENT, policy.scoreBand(90, 90, 100));
    }

    @Test
    void invalidRatiosFallBackTogether() {
        when(configApi.getConfigValueByKey("edu.score.good-ratio")).thenReturn("0.9");
        when(configApi.getConfigValueByKey("edu.score.excellent-ratio")).thenReturn("0.85");
        assertEquals(new EduMetricPolicy.ScoreRatios(0.75, 0.85), policy.scoreRatios());
    }

    @Test
    void passScoreUsesTaskThenConfiguredPercent() {
        EduHomeworkDO homework = new EduHomeworkDO();
        homework.setScore(80);
        homework.setPassScore(51);
        assertEquals(51, policy.passScore(homework));
        homework.setPassScore(null);
        when(configApi.getConfigValueByKey("edu.pass.score")).thenReturn("65");
        assertEquals(52, policy.passScore(homework));
        when(configApi.getConfigValueByKey("edu.pass.score")).thenReturn("invalid");
        assertEquals(48, policy.passScore(homework));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(value = {
            "60.25,121,60,60", "65,130,64,65", "NULL,120,59,60",
            "bad,120,59,60", "NaN,120,59,60", "Infinity,120,59,60", "-1,120,59,60", "101,120,59,60"
    }, nullValues = "NULL")
    void batchScoresPreserveFallbackNullFullScoreAndRounding(String configured, int first, int second, int third) {
        when(configApi.getConfigValueByKey("edu.pass.score")).thenReturn(configured);
        var tasks = List.of(task(1L, 200, null), task(2L, 99, null), task(3L, null, null), task(4L, 200, 125));
        assertEquals(Map.of(1L, first, 2L, second, 3L, third, 4L, 125), policy.passScores(tasks));
        org.mockito.Mockito.verify(configApi).getConfigValueByKey("edu.pass.score");
    }

    @Test
    void batchReadsConfigurationOnceAndExplicitScoresNeedNoConfiguration() {
        when(configApi.getConfigValueByKey("edu.pass.score")).thenThrow(new IllegalStateException("读取失败"));
        assertEquals(Map.of(1L, 120, 2L, 59), policy.passScores(List.of(task(1L, 200, null), task(2L, 99, null))));
        org.mockito.Mockito.verify(configApi).getConfigValueByKey("edu.pass.score");
        org.mockito.Mockito.clearInvocations(configApi);
        assertEquals(Map.of(1L, 130), policy.passScores(List.of(task(1L, 200, 130))));
        assertTrue(policy.passScores(List.of()).isEmpty());
        org.mockito.Mockito.verifyNoInteractions(configApi);
    }

    @Test
    void suppliedRatiosUseIdenticalBoundariesWithoutReadingConfiguration() {
        var ratios = new EduMetricPolicy.ScoreRatios(0.75, 0.85);
        assertEquals(EduMetricPolicy.ScoreBand.UNQUALIFIED, policy.scoreBand(59, 60, null, ratios));
        assertEquals(EduMetricPolicy.ScoreBand.QUALIFIED, policy.scoreBand(60, 60, null, ratios));
        assertEquals(EduMetricPolicy.ScoreBand.GOOD, policy.scoreBand(75, 60, null, ratios));
        assertEquals(EduMetricPolicy.ScoreBand.EXCELLENT, policy.scoreBand(85, 60, null, ratios));
        assertEquals(EduMetricPolicy.ScoreBand.GOOD, policy.scoreBand(80, 80, 100, ratios));
        assertEquals(EduMetricPolicy.ScoreBand.EXCELLENT, policy.scoreBand(90, 90, 100, ratios));
        org.mockito.Mockito.verifyNoInteractions(configApi);
    }

    private EduHomeworkDO task(Long id, Integer fullScore, Integer passScore) {
        var task = new EduHomeworkDO(); task.setId(id); task.setScore(fullScore); task.setPassScore(passScore);
        return task;
    }

    @Test
    void competitionRankAndWeights() {
        assertArrayEquals(new int[]{1, 1, 3}, policy.competitionRanks(List.of(90, 90, 80)));
        Map<Long, Double> rates = new LinkedHashMap<>();
        rates.put(1L, 50D);
        rates.put(2L, 100D);
        assertEquals(87.5, policy.weightedRate(rates, Map.of(1L, 1D, 2L, 3D)));
        assertEquals(75D, policy.weightedRate(rates, Map.of(1L, 1D)));
        assertEquals(75D, policy.weightedRate(rates, Map.of(1L, 0D, 2L, 0D)));
    }

    @Test
    void unlockBoundary() {
        assertTrue(policy.unlocked(2, Map.of()));
        assertTrue(policy.unlocked(3, Map.of(2, 80D)));
        assertFalse(policy.unlocked(3, Map.of(2, 79D)));
        assertTrue(policy.unlocked(2, Map.of(1, 80D)));
        assertFalse(policy.unlocked(2, Map.of(1, 79.99D)));
        assertFalse(policy.unlocked(3, Map.of(1, 80D, 2, 79D)));
        when(configApi.getConfigValueByKey("edu.course.unlock-ratio")).thenReturn("bad");
        assertEquals(0.8, policy.unlockRatio());
    }

    @Test
    void certificateThresholdUsesConfiguredPercentage() {
        when(configApi.getConfigValueByKey("edu.certificate.threshold")).thenReturn("75");
        assertEquals(75D, policy.certificateThreshold());
        when(configApi.getConfigValueByKey("edu.certificate.threshold")).thenReturn("110");
        assertEquals(80D, policy.certificateThreshold());
    }
}
