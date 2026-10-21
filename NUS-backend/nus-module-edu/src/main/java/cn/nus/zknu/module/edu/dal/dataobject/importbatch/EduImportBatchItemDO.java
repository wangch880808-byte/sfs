package cn.nus.zknu.module.edu.dal.dataobject.importbatch;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("edu_import_batch_item")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduImportBatchItemDO extends BaseDO {

    private Long id;
    private Long batchId;
    private String sheetName;
    @TableField("`row_number`")
    private Integer rowNumber;
    private String businessKey;
    private String normalizedData;
    private String action;
    private String validationStatus;
    private String validationErrors;
    private String beforeData;
    private String afterData;

}
