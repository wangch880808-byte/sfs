package cn.nus.zknu.module.edu.service.student;

import cn.nus.zknu.module.edu.controller.admin.student.vo.*;

/**
 * 学生能力分析 Service 接口
 */
public interface StudentCapacityService {

    /**
     * 能力分析概览（雷达图数据：各一级指标达标率）
     */
    AppStudentCapacityAnalysisRespVO getCapacityAnalysis(Long studentId);

    /**
     * 指标详情（二级指标+任务列表）
     */
    AppStudentCapacityDetailRespVO getCapacityDetail(Long studentId, Long indicatorFirstId);

    /**
     * 实训报告基础数据
     */
    AppStudentReportBasicRespVO getReportBasic(Long studentId);

    /**
     * 技能分析（各指标达标率+优势/薄弱）
     */
    AppStudentReportSkillsRespVO getReportSkills(Long studentId);

    /**
     * 成绩趋势
     */
    AppStudentReportTrendRespVO getReportTrend(Long studentId);

}
