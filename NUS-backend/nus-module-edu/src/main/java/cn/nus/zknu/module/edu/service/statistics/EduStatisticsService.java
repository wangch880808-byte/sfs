package cn.nus.zknu.module.edu.service.statistics;

import cn.nus.zknu.module.edu.controller.admin.statistics.vo.*;

import java.util.List;
import java.util.Map;

/**
 * 统计分析 Service 接口
 */
public interface EduStatisticsService {

    TotalPassRateRespVO getTotalPassRate(TotalPassRateReqVO reqVO);

    WorkTrendRespVO getWorkTrend(Long courseId, Long classId);

    CourseAnalysisRespVO getCourseAnalysis(Long courseId, Long classId);

    List<Map<String, Object>> getCourseOptions();

    List<Map<String, Object>> getClassOptions(Long courseId);

    AdminDashboardRespVO getAdminDashboard();

    TeacherDashboardRespVO getTeacherDashboard();

}
