package cn.nus.zknu.module.edu.service.importbatch;

import cn.nus.zknu.module.edu.enums.importbatch.EduImportRowActionEnum;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EduImportPreviewRow {

    private String sheetName;
    private Integer rowNumber;
    private String businessKey;
    private String normalizedData;
    private EduImportRowActionEnum action;
    private List<String> errors;
    private String beforeData;
    private String afterData;

}
