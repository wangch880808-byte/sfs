package cn.nus.zknu.module.edu.service.dataexchange.gradebook;

import lombok.Data;

@Data
public class GradebookImportRow {

    /** 跳过原因：成绩和评语都与系统里一致。 */
    public static final String SKIP_UNCHANGED = "UNCHANGED";
    /** 跳过原因：待批改（已提交、已重交）但没填成绩。 */
    public static final String SKIP_EMPTY = "EMPTY";
    /** 跳过原因：学生还没有提交。 */
    public static final String SKIP_NOT_SUBMITTED = "NOT_SUBMITTED";
    /** 跳过原因：旧版成绩册里重新提交记录的「成绩*」是打回前成绩，原样导回不定稿。 */
    public static final String SKIP_PRE_REDO_SCORE = "PRE_REDO_SCORE";
    /** 跳过原因：已打回，等学生重新提交。 */
    public static final String SKIP_AWAITING_RESUBMIT = "AWAITING_RESUBMIT";

    private String username;
    private String nickname;
    private Integer score;
    private String comment;
    /** 仅 SKIP 行有值，预检页据此分类统计。 */
    private String skipReason;
    private Long recordId;
    private Long homeworkId;
    private Long studentId;
    private String rowToken;
    private String templateChecksum;

}
