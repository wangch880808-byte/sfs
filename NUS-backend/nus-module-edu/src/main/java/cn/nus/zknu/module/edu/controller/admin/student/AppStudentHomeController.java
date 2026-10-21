package cn.nus.zknu.module.edu.controller.admin.student;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentHomeOverviewRespVO;
import cn.nus.zknu.module.edu.service.student.StudentHomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "学生端 - 首页")
@RestController
@RequestMapping("/edu/student/home")
@Validated
public class AppStudentHomeController {

    @Resource
    private StudentHomeService studentHomeService;

    @GetMapping("/overview")
    @Operation(summary = "首页概览")
    public CommonResult<AppStudentHomeOverviewRespVO> getOverview() {
        return success(studentHomeService.getOverview(getLoginUserId()));
    }

}
