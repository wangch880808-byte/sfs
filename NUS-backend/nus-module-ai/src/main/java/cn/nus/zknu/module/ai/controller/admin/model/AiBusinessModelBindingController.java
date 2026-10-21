package cn.nus.zknu.module.ai.controller.admin.model;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.module.ai.controller.admin.model.vo.binding.AiBusinessModelBindingRespVO;
import cn.nus.zknu.module.ai.controller.admin.model.vo.binding.AiBusinessModelBindingBatchSaveReqVO;
import cn.nus.zknu.module.ai.controller.admin.model.vo.binding.AiBusinessModelBindingSaveReqVO;
import cn.nus.zknu.module.ai.service.model.AiBusinessModelBindingService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/ai/business-model-binding")
public class AiBusinessModelBindingController {

    @Resource
    private AiBusinessModelBindingService bindingService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('ai:model:query')")
    public CommonResult<List<AiBusinessModelBindingRespVO>> getBindingList() {
        return success(bindingService.getBindingList());
    }

    @PutMapping("/save")
    @PreAuthorize("@ss.hasPermission('ai:model:update')")
    public CommonResult<Boolean> saveBinding(@Valid @RequestBody AiBusinessModelBindingSaveReqVO request) {
        bindingService.saveBinding(request);
        return success(true);
    }

    @PutMapping("/save-batch")
    @PreAuthorize("@ss.hasPermission('ai:model:update')")
    public CommonResult<Boolean> saveBindings(@Valid @RequestBody AiBusinessModelBindingBatchSaveReqVO request) {
        bindingService.saveBindings(request.getBindings());
        return success(true);
    }

}
