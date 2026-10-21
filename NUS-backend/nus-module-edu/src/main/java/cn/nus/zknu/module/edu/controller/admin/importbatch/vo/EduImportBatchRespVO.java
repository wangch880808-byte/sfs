package cn.nus.zknu.module.edu.controller.admin.importbatch.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "管理后台 - 导入批次 Response VO")
@Data
public class EduImportBatchRespVO {

    private String batchNo;
    private String bizType;
    private String templateVersion;
    private String sourceFileName;
    private String status;
    private Integer totalCount;
    private Integer validCount;
    private Integer invalidCount;
    private Integer insertedCount;
    private Integer updatedCount;
    private Integer skippedCount;
    private LocalDateTime expireTime;
    private LocalDateTime confirmTime;
    private String failureReason;
    private Boolean hasErrorFile;
    private String initialPasswordMode;
    private String defaultInitialPassword;
    private LocalDateTime createTime;
    @Schema(description = "跳过行按原因计数（如 UNCHANGED、EMPTY），只在预检、确认和查询单个批次时返回；业务没有细分原因时为空")
    private Map<String, Integer> skipReasonCounts;

}
