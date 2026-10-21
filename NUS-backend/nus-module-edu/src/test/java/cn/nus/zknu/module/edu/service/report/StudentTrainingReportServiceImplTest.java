package cn.nus.zknu.module.edu.service.report;

import cn.nus.zknu.framework.test.core.ut.BaseMockitoUnitTest;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentReportBasicRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentReportSkillsRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentReportTrendRespVO;
import cn.nus.zknu.module.edu.service.student.StudentCapacityService;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentTrainingReportServiceImplTest extends BaseMockitoUnitTest {

    @Mock
    private StudentCapacityService capacityService;
    @Mock
    private AdminUserService adminUserService;

    private StudentTrainingReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StudentTrainingReportServiceImpl();
        ReflectionTestUtils.setField(service, "capacityService", capacityService);
        ReflectionTestUtils.setField(service, "adminUserService", adminUserService);
        ReflectionTestUtils.setField(service, "pdfRenderer", new EduPdfReportRenderer());
    }

    @Test
    void exportUsesOnlyRequestedLoginStudentAndCreatesReadablePdf() throws Exception {
        Long studentId = 7L;
        AdminUserDO student = new AdminUserDO();
        student.setId(studentId);
        student.setUsername("00001234");
        student.setNickname("张三");
        AppStudentReportBasicRespVO basic = new AppStudentReportBasicRespVO();
        basic.setTotalTasks(10);
        basic.setSubmittedTasks(8);
        basic.setPendingReviewTasks(1);
        basic.setEvaluatedTasks(7);
        basic.setQualifiedTasks(7);
        basic.setQualifiedRate(70D);
        AppStudentReportSkillsRespVO.SkillItem skill = new AppStudentReportSkillsRespVO.SkillItem();
        skill.setIndicatorName("工程实践能力");
        skill.setQualifiedRate(85D);
        AppStudentReportSkillsRespVO skills = new AppStudentReportSkillsRespVO();
        skills.setSkills(List.of(skill));
        skills.setStrengths(List.of("工程实践能力"));
        skills.setWeaknesses(List.of());
        AppStudentReportTrendRespVO.TrendItem trendItem = new AppStudentReportTrendRespVO.TrendItem();
        trendItem.setRecordDate(LocalDate.of(2026, 8, 31));
        trendItem.setTotalScore(new BigDecimal("82.5"));
        AppStudentReportTrendRespVO trend = new AppStudentReportTrendRespVO();
        trend.setTrends(List.of(trendItem));
        when(adminUserService.getUser(studentId)).thenReturn(student);
        when(capacityService.getReportBasic(studentId)).thenReturn(basic);
        when(capacityService.getReportSkills(studentId)).thenReturn(skills);
        when(capacityService.getReportTrend(studentId)).thenReturn(trend);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.export(studentId, response);

        verify(capacityService).getReportBasic(studentId);
        verify(capacityService).getReportSkills(studentId);
        verify(capacityService).getReportTrend(studentId);
        assertEquals("application/pdf", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").contains("00001234"));
        byte[] content = response.getContentAsByteArray();
        Path output = Path.of("target", "test-reports", "student-training-report-sample.pdf");
        Files.createDirectories(output.getParent());
        Files.write(output, content);
        try (var document = Loader.loadPDF(content)) {
            assertEquals(1, document.getNumberOfPages());
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("个人训练报告"));
            assertTrue(text.contains("00001234"));
            assertTrue(text.contains("工程实践能力"));
            assertTrue(text.contains("2026-08-31"));
        }
    }
}
