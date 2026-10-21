package cn.nus.zknu.module.edu.controller.admin.aireview;

import cn.nus.zknu.module.edu.controller.admin.aireview.vo.AiAnalysisClassReqVO;
import cn.nus.zknu.module.edu.controller.admin.aireview.vo.AiTaskStartRespVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiWorkflowContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void taskStartResponseOnlyExposesTaskId() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(new AiTaskStartRespVO(42L)));
        assertEquals(Set.of("taskId"), fieldNames(json));
        assertEquals(42L, json.get("taskId").longValue());
    }

    @Test
    void classStreamRequestUsesCourseClassAndHomeworkIds() throws Exception {
        AiAnalysisClassReqVO clazz = new AiAnalysisClassReqVO();
        clazz.setCourseId(1L);
        clazz.setClassId(2L);
        clazz.setHomeworkId(4L);
        assertEquals(Set.of("courseId", "classId", "homeworkId"),
                fieldNames(objectMapper.valueToTree(clazz)));
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
