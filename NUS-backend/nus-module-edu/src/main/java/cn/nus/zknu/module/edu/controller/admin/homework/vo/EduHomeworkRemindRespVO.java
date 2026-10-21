package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 催交未提交学生 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EduHomeworkRemindRespVO {

    @Schema(description = "需要催交的学生数（未提交且还没过实际截止时间）", example = "5")
    private Integer targetCount;

    @Schema(description = "实际发出通知的学生数", example = "5")
    private Integer notifiedCount;

    @Schema(description = "下次可以催交的时间；没有发出催交时为空")
    private LocalDateTime nextAvailableTime;

}
