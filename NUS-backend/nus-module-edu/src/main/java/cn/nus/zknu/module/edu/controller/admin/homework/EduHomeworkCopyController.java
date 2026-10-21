package cn.nus.zknu.module.edu.controller.admin.homework;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.module.edu.controller.admin.homework.vo.copy.EduHomeworkCopyTargetRespVO;
import cn.nus.zknu.module.edu.service.homework.EduHomeworkCopyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 实训任务复制")
@RestController
@RequestMapping("/edu/homework/copy")
@Validated
public class EduHomeworkCopyController {

    @Resource
    private EduHomeworkCopyService copyService;

    @GetMapping("/targets")
    @Operation(summary = "列出某学期可以复制到的班级课程")
    @Parameter(name = "semesterName", description = "学期名称，为空时取当前学期", example = "2026-2027 上学期")
    @PreAuthorize("@ss.hasPermission('edu:homework:copy')")
    public CommonResult<List<EduHomeworkCopyTargetRespVO>> listTargets(
            @RequestParam(value = "semesterName", required = false) String semesterName) {
        return success(copyService.listTargets(semesterName));
    }

}
