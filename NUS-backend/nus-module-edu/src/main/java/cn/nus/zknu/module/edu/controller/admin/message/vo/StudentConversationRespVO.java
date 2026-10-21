package cn.nus.zknu.module.edu.controller.admin.message.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 学生的班课会话。 */
@Data
public class StudentConversationRespVO {
    private Long classId;
    private Long courseId;
    private String className;
    private String courseName;
    private String teacherName;
    private String lastMessageContent;
    private LocalDateTime lastMessageTime;
    private Integer unreadCount;
    private Integer status;
}
