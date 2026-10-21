package cn.nus.zknu.module.edu.controller.admin.dataexchange;

import cn.nus.zknu.framework.apilog.core.annotation.ApiAccessLog;
import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.pojo.PageParam;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.edu.controller.admin.importbatch.vo.EduImportBatchItemRespVO;
import cn.nus.zknu.module.edu.controller.admin.importbatch.vo.EduImportBatchRespVO;
import cn.nus.zknu.module.edu.controller.admin.importbatch.vo.EduImportConfirmReqVO;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataExchangeTemplate;
import cn.nus.zknu.module.edu.service.dataexchange.EduDataExchangeTemplateService;
import cn.nus.zknu.module.edu.service.importbatch.EduImportBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static cn.nus.zknu.framework.apilog.core.enums.OperateTypeEnum.IMPORT;
import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 数据交换")
@RestController
@RequestMapping("/edu/data-exchange")
public class EduDataExchangeController {

    @Resource
    private EduDataExchangeTemplateService templateService;
    @Resource
    private EduImportBatchService importBatchService;

    @GetMapping("/templates/{bizType}")
    @Operation(summary = "下载业务导入模板")
    @PreAuthorize("@ss.hasAnyPermissions('edu:data-exchange:import', 'system:user:import', 'edu:question-bank:manage')")
    public void downloadTemplate(@PathVariable("bizType") String bizType, HttpServletResponse response)
            throws IOException {
        EduDataExchangeTemplate template = templateService.getTemplate(bizType);
        writeXlsx(response, template.getFileName(), template.generate());
    }

    @PostMapping("/imports/{bizType}/preview")
    @Operation(summary = "上传并预检业务数据")
    @PreAuthorize("@ss.hasAnyPermissions('edu:data-exchange:import', 'system:user:import', 'edu:question-bank:manage')")
    @ApiAccessLog(operateType = IMPORT, requestEnable = false)
    public CommonResult<EduImportBatchRespVO> preview(@PathVariable("bizType") String bizType,
                                                       @RequestParam(value = "optionsJson", required = false)
                                                       String optionsJson,
                                                       @RequestParam("file") MultipartFile file) {
        EduDataExchangeTemplate template = templateService.getTemplate(bizType);
        return success(importBatchService.preview(template.getBizType(), template.getTemplateVersion(),
                optionsJson, file));
    }

    @GetMapping("/imports/{batchNo}")
    @Operation(summary = "获取导入批次")
    @PreAuthorize("@ss.hasAnyPermissions('edu:data-exchange:import', 'system:user:import', 'edu:question-bank:manage')")
    public CommonResult<EduImportBatchRespVO> get(@PathVariable("batchNo") String batchNo) {
        return success(importBatchService.get(batchNo));
    }

    @GetMapping("/imports/{batchNo}/items")
    @Operation(summary = "获取导入预检明细")
    @PreAuthorize("@ss.hasAnyPermissions('edu:data-exchange:import', 'system:user:import', 'edu:question-bank:manage')")
    public CommonResult<PageResult<EduImportBatchItemRespVO>> getItems(
            @PathVariable("batchNo") String batchNo, @org.springframework.validation.annotation.Validated PageParam pageParam) {
        return success(importBatchService.getItemPage(batchNo, pageParam));
    }

    @PostMapping("/imports/{batchNo}/confirm")
    @Operation(summary = "异步确认执行导入批次",
            description = "校验通过后返回 CONFIRMING，调用方需轮询查询批次接口，直到状态变为 SUCCESS 或 FAILED")
    @PreAuthorize("@ss.hasAnyPermissions('edu:data-exchange:import', 'system:user:import', 'edu:question-bank:manage')")
    @ApiAccessLog(operateType = IMPORT, requestEnable = false)
    public CommonResult<EduImportBatchRespVO> confirm(@PathVariable("batchNo") String batchNo,
                                                       @RequestBody(required = false) @jakarta.validation.Valid
                                                       EduImportConfirmReqVO reqVO) {
        return success(importBatchService.confirm(batchNo, reqVO == null ? null : reqVO.getInitialPassword()));
    }

    @GetMapping("/imports/{batchNo}/errors")
    @Operation(summary = "获取错误回执临时地址")
    @PreAuthorize("@ss.hasAnyPermissions('edu:data-exchange:import', 'system:user:import', 'edu:question-bank:manage')")
    public CommonResult<String> getErrors(@PathVariable("batchNo") String batchNo) {
        return success(importBatchService.getErrorFileDownloadUrl(batchNo));
    }

    @PostMapping("/imports/{batchNo}/cancel")
    @Operation(summary = "取消待确认导入批次")
    @PreAuthorize("@ss.hasAnyPermissions('edu:data-exchange:import', 'system:user:import', 'edu:question-bank:manage')")
    public CommonResult<EduImportBatchRespVO> cancel(@PathVariable("batchNo") String batchNo) {
        return success(importBatchService.cancel(batchNo));
    }

    public static void writeXlsx(HttpServletResponse response, String fileName, byte[] content) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename="
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
    }

}
