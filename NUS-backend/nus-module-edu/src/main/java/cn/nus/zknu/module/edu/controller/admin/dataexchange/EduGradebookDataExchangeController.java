package cn.nus.zknu.module.edu.controller.admin.dataexchange;

import cn.nus.zknu.framework.apilog.core.annotation.ApiAccessLog;
import cn.nus.zknu.module.edu.service.dataexchange.gradebook.GradebookDataExchangeHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static cn.nus.zknu.framework.apilog.core.enums.OperateTypeEnum.EXPORT;

@Tag(name = "管理后台 - 成绩册数据交换")
@RestController
@RequestMapping("/edu/homework-record/gradebook")
public class EduGradebookDataExchangeController {

    @Resource
    private GradebookDataExchangeHandler gradebookHandler;

    @GetMapping("/export")
    @Operation(summary = "导出任务成绩册及成绩导入模板")
    @PreAuthorize("@ss.hasPermission('edu:homework-record:query')")
    @ApiAccessLog(operateType = EXPORT)
    public void export(@RequestParam("homeworkId") Long homeworkId, HttpServletResponse response) throws IOException {
        EduDataExchangeController.writeXlsx(response, "任务成绩册.xlsx", gradebookHandler.export(homeworkId));
    }

}
