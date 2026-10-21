package cn.nus.zknu.module.edu.controller.admin.dataexchange;

import cn.nus.zknu.framework.apilog.core.annotation.ApiAccessLog;
import cn.nus.zknu.module.edu.controller.admin.dataexchange.vo.EduMasterDataExportReqVO;
import cn.nus.zknu.module.edu.service.dataexchange.organization.OrganizationDataExchangeHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static cn.nus.zknu.framework.apilog.core.enums.OperateTypeEnum.EXPORT;

@Tag(name = "管理后台 - 组织班级数据交换")
@RestController
@RequestMapping("/edu/master-data")
public class EduMasterDataExchangeController {

    @Resource
    private OrganizationDataExchangeHandler handler;

    @GetMapping("/organization/export")
    @Operation(summary = "按当前筛选条件导出组织和班级")
    @PreAuthorize("@ss.hasPermission('system:dept:query') and @ss.hasPermission('edu:class:query')")
    @ApiAccessLog(operateType = EXPORT)
    public void export(@Validated EduMasterDataExportReqVO reqVO, HttpServletResponse response) throws IOException {
        EduDataExchangeController.writeXlsx(response, "组织班级数据.xlsx", handler.export(reqVO));
    }

}
