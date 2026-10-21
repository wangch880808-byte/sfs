package cn.nus.zknu.module.edu.controller.admin.importbatch.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Schema(description = "管理后台 - 确认导入 Request VO")
@Data
public class EduImportConfirmReqVO {

    @Schema(description = "新增账号初始密码；不传时使用业务默认值", example = "123456")
    @Length(min = 4, max = 16, message = "初始密码长度为 4-16 位")
    private String initialPassword;

}
