package cn.nus.zknu.module.edu.controller.admin.courseresource.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 课程资源上传 Request VO")
@Data
public class EduCourseResourceSaveReqVO {

    @Schema(description = "资源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "第一章课件")
    @NotBlank(message = "资源名称不能为空")
    private String resourceName;

    @Schema(description = "所属课程 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "课程 ID 不能为空")
    private Long courseId;

    @Schema(description = "文件URL", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://oss.example.com/file.pdf")
    @NotBlank(message = "文件 URL 不能为空")
    private String fileUrl;

    @Schema(description = "MIME类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "application/pdf")
    @NotBlank(message = "文件类型不能为空")
    private String fileType;

    @Schema(description = "文件大小（字节）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1048576")
    @NotNull(message = "文件大小不能为空")
    private Long fileSize;

    @Schema(description = "标准音频URL", example = "https://oss.example.com/audio.mp3")
    private String standardAudioUrl;

}
