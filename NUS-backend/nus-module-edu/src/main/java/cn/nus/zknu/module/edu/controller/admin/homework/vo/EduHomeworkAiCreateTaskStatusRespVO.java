package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - AI新建任务异步状态 Response VO")
@Data
public class EduHomeworkAiCreateTaskStatusRespVO {

    private Long taskId;
    private String status;
    private Integer progress;
    private String errorMessage;
    private EduHomeworkAiCreatePreviewRespVO preview;

}
