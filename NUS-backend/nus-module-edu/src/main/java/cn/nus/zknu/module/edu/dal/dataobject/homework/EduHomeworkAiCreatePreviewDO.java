package cn.nus.zknu.module.edu.dal.dataobject.homework;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI新建任务预览会话 DO
 */
@TableName("edu_homework_ai_create_preview")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduHomeworkAiCreatePreviewDO extends BaseDO {

    private Long id;

    private String clientRequestId;
    private String taskStatus;
    private Integer progress;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;

    /**
     * 预览编号
     */
    private String previewId;
    /**
     * 创建教师 ID
     */
    private Long teacherId;
    private Long courseId;
    private Long indicatorFirstId;
    private Long indicatorSecondId;
    private String classIdsJson;
    private String questionIdsJson;
    private String itemsJson;
    private Integer homeworkType;
    private Integer homeworkLevel;
    private LocalDateTime deadline;
    private Integer autoGrade;
    private String submissionMode;
    private Integer aiReviewMode;
    private Integer passScore;
    private Integer score;
    private LocalDateTime expireTime;
    /**
     * 是否已确认落库
     */
    private Boolean confirmed;

}
