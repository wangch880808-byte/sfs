package cn.nus.zknu.module.edu.service.report;

import java.util.List;
import java.util.Map;

public record TeachingReportData(String title,
                                 Map<String, String> filters,
                                 Map<String, String> summary,
                                 List<String> headers,
                                 List<Float> columnWeights,
                                 List<List<String>> rows) {
}
