package cn.nus.zknu.module.ai.controller.admin.prompt;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.module.ai.controller.admin.prompt.vo.AiPromptTemplateCreateReqVO;
import cn.nus.zknu.module.ai.controller.admin.prompt.vo.AiPromptTemplateRespVO;
import cn.nus.zknu.module.ai.controller.admin.prompt.vo.AiPromptTemplateSummaryRespVO;
import cn.nus.zknu.module.ai.service.prompt.AiPromptTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - AI 提示词")
@RestController
@RequestMapping("/ai/prompt-template")
@Validated
public class AiPromptTemplateController {

    @Resource
    private AiPromptTemplateService promptTemplateService;

    @GetMapping("/list")
    @Operation(summary = "提示词概览：每个业务的生效内容与内置默认内容")
    @PreAuthorize("@ss.hasPermission('ai:prompt-template:query')")
    public CommonResult<List<AiPromptTemplateSummaryRespVO>> getSummaryList() {
        return success(promptTemplateService.getSummaryList());
    }

    @GetMapping("/history")
    @Operation(summary = "某业务的提示词历史版本")
    @Parameter(name = "businessCode", description = "提示词业务码", required = true)
    @PreAuthorize("@ss.hasPermission('ai:prompt-template:query')")
    public CommonResult<List<AiPromptTemplateRespVO>> getHistory(@RequestParam("businessCode") String businessCode) {
        return success(promptTemplateService.getHistory(businessCode));
    }

    @PostMapping("/create")
    @Operation(summary = "保存新版本")
    @PreAuthorize("@ss.hasPermission('ai:prompt-template:create')")
    public CommonResult<Long> createVersion(@Valid @RequestBody AiPromptTemplateCreateReqVO reqVO) {
        return success(promptTemplateService.createVersion(reqVO));
    }

    @PutMapping("/activate")
    @Operation(summary = "启用某个版本")
    @Parameter(name = "id", description = "版本编号", required = true)
    @PreAuthorize("@ss.hasPermission('ai:prompt-template:activate')")
    public CommonResult<Boolean> activateVersion(@RequestParam("id") Long id) {
        promptTemplateService.activateVersion(id);
        return success(true);
    }

}
