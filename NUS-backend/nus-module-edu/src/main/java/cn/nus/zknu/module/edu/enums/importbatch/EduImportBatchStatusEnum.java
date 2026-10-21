package cn.nus.zknu.module.edu.enums.importbatch;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EduImportBatchStatusEnum {

    PREVIEW_READY("PREVIEW_READY"),
    PREVIEW_INVALID("PREVIEW_INVALID"),
    CONFIRMING("CONFIRMING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED");

    private final String status;

}
