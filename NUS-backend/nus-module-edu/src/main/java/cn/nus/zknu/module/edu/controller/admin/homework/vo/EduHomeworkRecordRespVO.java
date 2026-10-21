package cn.nus.zknu.module.edu.controller.admin.homework.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 任务提交记录 Response VO")
@Data
public class EduHomeworkRecordRespVO {

    @Schema(description = "记录 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "关联任务 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long homeworkId;

    @Schema(description = "学生 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long studentId;

    @Schema(description = "学生姓名", example = "张三")
    private String studentName;

    @Schema(description = "学号", example = "202301001")
    private String studentNo;

    @Schema(description = "班级排名", example = "3")
    private Integer classRank;

    @Schema(description = "参与排名总人数", example = "32")
    private Integer rankTotalStudents;

    @Schema(description = "提交内容", example = "这是我的任务内容...")
    private String content;

    @Schema(description = "附件URL列表（JSON数组）", example = "[\"https://oss.example.com/file1.pdf\"]")
    private String attachmentUrls;

    @Schema(description = "提交时间")
    private LocalDateTime submitTime;

    @Schema(description = "得分（0-100）", example = "85")
    private Integer score;

    @Schema(description = "教师/AI评语", example = "完成度较好")
    private String comment;

    @Schema(description = "批改方式：1=手动, 2=AI", example = "1")
    private Integer correctType;

    @Schema(description = "批改时间")
    private LocalDateTime correctTime;

    @Schema(description = "状态：0=未提交, 1=已提交待批改, 2=已批改, 3=已打回重做, 4=已重新提交", example = "1")
    private Integer status;

    @Schema(description = "累计打回重做次数", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer redoCount;

    @Schema(description = "重做截止时间，为空时沿用任务截止时间")
    private LocalDateTime redoDeadline;

    @Schema(description = "AI 初评状态：0=排队中,1=处理中,2=已生成,3=失败,4=已采纳,5=已拒绝,6=已跳过")
    private Integer aiReviewStatus;

    @Schema(description = "AI 初评分数")
    private Integer aiReviewScore;

    @Schema(description = "AI 初评评语")
    private String aiReviewComment;

    @Schema(description = "AI 初评置信度")
    private Double aiReviewConfidence;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "更新时间；批改、打回时作为 expectedUpdateTime 传回")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime updateTime;

}
