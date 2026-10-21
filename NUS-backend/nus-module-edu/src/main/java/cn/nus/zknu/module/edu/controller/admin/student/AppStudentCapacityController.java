package cn.nus.zknu.module.edu.controller.admin.student;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.module.edu.controller.admin.student.vo.*;
import cn.nus.zknu.module.edu.service.student.StudentCapacityService;
import cn.nus.zknu.module.edu.service.report.StudentTrainingReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "学生端 - 能力分析")
@RestController
@RequestMapping("/edu/student/capacity")
@Validated
public class AppStudentCapacityController {

    @Resource
    private StudentCapacityService studentCapacityService;
    @Resource
    private StudentTrainingReportService studentTrainingReportService;

    @GetMapping("/analysis")
    @Operation(summary = "能力分析概览")
    public CommonResult<AppStudentCapacityAnalysisRespVO> getCapacityAnalysis() {
        return success(studentCapacityService.getCapacityAnalysis(getLoginUserId()));
    }

    @GetMapping("/detail")
    @Operation(summary = "指标详情")
    @Parameter(name = "indicatorFirstId", description = "一级指标ID", required = true, example = "1")
    public CommonResult<AppStudentCapacityDetailRespVO> getCapacityDetail(
            @RequestParam("indicatorFirstId") Long indicatorFirstId) {
        return success(studentCapacityService.getCapacityDetail(getLoginUserId(), indicatorFirstId));
    }

    @GetMapping("/report/basic")
    @Operation(summary = "实训报告基础数据")
    public CommonResult<AppStudentReportBasicRespVO> getReportBasic() {
        return success(studentCapacityService.getReportBasic(getLoginUserId()));
    }

    @GetMapping("/report/skills")
    @Operation(summary = "技能分析")
    public CommonResult<AppStudentReportSkillsRespVO> getReportSkills() {
        return success(studentCapacityService.getReportSkills(getLoginUserId()));
    }

    @GetMapping("/report/trend")
    @Operation(summary = "成绩趋势")
    public CommonResult<AppStudentReportTrendRespVO> getReportTrend() {
        return success(studentCapacityService.getReportTrend(getLoginUserId()));
    }

    @GetMapping("/report/export-pdf")
    @Operation(summary = "导出个人训练报告 PDF")
    public void exportReportPdf(HttpServletResponse response) {
        studentTrainingReportService.export(getLoginUserId(), response);
    }

}
