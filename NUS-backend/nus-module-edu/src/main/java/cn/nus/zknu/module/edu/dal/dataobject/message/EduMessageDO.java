package cn.nus.zknu.module.edu.dal.dataobject.message;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 留言互动 DO
 */
@TableName("edu_message")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduMessageDO extends BaseDO {

    private Long id;
    /**
     * 班级ID
     */
    private Long classId;
    /**
     * 课程ID
     */
    private Long courseId;
    /**
     * 学生ID
     */
    private Long studentId;
    /**
     * 留言内容
     */
    private String content;
    /**
     * 0=学生留言,1=教师回复
     */
    private Integer type;
    /**
     * 0=开启,1=关闭
     */
    private Integer status;

    /** 0 未读，1 已读；接收方由 type 决定。 */
    private Integer readStatus;
    private java.time.LocalDateTime readTime;

}
