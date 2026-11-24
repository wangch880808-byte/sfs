package cn.nus.zknu.module.edu.controller.admin.courseresource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 课程资源 Response VO")
@Data
public class EduCourseResourceRespVO {

    @Schema(description = "资源 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "资源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "第一章课件")
    private String resourceName;

    @Schema(description = "所属课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long courseId;

    @Schema(description = "文件URL", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://oss.example.com/file.pdf")
    private String fileUrl;

    @Schema(description = "MIME类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "application/pdf")
    private String fileType;

    @Schema(description = "文件大小（字节）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1048576")
    private Long fileSize;

    @Schema(description = "标准音频URL", example = "https://oss.example.com/audio.mp3")
    private String standardAudioUrl;

    @Schema(description = "审核状态：0 待审核，1 通过，2 拒绝", example = "0")
    private Integer reviewStatus;

    @Schema(description = "审核原因（驳回时填写）", example = "内容不合规")
    private String reviewReason;

    @Schema(description = "审核人 ID", example = "1024")
    private Long reviewerId;

    @Schema(description = "审核时间")
    private LocalDateTime reviewedAt;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
