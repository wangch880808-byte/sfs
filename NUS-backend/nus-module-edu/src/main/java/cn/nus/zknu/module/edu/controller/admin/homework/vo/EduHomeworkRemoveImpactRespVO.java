package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Schema(description = "管理后台 - 实训任务删除影响 Response VO")
@Data
@AllArgsConstructor
public class EduHomeworkRemoveImpactRespVO {

    @Schema(description = "任务数")
    private long homeworkCount;
    @Schema(description = "已提交记录数，含已批改、打回及重交")
    private long submittedCount;
    @Schema(description = "分配学生数，同一学生跨任务去重")
    private long studentCount;
}
