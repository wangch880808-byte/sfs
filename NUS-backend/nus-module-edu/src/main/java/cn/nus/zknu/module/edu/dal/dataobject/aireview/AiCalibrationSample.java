package cn.nus.zknu.module.edu.dal.dataobject.aireview;

import lombok.Data;

/** 教师反馈或教师终评分与 AI 草案的偏差样本。 */
@Data
public class AiCalibrationSample {
    private String feedbackType;
    private Integer deviation;
    private Integer fullScore;
}
