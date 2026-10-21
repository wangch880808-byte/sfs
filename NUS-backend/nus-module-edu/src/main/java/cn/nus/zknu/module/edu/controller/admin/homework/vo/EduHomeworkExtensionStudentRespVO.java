package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 个别延期可选学生 Response VO")
@Data
public class EduHomeworkExtensionStudentRespVO {

    @Schema(description = "学生ID", example = "1")
    private Long studentId;

    @Schema(description = "学生姓名", example = "赵一鸣")
    private String studentName;

    @Schema(description = "学号", example = "2025001")
    private String studentNo;

    @Schema(description = "提交状态：0=未提交, 1=已提交待批改, 2=已批改, 3=已打回, 4=已重交", example = "0")
    private Integer recordStatus;

    @Schema(description = "已设置的个人截止时间，没有延期时为空")
    private LocalDateTime personalDeadline;

}
