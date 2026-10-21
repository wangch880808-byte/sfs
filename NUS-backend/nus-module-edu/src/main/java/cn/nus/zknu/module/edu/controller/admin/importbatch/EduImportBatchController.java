package cn.nus.zknu.module.edu.controller.admin.importbatch;

import cn.nus.zknu.framework.apilog.core.annotation.ApiAccessLog;
import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.pojo.PageParam;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.importbatch.vo.EduImportBatchItemRespVO;
import cn.nus.zknu.module.edu.controller.admin.importbatch.vo.EduImportBatchPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.importbatch.vo.EduImportBatchRespVO;
import cn.nus.zknu.module.edu.controller.admin.importbatch.vo.EduImportConfirmReqVO;
import cn.nus.zknu.module.edu.service.importbatch.EduImportBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static cn.nus.zknu.framework.apilog.core.enums.OperateTypeEnum.IMPORT;
import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 数据导入批次")
@RestController
@RequestMapping("/edu/import-batch")
@Validated
public class EduImportBatchController {

    @Resource
    private EduImportBatchService importBatchService;

    @PostMapping("/preview")
    @Operation(summary = "上传并预检导入文件")
    @PreAuthorize("@ss.hasAnyPermissions('edu:data-exchange:import', 'system:user:import', 'edu:question-bank:manage')")
    @ApiAccessLog(operateType = IMPORT, requestEnable = false)
    public CommonResult<EduImportBatchRespVO> preview(@RequestParam("bizType") String bizType,
                                                       @RequestParam("templateVersion") String templateVersion,
                                                       @RequestParam(value = "optionsJson", required = false) String optionsJson,
                                                       @RequestParam("file") MultipartFile file) {
        return success(importBatchService.preview(bizType, templateVersion, optionsJson, file));
    }

    @PostMapping("/{batchNo}/confirm")
    @Operation(summary = "异步确认执行导入批次",
            description = "校验通过后返回 CONFIRMING，调用方需轮询查询批次接口，直到状态变为 SUCCESS 或 FAILED")
    @PreAuthorize("@ss.hasAnyPermissions('edu:data-exchange:import', 'system:user:import', 'edu:question-bank:manage')")
    @ApiAccessLog(operateType = IMPORT, requestEnable = false)
    public CommonResult<EduImportBatchRespVO> confirm(@PathVariable("batchNo") String batchNo,
                                                       @RequestBody(required = false) @jakarta.validation.Valid
                                                       EduImportConfirmReqVO reqVO) {
        return success(importBatchService.confirm(batchNo, reqVO == null ? null : reqVO.getInitialPassword()));
    }

    @GetMapping("/{batchNo}")
    @Operation(summary = "获取导入批次")
    @PreAuthorize("@ss.hasAnyPermissions('edu:data-exchange:import', 'system:user:import', 'edu:question-bank:manage')")
    public CommonResult<EduImportBatchRespVO> get(@PathVariable("batchNo") String batchNo) {
        return success(importBatchService.get(batchNo));
    }

    @GetMapping("/page")
    @Operation(summary = "获取本人导入批次分页")
    @PreAuthorize("@ss.hasAnyPermissions('edu:data-exchange:import', 'system:user:import', 'edu:question-bank:manage')")
    public CommonResult<PageResult<EduImportBatchRespVO>> getPage(@Validated EduImportBatchPageReqVO reqVO) {
        return success(importBatchService.getPage(reqVO));
    }

    @GetMapping("/{batchNo}/items")
    @Operation(summary = "获取导入预检明细")
    @PreAuthorize("@ss.hasAnyPermissions('edu:data-exchange:import', 'system:user:import', 'edu:question-bank:manage')")
    public CommonResult<PageResult<EduImportBatchItemRespVO>> getItems(
            @PathVariable("batchNo") String batchNo, @Validated PageParam pageParam) {
        return success(importBatchService.getItemPage(batchNo, pageParam));
    }

    @GetMapping("/{batchNo}/error-file-url")
    @Operation(summary = "获取错误回执临时下载地址")
    @PreAuthorize("@ss.hasAnyPermissions('edu:data-exchange:import', 'system:user:import', 'edu:question-bank:manage')")
    public CommonResult<String> getErrorFileUrl(@PathVariable("batchNo") String batchNo) {
        return success(importBatchService.getErrorFileDownloadUrl(batchNo));
    }

}
