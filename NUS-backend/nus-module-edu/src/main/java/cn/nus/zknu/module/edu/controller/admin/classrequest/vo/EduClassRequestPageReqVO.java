package cn.nus.zknu.module.edu.controller.admin.classrequest.vo;

import cn.nus.zknu.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 建班申请分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class EduClassRequestPageReqVO extends PageParam {

    @Schema(description = "审核状态：0 待审核，1 通过，2 拒绝", example = "0")
    private Integer status;

    @Schema(description = "课程 ID", example = "1")
    private Long courseId;

    @Schema(description = "申请人 ID", example = "1024")
    private Long applicantId;

}
