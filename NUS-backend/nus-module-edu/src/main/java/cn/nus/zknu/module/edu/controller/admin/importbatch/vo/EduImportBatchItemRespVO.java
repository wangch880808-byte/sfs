package cn.nus.zknu.module.edu.controller.admin.importbatch.vo;

import lombok.Data;

@Data
public class EduImportBatchItemRespVO {

    private String sheetName;
    private Integer rowNumber;
    private String businessKey;
    private String normalizedData;
    private String action;
    private String validationStatus;
    private String validationErrors;
    private String beforeData;
    private String afterData;

}
