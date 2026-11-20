package cn.nus.zknu.module.edu.dal.dataobject.student;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 等级闯关计算用的任务行：一个分配给学生的任务及其本人提交情况（查询结果，不对应表）。
 */
@Data
public class StudentLevelTaskRow {

    private Long homeworkId;
    private Integer homeworkLevel;
    /** 一级指标（技能）编号，SQL 里 COALESCE 后为 0 表示未填 */
    private Long indicatorFirstId;
    /** 一级指标（技能）名称，用于解锁原因文案 */
    private String indicatorName;
    private LocalDateTime deadline;
    /** 任务达标分数线；为空时按配置的默认比例换算 */
    private Integer passScore;
    /** 任务满分 */
    private Integer fullScore;
    /** 学生的个别延期截止时间（未删除），没有延期时为空 */
    private LocalDateTime personalDeadline;
    private Long recordId;
    /** 提交状态，未提交为空 */
    private Integer recordStatus;
    /** 提交记录的重做截止时间 */
    private LocalDateTime redoDeadline;
    /** 提交记录上的分数 */
    private Integer recordScore;
    /** 评分轨迹中最近一次批改（CORRECT）的分数，不含已撤销的批改 */
    private Integer lastCorrectScore;

}
