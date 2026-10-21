package cn.nus.zknu.module.edu.controller.admin.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "留言学生列表响应")
@Data
public class MessageStudentListRespVO {

    @Schema(description = "学生ID")
    private Long studentId;
    @Schema(description = "学生姓名")
    private String studentName;
    @Schema(description = "头像")
    private String avatar;
    @Schema(description = "最新留言时间")
    private LocalDateTime lastMessageTime;
    private String lastMessageContent;
    private Integer unreadCount;

}
