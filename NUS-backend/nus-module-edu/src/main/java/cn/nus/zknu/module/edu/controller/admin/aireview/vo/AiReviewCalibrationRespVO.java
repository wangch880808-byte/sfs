package cn.nus.zknu.module.edu.controller.admin.aireview.vo;

import lombok.Data;

@Data
public class AiReviewCalibrationRespVO {
    /** TASK、COURSE 或 NONE。 */
    private String scope;
    private int sampleCount;
    private int strictCount;
    private int normalCount;
    private int looseCount;
    /** 教师终评分减 AI 草案分，单位为分。 */
    private double averageDeviation;
    private int fullScore;
    /** STRICTER、LOOSER、NONE 或 INSUFFICIENT。 */
    private String direction;
    private String hint;
}
