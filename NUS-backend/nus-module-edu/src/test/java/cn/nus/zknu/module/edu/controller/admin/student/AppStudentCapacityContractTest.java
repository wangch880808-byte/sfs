package cn.nus.zknu.module.edu.controller.admin.student;

import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentCapacityAnalysisRespVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppStudentCapacityContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void analysisResponseUsesStableContract() throws Exception {
        AppStudentCapacityAnalysisRespVO.IndicatorRate indicator =
                new AppStudentCapacityAnalysisRespVO.IndicatorRate();
        indicator.setIndicatorFirstId(6L);
        indicator.setIndicatorName("学科素养技能");
        indicator.setTotalTasks(4);
        indicator.setSubmittedTasks(4);
        indicator.setPendingReviewTasks(1);
        indicator.setEvaluatedTasks(3);
        indicator.setQualifiedTasks(3);
        indicator.setQualifiedRate(75.0);
        indicator.setEvaluationStatus("EVALUATED");

        AppStudentCapacityAnalysisRespVO response = new AppStudentCapacityAnalysisRespVO();
        response.setIndicators(List.of(indicator));
        response.setOverallRate(75.0);
        response.setQualified(true);
        response.setEvaluationStatus("EVALUATED");
        response.setTotalTasks(4);
        response.setSubmittedTasks(4);
        response.setPendingReviewTasks(1);
        response.setEvaluatedTasks(3);
        response.setQualifiedTasks(3);
        response.setUnindicatedEvaluatedCount(1);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));
        assertEquals(Set.of("indicators", "overallRate", "qualified", "evaluationStatus", "totalTasks",
                "submittedTasks", "pendingReviewTasks", "evaluatedTasks", "qualifiedTasks", "unindicatedEvaluatedCount"), fieldNames(json));
        assertEquals(1, json.get("unindicatedEvaluatedCount").intValue());
        assertEquals(Set.of("indicatorFirstId", "indicatorName", "totalTasks", "submittedTasks",
                        "pendingReviewTasks", "evaluatedTasks", "qualifiedTasks", "qualifiedRate", "evaluationStatus"),
                fieldNames(json.get("indicators").get(0)));
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
