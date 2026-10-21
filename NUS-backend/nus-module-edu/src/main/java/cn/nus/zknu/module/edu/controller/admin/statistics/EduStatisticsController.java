package cn.nus.zknu.module.edu.controller.admin.statistics;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.module.edu.controller.admin.statistics.vo.*;
import cn.nus.zknu.module.edu.service.statistics.EduStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 统计分析")
@RestController
@RequestMapping("/edu/statistics")
@Validated
public class EduStatisticsController {

    @Resource
    private EduStatisticsService eduStatisticsService;

    @GetMapping("/total-pass-rate")
    @Operation(summary = "总体合格率统计")
    @PreAuthorize("@ss.hasPermission('edu:statistics:query')")
    public CommonResult<TotalPassRateRespVO> getTotalPassRate(TotalPassRateReqVO reqVO) {
        return success(eduStatisticsService.getTotalPassRate(reqVO));
    }

    @GetMapping("/work-trend")
    @Operation(summary = "任务成绩趋势")
    @PreAuthorize("@ss.hasPermission('edu:statistics:query')")
    public CommonResult<WorkTrendRespVO> getWorkTrend(@RequestParam("courseId") Long courseId,
                                                      @RequestParam(value = "classId", required = false) Long classId) {
        return success(eduStatisticsService.getWorkTrend(courseId, classId));
    }

    @GetMapping("/course-analysis")
    @Operation(summary = "课程分析")
    @PreAuthorize("@ss.hasPermission('edu:statistics:query')")
    public CommonResult<CourseAnalysisRespVO> getCourseAnalysis(@RequestParam("courseId") Long courseId,
                                                                @RequestParam(value = "classId", required = false) Long classId) {
        return success(eduStatisticsService.getCourseAnalysis(courseId, classId));
    }

    @GetMapping("/course-options")
    @Operation(summary = "教师课程选项")
    @PreAuthorize("@ss.hasPermission('edu:statistics:query')")
    public CommonResult<List<Map<String, Object>>> getCourseOptions() {
        return success(eduStatisticsService.getCourseOptions());
    }

    @GetMapping("/class-options")
    @Operation(summary = "课程下的班级选项")
    @PreAuthorize("@ss.hasPermission('edu:statistics:query')")
    public CommonResult<List<Map<String, Object>>> getClassOptions(@RequestParam("courseId") Long courseId) {
        return success(eduStatisticsService.getClassOptions(courseId));
    }

    @GetMapping("/admin/dashboard")
    @Operation(summary = "管理员首页看板")
    @PreAuthorize("@ss.hasPermission('edu:statistics:query')")
    public CommonResult<AdminDashboardRespVO> getAdminDashboard() {
        return success(eduStatisticsService.getAdminDashboard());
    }

    @GetMapping("/teacher/dashboard")
    @Operation(summary = "教师工作台")
    @PreAuthorize("@ss.hasPermission('edu:statistics:query')")
    public CommonResult<TeacherDashboardRespVO> getTeacherDashboard() {
        return success(eduStatisticsService.getTeacherDashboard());
    }

}
