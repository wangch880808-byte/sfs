package cn.nus.zknu.module.edu.dal.dataobject.aireview;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 作业 AI 初评草案
 */
@TableName("edu_homework_ai_review")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduHomeworkAiReviewDO extends BaseDO {

    private Long id;

    private Long homeworkRecordId;

    private Long homeworkId;

    private Long studentId;

    /**
     * 0=排队中,1=处理中,2=已生成,3=失败,4=已采纳,5=已拒绝,6=已跳过
     */
    private Integer status;

    private String submissionMode;

    private String generationToken;

    private LocalDateTime sourceSubmitTime;

    private Long modelId;

    private String modelName;

    private String transcript;

    private String evidenceJson;

    private Integer aiScore;

    private String aiComment;

    private Double aiConfidence;

    private String subScoresJson;

    private String riskFlagsJson;

    /**
     * 教师处理结果：accept/adjust/reject
     */
    private String teacherDecision;

    private Integer teacherScore;

    private String teacherComment;

    private String rawResponse;

    private String errorMessage;
}
