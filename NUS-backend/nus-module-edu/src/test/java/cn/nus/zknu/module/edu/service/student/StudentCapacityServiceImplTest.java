package cn.nus.zknu.module.edu.service.student;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.student.StudentCapacityMapper;
import cn.nus.zknu.module.edu.dal.mysql.homework.EduHomeworkAssigneeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class StudentCapacityServiceImplTest extends BaseMockitoUnitTest {

    @Mock
    private StudentCapacityMapper capacityMapper;
    @Mock
    private EduClassStudentMapper classStudentMapper;
    @Mock
    private EduHomeworkAssigneeMapper assigneeMapper;

    private StudentCapacityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StudentCapacityServiceImpl();
        ReflectionTestUtils.setField(service, "studentCapacityMapper", capacityMapper);
        ReflectionTestUtils.setField(service, "classStudentMapper", classStudentMapper);
        ReflectionTestUtils.setField(service, "assigneeMapper", assigneeMapper);
        org.mockito.Mockito.lenient().when(assigneeMapper.selectListByStudentId(7L)).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "metricPolicy", new cn.nus.zknu.module.edu.service.statistics.EduMetricPolicy());
        EduClassStudentDO relation = new EduClassStudentDO();
        relation.setClassId(9L);
        relation.setStudentId(7L);
        when(classStudentMapper.selectListByStudentIds(List.of(7L))).thenReturn(List.of(relation));
    }

    @Test
    void reportTrendReadsAggregatedScoresWithoutRankingTable() {
        when(capacityMapper.selectScoreTrend(7L, List.of(9L))).thenReturn(List.of(Map.of(
                "recordDate", java.sql.Date.valueOf("2026-09-01"),
                "totalScore", new java.math.BigDecimal("85"))));

        var result = service.getReportTrend(7L);

        assertEquals(1, result.getTrends().size());
        assertEquals(java.time.LocalDate.of(2026, 9, 1), result.getTrends().get(0).getRecordDate());
        assertEquals(new java.math.BigDecimal("85"), result.getTrends().get(0).getTotalScore());
    }

    @Test
    void pendingReviewDoesNotProduceUnqualifiedConclusion() {
        when(capacityMapper.selectFirstIndicatorRates(7L, List.of(9L))).thenReturn(List.of(Map.of(
                "indicatorFirstId", 1L,
                "indicatorName", "教学能力",
                "totalTasks", 2,
                "submittedTasks", 1,
                "pendingReviewTasks", 1,
                "evaluatedTasks", 0,
                "qualifiedTasks", 0)));

        when(capacityMapper.selectOverallStats(7L, List.of(9L))).thenReturn(stats(2, 1, 1, 0, 0, 0));

        var result = service.getCapacityAnalysis(7L);

        assertEquals("PENDING", result.getEvaluationStatus());
        assertNull(result.getOverallRate());
        assertNull(result.getQualified());
        assertNull(result.getIndicators().get(0).getQualifiedRate());
    }

    @Test
    void qualifiedRateUsesOnlyEvaluatedTasks() {
        when(capacityMapper.selectFirstIndicatorRates(7L, List.of(9L))).thenReturn(List.of(Map.of(
                "indicatorFirstId", 1L,
                "indicatorName", "教学能力",
                "totalTasks", 4,
                "submittedTasks", 3,
                "pendingReviewTasks", 1,
                "evaluatedTasks", 2,
                "qualifiedTasks", 1)));

        when(capacityMapper.selectOverallStats(7L, List.of(9L))).thenReturn(stats(4, 3, 1, 2, 1, 0));

        var result = service.getCapacityAnalysis(7L);

        assertEquals("EVALUATED", result.getEvaluationStatus());
        assertEquals(50.0, result.getOverallRate());
        assertFalse(result.getQualified());
    }

    @Test
    void pendingIndicatorsAreNeitherStrengthsNorWeaknesses() {
        when(capacityMapper.selectFirstIndicatorRates(7L, List.of(9L))).thenReturn(List.of(Map.of(
                "indicatorFirstId", 1L,
                "indicatorName", "教学能力",
                "totalTasks", 2,
                "submittedTasks", 1,
                "pendingReviewTasks", 1,
                "evaluatedTasks", 0,
                "qualifiedTasks", 0)));

        var result = service.getReportSkills(7L);

        assertTrue(result.getStrengths().isEmpty());
        assertTrue(result.getWeaknesses().isEmpty());
        assertNull(result.getSkills().get(0).getQualifiedRate());
    }
    @Test
    void unindicatedEvaluatedTaskCountsInBothPagesButNotInCapacityProfile() {
        when(capacityMapper.selectOverallStats(7L, List.of(9L))).thenReturn(stats(2, 2, 0, 2, 1, 1));
        when(capacityMapper.selectFirstIndicatorRates(7L, List.of(9L))).thenReturn(List.of(Map.of(
                "indicatorFirstId", 1L, "indicatorName", "教学能力", "indicatorWeight", 1,
                "totalTasks", 1, "submittedTasks", 1, "pendingReviewTasks", 0,
                "evaluatedTasks", 1, "qualifiedTasks", 0)));

        var analysis = service.getCapacityAnalysis(7L);
        var report = service.getReportBasic(7L);

        assertEquals(1, analysis.getUnindicatedEvaluatedCount());
        assertEquals(2, analysis.getTotalTasks());
        assertEquals(2, analysis.getEvaluatedTasks());
        assertEquals(1, analysis.getQualifiedTasks());
        assertEquals(analysis.getTotalTasks(), report.getTotalTasks());
        assertEquals(analysis.getSubmittedTasks(), report.getSubmittedTasks());
        assertEquals(analysis.getPendingReviewTasks(), report.getPendingReviewTasks());
        assertEquals(analysis.getEvaluatedTasks(), report.getEvaluatedTasks());
        assertEquals(analysis.getQualifiedTasks(), report.getQualifiedTasks());
        assertEquals(50.0, report.getQualifiedRate());
        assertEquals(0.0, analysis.getOverallRate());
        assertFalse(analysis.getQualified());
        assertEquals(analysis.getQualified(), report.getCertificateEligible());
        assertEquals(1, analysis.getIndicators().get(0).getTotalTasks());
    }

    @Test
    void onlyUnindicatedEvaluatedTaskDoesNotLeaveReportCertificateEmpty() {
        when(capacityMapper.selectOverallStats(7L, List.of(9L))).thenReturn(stats(1, 1, 0, 1, 1, 1));
        when(capacityMapper.selectFirstIndicatorRates(7L, List.of(9L))).thenReturn(List.of());

        var analysis = service.getCapacityAnalysis(7L);
        var report = service.getReportBasic(7L);

        assertEquals(1, analysis.getUnindicatedEvaluatedCount());
        assertEquals(1, analysis.getEvaluatedTasks());
        assertEquals(analysis.getTotalTasks(), report.getTotalTasks());
        assertEquals(analysis.getEvaluatedTasks(), report.getEvaluatedTasks());
        assertEquals(analysis.getQualifiedTasks(), report.getQualifiedTasks());
        assertEquals(100.0, report.getQualifiedRate());
        assertFalse(report.getCertificateEligible());
        assertEquals("EVALUATED", report.getEvaluationStatus());
        assertEquals("PENDING", analysis.getEvaluationStatus());
        assertNull(analysis.getOverallRate());
        assertNull(analysis.getQualified());
        assertTrue(analysis.getIndicators().isEmpty());
    }

    @Test
    void certificateUsesIndicatedRateWhileReportUsesAllTasks() {
        when(capacityMapper.selectOverallStats(7L, List.of(9L))).thenReturn(stats(4, 4, 0, 4, 3, 1));
        when(capacityMapper.selectFirstIndicatorRates(7L, List.of(9L))).thenReturn(List.of(Map.of(
                "indicatorFirstId", 1L, "indicatorName", "教学能力", "indicatorWeight", 1,
                "totalTasks", 3, "submittedTasks", 3, "pendingReviewTasks", 0,
                "evaluatedTasks", 3, "qualifiedTasks", 2)));

        var analysis = service.getCapacityAnalysis(7L);
        var report = service.getReportBasic(7L);

        assertEquals(66.7, analysis.getOverallRate());
        assertFalse(analysis.getQualified());
        assertEquals(75.0, report.getQualifiedRate());
        assertFalse(report.getCertificateEligible());
    }

    @Test
    void failingUnindicatedTaskDoesNotPreventCertificateForQualifiedProfile() {
        when(capacityMapper.selectOverallStats(7L, List.of(9L))).thenReturn(stats(2, 2, 0, 2, 1, 1));
        when(capacityMapper.selectFirstIndicatorRates(7L, List.of(9L))).thenReturn(List.of(Map.of(
                "indicatorFirstId", 1L, "indicatorName", "教学能力", "indicatorWeight", 1,
                "totalTasks", 1, "submittedTasks", 1, "pendingReviewTasks", 0,
                "evaluatedTasks", 1, "qualifiedTasks", 1)));

        var analysis = service.getCapacityAnalysis(7L);
        var report = service.getReportBasic(7L);

        assertEquals(100.0, analysis.getOverallRate());
        assertTrue(analysis.getQualified());
        assertEquals(50.0, report.getQualifiedRate());
        assertTrue(report.getCertificateEligible());
    }

    @Test
    void noEvaluatedTasksKeepRatesAndCertificatePending() {
        when(capacityMapper.selectOverallStats(7L, List.of(9L))).thenReturn(stats(1, 1, 1, 0, 0, 0));
        when(capacityMapper.selectFirstIndicatorRates(7L, List.of(9L))).thenReturn(List.of());

        var analysis = service.getCapacityAnalysis(7L);
        var report = service.getReportBasic(7L);

        assertEquals(0, analysis.getUnindicatedEvaluatedCount());
        assertEquals(1, analysis.getPendingReviewTasks());
        assertNull(analysis.getOverallRate());
        assertNull(report.getQualifiedRate());
        assertNull(report.getCertificateEligible());
        assertEquals("PENDING", report.getEvaluationStatus());
    }

    @Test
    void studentWithoutClassesOrAssignmentsHasZeroCounts() {
        when(classStudentMapper.selectListByStudentIds(List.of(7L))).thenReturn(List.of());

        var analysis = service.getCapacityAnalysis(7L);
        var report = service.getReportBasic(7L);

        assertEquals(0, analysis.getUnindicatedEvaluatedCount());
        assertEquals(0, analysis.getTotalTasks());
        assertEquals(analysis.getTotalTasks(), report.getTotalTasks());
        assertNull(report.getCertificateEligible());
        org.mockito.Mockito.verifyNoInteractions(capacityMapper);
    }

    private Map<String, Object> stats(int total, int submitted, int pendingReview, int evaluated,
                                      int qualified, int unindicated) {
        return Map.of("totalTasks", total, "submittedTasks", submitted, "pendingReviewTasks", pendingReview,
                "evaluatedTasks", evaluated, "qualifiedTasks", qualified,
                "unindicatedEvaluatedCount", unindicated);
    }

}
