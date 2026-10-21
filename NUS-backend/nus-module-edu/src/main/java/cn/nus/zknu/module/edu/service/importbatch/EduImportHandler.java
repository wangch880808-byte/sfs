package cn.nus.zknu.module.edu.service.importbatch;

import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchDO;
import cn.nus.zknu.module.edu.dal.dataobject.importbatch.EduImportBatchItemDO;

import java.util.List;

/**
 * A business import handler must produce deterministic preview rows and commit
 * only the normalized row snapshots persisted by the generic batch service.
 */
public interface EduImportHandler {

    String INITIAL_PASSWORD_MODE_FIXED = "FIXED";
    String INITIAL_PASSWORD_MODE_USERNAME = "USERNAME";

    String getBizType();

    String getTemplateVersion();

    void validatePermission();

    List<EduImportPreviewRow> preview(byte[] fileContent, String optionsJson);

    EduImportCommitResult commit(EduImportBatchDO batch, List<EduImportBatchItemDO> validItems,
                                 String initialPassword);

    default String getInitialPasswordMode() {
        return null;
    }

    default String getDefaultInitialPassword() {
        return null;
    }

}
