package cn.nus.zknu.module.edu.controller.admin.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "留言详情响应")
@Data
public class MessageDetailRespVO {

    @Schema(description = "留言ID")
    private Long id;
    @Schema(description = "0=学生留言,1=教师回复")
    private Integer type;
    @Schema(description = "留言内容")
    private String content;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    private String senderName;
    private Integer readStatus;

}
