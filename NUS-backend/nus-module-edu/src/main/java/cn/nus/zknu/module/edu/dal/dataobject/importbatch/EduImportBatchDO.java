package cn.nus.zknu.module.edu.dal.dataobject.importbatch;

import cn.nus.zknu.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("edu_import_batch")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduImportBatchDO extends BaseDO {

    private Long id;
    private String batchNo;
    private String bizType;
    private String templateVersion;
    private String sourceFileName;
    private String sourceFileUrl;
    private String sourceFileSha256;
    private String status;
    private Integer totalCount;
    private Integer validCount;
    private Integer invalidCount;
    private Integer insertedCount;
    private Integer updatedCount;
    private Integer skippedCount;
    private String optionsJson;
    private String errorFileUrl;
    private Long operatorId;
    private LocalDateTime expireTime;
    private LocalDateTime confirmTime;
    private String failureReason;

}
