package cn.nus.zknu.module.edu.controller.admin.workspace;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.module.edu.controller.admin.workspace.vo.WorkspaceRuntimeRespVO;
import cn.nus.zknu.module.edu.controller.admin.workspace.vo.WorkspaceTodoRespVO;
import cn.nus.zknu.module.edu.service.workspace.EduWorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 工作台
 */
@Tag(name = "管理后台 - 工作台")
@RestController
@RequestMapping("/edu/workspace")
@Validated
public class EduWorkspaceController {

    @Resource
    private EduWorkspaceService eduWorkspaceService;

    /**
     * 获取待办事项（P1-2）
     */
    @GetMapping("/todos")
    @Operation(summary = "获取待办事项")
    @PreAuthorize("@ss.hasPermission('edu:workspace:query')")
    public CommonResult<WorkspaceTodoRespVO> getTodos() {
        return success(eduWorkspaceService.getTodos());
    }

    /**
     * 获取运行概况（P1-3）
     */
    @GetMapping("/runtime")
    @Operation(summary = "获取运行概况")
    @PreAuthorize("@ss.hasPermission('edu:workspace:query')")
    public CommonResult<WorkspaceRuntimeRespVO> getRuntime() {
        return success(eduWorkspaceService.getRuntime());
    }
}
