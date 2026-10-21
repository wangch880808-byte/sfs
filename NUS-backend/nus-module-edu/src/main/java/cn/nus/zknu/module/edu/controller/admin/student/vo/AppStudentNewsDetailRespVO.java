package cn.nus.zknu.module.edu.controller.admin.student.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "学生端 - 校园资讯详情 Response VO")
@Data
public class AppStudentNewsDetailRespVO {

    @Schema(description = "资讯ID", example = "1")
    private Long id;

    @Schema(description = "来源编码", example = "zsxw")
    private String sourceCode;

    @Schema(description = "来源名称", example = "周师要闻")
    private String sourceName;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "作者")
    private String author;

    @Schema(description = "发布日期")
    private LocalDate publishDate;

    @Schema(description = "原文链接")
    private String articleUrl;

    @Schema(description = "正文纯文本")
    private String contentText;

    @Schema(description = "正文HTML")
    private String contentHtml;

    @Schema(description = "原始元信息")
    private String rawMeta;

    @Schema(description = "首次抓取时间")
    private LocalDateTime firstCrawledAt;

    @Schema(description = "最近抓取时间")
    private LocalDateTime lastCrawledAt;

}
