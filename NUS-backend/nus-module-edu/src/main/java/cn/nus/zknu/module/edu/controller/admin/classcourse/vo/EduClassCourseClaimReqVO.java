package cn.nus.zknu.module.edu.controller.admin.classcourse.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.util.List;

@Schema(description = "管理后台 - 公共课认领 Request VO")
@Data
public class EduClassCourseClaimReqVO {
    @NotNull
    @Positive
    private Long courseId;
    @NotEmpty
    private List<@NotNull @Positive Long> classIds;
    private String semesterName;
}
