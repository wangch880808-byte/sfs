package cn.nus.zknu.module.edu.service.report;

import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentReportBasicRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentReportSkillsRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentReportTrendRespVO;
import cn.nus.zknu.module.edu.service.student.StudentCapacityService;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.module.edu.enums.ErrorCodeConstants.STATISTICS_REPORT_EXPORT_FAILED;

@Service
public class StudentTrainingReportServiceImpl implements StudentTrainingReportService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource
    private StudentCapacityService capacityService;
    @Resource
    private AdminUserService adminUserService;
    @Resource
    private EduPdfReportRenderer pdfRenderer;

    @Override
    public void export(Long studentId, HttpServletResponse response) {
        AdminUserDO student = adminUserService.getUser(studentId);
        AppStudentReportBasicRespVO basic = capacityService.getReportBasic(studentId);
        AppStudentReportSkillsRespVO skills = capacityService.getReportSkills(studentId);
        AppStudentReportTrendRespVO trend = capacityService.getReportTrend(studentId);
        TeachingReportData data = buildReport(student, basic, skills, trend);
        try {
            byte[] content = pdfRenderer.render(data);
            String studentNo = student != null ? safe(student.getUsername()) : "student";
            String fileName = "个人训练报告_" + studentNo + "_" + LocalDateTime.now().format(FILE_TIME) + ".pdf";
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment;filename=" +
                    URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
            response.setContentLength(content.length);
            response.getOutputStream().write(content);
        } catch (IOException ex) {
            throw exception(STATISTICS_REPORT_EXPORT_FAILED);
        }
    }

    private TeachingReportData buildReport(AdminUserDO student, AppStudentReportBasicRespVO basic,
                                           AppStudentReportSkillsRespVO skills,
                                           AppStudentReportTrendRespVO trend) {
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("学号", student == null ? "" : safe(student.getUsername()));
        filters.put("姓名", student == null ? "" : safe(student.getNickname()));

        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("总任务数", text(basic.getTotalTasks()));
        summary.put("已提交", text(basic.getSubmittedTasks()));
        summary.put("待批改", text(basic.getPendingReviewTasks()));
        summary.put("已评定", text(basic.getEvaluatedTasks()));
        summary.put("已达标", text(basic.getQualifiedTasks()));
        summary.put("达标率", basic.getQualifiedRate() == null ? "待评定" : percent(basic.getQualifiedRate()));
        summary.put("可获证", basic.getCertificateEligible() == null ? "待评定"
                : basic.getCertificateEligible() ? "是" : "否");

        List<List<String>> rows = new ArrayList<>();
        List<String> strengths = skills.getStrengths() == null ? List.of() : skills.getStrengths();
        List<String> weaknesses = skills.getWeaknesses() == null ? List.of() : skills.getWeaknesses();
        if (skills.getSkills() != null) {
            for (AppStudentReportSkillsRespVO.SkillItem skill : skills.getSkills()) {
                String name = safe(skill.getIndicatorName());
                String evaluation = skill.getQualifiedRate() == null ? "待评定"
                        : strengths.contains(name) ? "优势" : weaknesses.contains(name) ? "薄弱" : "一般";
                rows.add(List.of("能力", name, percent(skill.getQualifiedRate()), evaluation));
            }
        }
        if (trend.getTrends() != null) {
            for (AppStudentReportTrendRespVO.TrendItem item : trend.getTrends()) {
                rows.add(List.of("趋势", text(item.getRecordDate()), text(item.getTotalScore()), "—"));
            }
        }
        return new TeachingReportData("个人训练报告", filters, summary,
                List.of("类型", "指标或日期", "达标率或综合分", "评价"),
                List.of(0.8F, 2F, 1.5F, 1F), rows);
    }

    private String percent(Double value) {
        return value == null ? "0%" : String.format(Locale.ROOT, "%.1f%%", value);
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
