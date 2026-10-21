package cn.nus.zknu.module.infra.controller.admin.monitor.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Schema(description = "管理后台 - 监控访问票据 Response VO")
@Data
@AllArgsConstructor
public class MonitorAccessTicketRespVO {

    @Schema(description = "一次性票据消费地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private String url;

}
