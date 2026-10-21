package cn.nus.zknu.module.edu.controller.admin.student;

import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentCourseDetailRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentCourseRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentProfileRespVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AppStudentCourseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void courseListResponseContainsCompleteStudentContract() throws Exception {
        AppStudentCourseRespVO response = new AppStudentCourseRespVO();
        response.setResources(List.of(new AppStudentCourseDetailRespVO.Resource()));
        response.setHomeworkList(List.of(new AppStudentCourseDetailRespVO.Homework()));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        assertEquals(Set.of("courseId", "courseName", "coverUrl", "description", "teacherId", "teacherName",
                "indicatorId", "indicatorName", "totalHomework", "completedHomework", "avgScore", "resources",
                "homeworkList"), fieldNames(json));
        assertEquals(Set.of("id", "resourceName", "fileType", "fileSize"),
                fieldNames(json.get("resources").get(0)));
        assertFalse(json.get("resources").get(0).has("fileUrl"));
        assertEquals(Set.of("homeworkId", "homeworkName", "homeworkType", "homeworkLevel", "deadline", "status",
                "score"), fieldNames(json.get("homeworkList").get(0)));
    }

    @Test
    void studentProfileResponseUsesStableBaseInformationContract() throws Exception {
        AppStudentProfileRespVO response = new AppStudentProfileRespVO();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        assertEquals(Set.of("userId", "username", "nickname", "avatar", "mobile", "email", "deptId", "deptName",
                "gradeYear", "classIds", "classNames", "className", "majorName"), fieldNames(json));
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

}
