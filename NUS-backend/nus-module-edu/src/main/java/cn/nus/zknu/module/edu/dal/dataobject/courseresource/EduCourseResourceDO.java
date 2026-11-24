package cn.nus.zknu.module.edu.dal.dataobject.courseresource;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 课程资源 DO
 */
@TableName("edu_course_resource")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduCourseResourceDO extends BaseDO {

    /**
     * 资源 ID
     */
    private Long id;
    /**
     * 资源名称
     */
    private String resourceName;
    /**
     * 所属课程 ID
     */
    private Long courseId;
    /**
     * 文件URL
     */
    private String fileUrl;
    /**
     * MIME类型
     */
    private String fileType;
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    /**
     * 标准音频URL（音频文件自动生成）
     */
    private String standardAudioUrl;
    /**
     * 审核状态：0 待审核，1 通过，2 拒绝（存量数据默认 1）
     */
    private Integer reviewStatus;
    /**
     * 审核原因（驳回时填写）
     */
    private String reviewReason;
    /**
     * 审核人（用户 ID）
     */
    private Long reviewerId;
    /**
     * 审核时间
     */
    private LocalDateTime reviewedAt;

}
