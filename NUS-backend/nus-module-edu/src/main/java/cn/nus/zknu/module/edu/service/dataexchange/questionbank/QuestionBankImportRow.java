package cn.nus.zknu.module.edu.service.dataexchange.questionbank;

import lombok.Data;

@Data
public class QuestionBankImportRow {

    private Long indicatorFirstId;
    private Long indicatorSecondId;
    private String indicatorFirstName;
    private String indicatorSecondName;
    private Integer homeworkLevel;
    private String levelName;
    private String title;
    private String requirement;

}
