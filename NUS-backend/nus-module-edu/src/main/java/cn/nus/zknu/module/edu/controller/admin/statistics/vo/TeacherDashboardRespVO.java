package cn.nus.zknu.module.edu.controller.admin.statistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "教师工作台响应")
@Data
public class TeacherDashboardRespVO {

    @Schema(description = "我的课程数")
    private Long myCourses;
    @Schema(description = "待批改数")
    private Long pendingCorrections;
    @Schema(description = "未读留言数")
    private Long unreadMessages;
    @Schema(description = "本周新提交数")
    private Long weekSubmissions;
    @Schema(description = "最近任务列表")
    private List<Map<String, Object>> recentHomeworks;

}
