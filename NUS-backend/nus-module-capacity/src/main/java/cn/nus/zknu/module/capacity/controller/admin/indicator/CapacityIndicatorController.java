package cn.nus.zknu.module.capacity.controller.admin.indicator;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.module.capacity.controller.admin.indicator.vo.CapacityIndicatorBatchCreateReqVO;
import cn.nus.zknu.module.capacity.controller.admin.indicator.vo.CapacityIndicatorResetWeightReqVO;
import cn.nus.zknu.module.capacity.controller.admin.indicator.vo.CapacityIndicatorRespVO;
import cn.nus.zknu.module.capacity.controller.admin.indicator.vo.CapacityIndicatorSaveReqVO;
import cn.nus.zknu.module.capacity.dal.dataobject.indicator.CapacityIndicatorDO;
import cn.nus.zknu.module.capacity.service.indicator.CapacityIndicatorService;
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

@Tag(name = "管理后台 - 培养指标")
@RestController
@RequestMapping("/capacity/indicator")
@Validated
public class CapacityIndicatorController {

    @Resource
    private CapacityIndicatorService capacityIndicatorService;

    /**
     * 创建单条培养指标。
     */
    @PostMapping("/create")
    @Operation(summary = "创建培养指标")
    @PreAuthorize("@ss.hasPermission('capacity:indicator:create')")
    public CommonResult<Long> createIndicator(@Valid @RequestBody CapacityIndicatorSaveReqVO createReqVO) {
        return success(capacityIndicatorService.createIndicator(createReqVO));
    }

    /**
     * 批量创建培养指标。
     */
    @PostMapping("/create-batch")
    @Operation(summary = "批量创建培养指标")
    @PreAuthorize("@ss.hasPermission('capacity:indicator:create')")
    public CommonResult<Boolean> createIndicatorBatch(@Valid @RequestBody CapacityIndicatorBatchCreateReqVO batchCreateReqVO) {
        // 批量新增用于前端一次提交多条指标，避免逐条提交触发中间态权重冲突
        capacityIndicatorService.batchCreateIndicators(batchCreateReqVO);
        return success(true);
    }

    /**
     * 更新单条培养指标。
     */
    @PutMapping("/update")
    @Operation(summary = "更新培养指标")
    @PreAuthorize("@ss.hasPermission('capacity:indicator:update')")
    public CommonResult<Boolean> updateIndicator(@Valid @RequestBody CapacityIndicatorSaveReqVO updateReqVO) {
        capacityIndicatorService.updateIndicator(updateReqVO);
        return success(true);
    }

    /**
     * 批量重设培养指标权重。
     */
    @PutMapping("/reset-weight")
    @Operation(summary = "批量重设培养指标权重")
    @PreAuthorize("@ss.hasPermission('capacity:indicator:update')")
    public CommonResult<Boolean> resetIndicatorWeight(@Valid @RequestBody CapacityIndicatorResetWeightReqVO resetWeightReqVO) {
        // 按作用域（一级总表/某一级下二级表）整体重设权重
        capacityIndicatorService.resetIndicatorWeights(resetWeightReqVO);
        return success(true);
    }

    /**
     * 删除培养指标。
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除培养指标")
    @Parameter(name = "id", description = "指标编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('capacity:indicator:delete')")
    public CommonResult<Boolean> deleteIndicator(@RequestParam("id") Long id) {
        capacityIndicatorService.deleteIndicator(id);
        return success(true);
    }

    /**
     * 获取培养指标详情。
     */
    @GetMapping("/get")
    @Operation(summary = "获取培养指标详情")
    @Parameter(name = "id", description = "指标编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('capacity:indicator:query')")
    public CommonResult<CapacityIndicatorRespVO> getIndicator(@RequestParam("id") Long id) {
        CapacityIndicatorDO indicator = capacityIndicatorService.getIndicator(id);
        return success(BeanUtils.toBean(indicator, CapacityIndicatorRespVO.class));
    }

    /**
     * 获取培养指标树列表。
     */
    @GetMapping("/list")
    @Operation(summary = "获取培养指标列表")
    @PreAuthorize("@ss.hasPermission('capacity:indicator:query')")
    public CommonResult<List<CapacityIndicatorRespVO>> getIndicatorList() {
        return success(BeanUtils.toBean(capacityIndicatorService.getIndicatorList(), CapacityIndicatorRespVO.class));
    }

}
