package cn.nus.zknu.module.edu.service.report;

import jakarta.servlet.http.HttpServletResponse;

public interface StudentTrainingReportService {

    void export(Long studentId, HttpServletResponse response);
}
