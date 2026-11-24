package cn.nus.zknu.module.edu.service.workspace;

import cn.nus.zknu.module.edu.controller.admin.workspace.vo.WorkspaceRuntimeRespVO;
import cn.nus.zknu.module.edu.controller.admin.workspace.vo.WorkspaceTodoRespVO;

/**
 * 工作台 Service 接口
 */
public interface EduWorkspaceService {

    /**
     * 获取待办事项（P1-2）
     */
    WorkspaceTodoRespVO getTodos();

    /**
     * 获取运行概况（P1-3）
     */
    WorkspaceRuntimeRespVO getRuntime();
}
