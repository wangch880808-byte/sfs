package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "管理后台 - 批改队列 Response VO：任务下教师可批改的全部提交，不分页、不截断")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EduHomeworkRecordQueueRespVO {

    @Schema(description = "提交总数，与 list 的长度一致", example = "45")
    private Integer total;

    @Schema(description = "按状态、提交时间排列的提交")
    private List<EduHomeworkRecordQueueItemRespVO> list;

}
