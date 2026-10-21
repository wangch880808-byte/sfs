package cn.nus.zknu.module.ai.controller.admin.log;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.ai.controller.admin.log.vo.AiCallLogDailySummaryRespVO;
import cn.nus.zknu.module.ai.controller.admin.log.vo.AiCallLogPageReqVO;
import cn.nus.zknu.module.ai.controller.admin.log.vo.AiCallLogRespVO;
import cn.nus.zknu.module.ai.controller.admin.log.vo.AiCallLogSummaryReqVO;
import cn.nus.zknu.module.ai.service.log.AiCallLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - AI 调用日志")
@RestController
@RequestMapping("/ai/call-log")
@Validated
public class AiCallLogController {

    @Resource
    private AiCallLogService callLogService;

    @GetMapping("/page")
    @Operation(summary = "调用日志分页")
    @PreAuthorize("@ss.hasPermission('ai:call-log:query')")
    public CommonResult<PageResult<AiCallLogRespVO>> getPage(@Valid AiCallLogPageReqVO reqVO) {
        return success(callLogService.getPage(reqVO));
    }

    @GetMapping("/daily-summary")
    @Operation(summary = "每日调用次数与令牌用量，按业务分组")
    @PreAuthorize("@ss.hasPermission('ai:call-log:query')")
    public CommonResult<List<AiCallLogDailySummaryRespVO>> getDailySummary(@Valid AiCallLogSummaryReqVO reqVO) {
        return success(callLogService.getDailySummary(reqVO));
    }

}
