package cn.nus.zknu.module.edu.controller.admin.importbatch.vo;

import cn.nus.zknu.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 导入批次分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduImportBatchPageReqVO extends PageParam {

    @Schema(description = "业务类型", example = "ORGANIZATION")
    private String bizType;

    @Schema(description = "批次状态", example = "PREVIEW_READY")
    private String status;

}
