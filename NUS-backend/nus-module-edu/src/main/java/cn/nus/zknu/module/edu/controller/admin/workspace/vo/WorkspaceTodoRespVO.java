package cn.nus.zknu.module.edu.controller.admin.workspace.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 工作台待办事项响应 VO（P1-2）
 */
@Schema(description = "工作台待办事项响应")
@Data
public class WorkspaceTodoRespVO {

    @Schema(description = "待办列表")
    private List<TodoItem> todos;

    @Schema(description = "待办事项")
    @Data
    public static class TodoItem {
        @Schema(description = "分类标识")
        private String category;
        @Schema(description = "分类名称")
        private String label;
        @Schema(description = "数量")
        private Long count;
        @Schema(description = "前端跳转路由")
        private String route;
    }
}
