package cn.nus.zknu.module.edu.dal.dataobject.questionbank;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题库 DO
 */
@TableName("edu_question_bank")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduQuestionBankDO extends BaseDO {

    /**
     * 题目 ID
     */
    private Long id;
    /**
     * 题目标题
     */
    private String title;
    /**
     * 题目要求
     */
    private String requirement;
    /**
     * 一级技能指标 ID
     */
    private Long indicatorFirstId;
    /**
     * 二级技能指标 ID
     */
    private Long indicatorSecondId;
    /**
     * 任务等级：0=专业, 1=初级, 2=中级, 3=高级
     */
    private Integer homeworkLevel;
    /**
     * 创建教师 ID（数据隔离键）
     */
    private Long teacherId;

}
