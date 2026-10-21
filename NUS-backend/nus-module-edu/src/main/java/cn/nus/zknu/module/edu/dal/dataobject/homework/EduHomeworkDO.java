package cn.nus.zknu.module.edu.dal.dataobject.homework;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 实训任务 DO
 */
@TableName("edu_homework")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduHomeworkDO extends BaseDO {

    /**
     * 任务 ID
     */
    private Long id;
    /**
     * 任务名称
     */
    private String homeworkName;
    /**
     * 任务类型：1=平时任务, 2=考核任务
     */
    private Integer homeworkType;
    /**
     * 任务等级：0=专业, 1=初级, 2=中级, 3=高级
     */
    private Integer homeworkLevel;
    /**
     * 所属课程 ID
     */
    private Long courseId;
    /**
     * 所属班级 ID
     */
    private Long classId;
    /**
     * 学期名称（示例：2025-2026-2）
     */
    private String semesterName;
    /**
     * 一级技能指标 ID
     */
    private Long indicatorFirstId;
    /**
     * 二级技能指标 ID
     */
    private Long indicatorSecondId;
    /**
     * 任务要求（富文本）
     */
    private String requirement;
    /**
     * 截止时间
     */
    private LocalDateTime deadline;
    /**
     * 是否自动批改：0=手动, 1=自动AI批改
     */
    private Integer autoGrade;
    /**
     * 提交模式：text/image/audio/video/mixed
     */
    private String submissionMode;
    /**
     * AI 评阅模式：0=仅人工, 1=自动初评+人工复核
     */
    private Integer aiReviewMode;
    /**
     * 允许/要求的附件类型配置（JSON数组）
     */
    private String requiredAttachmentTypesJson;
    /**
     * AI 评分 rubric（JSON）
     */
    private String aiRubricJson;
    /**
     * AI 参考资料（JSON）
     */
    private String aiReferenceJson;
    /**
     * AI 初评置信度阈值
     */
    private Double aiConfidenceThreshold;
    /**
     * 及格分数线，默认60
     */
    private Integer passScore;
    /**
     * 满分值，默认100
     */
    private Integer score;
    /**
     * 发布教师 ID
     */
    private Long teacherId;
    /**
     * 状态：0=草稿, 1=已发布, 2=已截止, 3=已归档
     */
    private Integer status;

    /** 归档前状态，用于恢复草稿、已发布或已截止任务。 */
    private Integer statusBeforeArchive;

    /** 复制或公共课补发的来源任务 ID；直接新建的任务为空。 */
    private Long sourceHomeworkId;

}
