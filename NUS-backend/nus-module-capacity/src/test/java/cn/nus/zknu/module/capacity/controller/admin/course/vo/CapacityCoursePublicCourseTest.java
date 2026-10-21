package cn.nus.zknu.module.capacity.controller.admin.course.vo;

import cn.nus.zknu.module.capacity.api.course.dto.CapacityCourseRespDTO;
import cn.nus.zknu.module.capacity.enums.CapacityCourseTypeConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CapacityCoursePublicCourseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void publicCourseIsDerivedFromTheCurrentTypeForBothResponses() {
        CapacityCourseRespVO vo = new CapacityCourseRespVO();
        CapacityCourseRespDTO dto = new CapacityCourseRespDTO();
        for (Integer type : Arrays.asList(CapacityCourseTypeConstants.PUBLIC_COURSE,
                CapacityCourseTypeConstants.EXCLUSIVE_COURSE, null, 2)) {
            vo.setIsPublicCourse(type);
            dto.setIsPublicCourse(type);
            boolean expected = Integer.valueOf(CapacityCourseTypeConstants.PUBLIC_COURSE).equals(type);

            assertEquals(expected, vo.getPublicCourse());
            assertEquals(expected, dto.getPublicCourse());
            assertEquals(expected, mapper.valueToTree(vo).path("publicCourse").asBoolean());
            assertEquals(expected, mapper.valueToTree(dto).path("publicCourse").asBoolean());
        }
    }

    @Test
    void incomingDerivedFieldCannotOverrideTheStoredCourseType() throws Exception {
        String json = "{\"isPublicCourse\":1,\"publicCourse\":true}";

        CapacityCourseRespVO vo = mapper.readValue(json, CapacityCourseRespVO.class);
        CapacityCourseRespDTO dto = mapper.readValue(json, CapacityCourseRespDTO.class);

        assertEquals(CapacityCourseTypeConstants.EXCLUSIVE_COURSE, vo.getIsPublicCourse());
        assertEquals(CapacityCourseTypeConstants.EXCLUSIVE_COURSE, dto.getIsPublicCourse());
        assertFalse(vo.getPublicCourse());
        assertFalse(dto.getPublicCourse());
    }

    @Test
    void onlinePublicCourseStillRequiresApprovalAndActiveStatus() {
        CapacityCourseRespDTO dto = new CapacityCourseRespDTO();
        dto.setIsPublicCourse(CapacityCourseTypeConstants.PUBLIC_COURSE);
        dto.setPublicAuditStatus(CapacityCourseRespDTO.PUBLIC_AUDIT_APPROVED);
        dto.setStatus(0);
        assertTrue(dto.isOnlinePublicCourse());
        dto.setPublicAuditStatus(1);
        assertFalse(dto.isOnlinePublicCourse());
        dto.setPublicAuditStatus(CapacityCourseRespDTO.PUBLIC_AUDIT_APPROVED);
        dto.setStatus(1);
        assertFalse(dto.isOnlinePublicCourse());
        dto.setStatus(0);
        dto.setIsPublicCourse(CapacityCourseTypeConstants.EXCLUSIVE_COURSE);
        assertFalse(dto.isOnlinePublicCourse());
    }

}
