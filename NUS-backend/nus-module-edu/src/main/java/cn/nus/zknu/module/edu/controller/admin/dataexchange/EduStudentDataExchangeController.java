package cn.nus.zknu.module.edu.controller.admin.dataexchange;

import cn.nus.zknu.framework.apilog.core.annotation.ApiAccessLog;
import cn.nus.zknu.module.edu.service.dataexchange.student.ClassRosterDataExchangeHandler;
import cn.nus.zknu.module.edu.service.dataexchange.student.StudentMasterDataExchangeHandler;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserPageReqVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static cn.nus.zknu.framework.apilog.core.enums.OperateTypeEnum.EXPORT;

@Tag(name = "管理后台 - 学生数据交换")
@RestController
@RequestMapping("/edu")
public class EduStudentDataExchangeController {

    @Resource
    private StudentMasterDataExchangeHandler studentHandler;
    @Resource
    private ClassRosterDataExchangeHandler rosterHandler;

    @GetMapping("/student-data/export")
    @Operation(summary = "按完整筛选条件导出学生档案与班级关系")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportStudents(@Validated UserPageReqVO reqVO, HttpServletResponse response) throws IOException {
        EduDataExchangeController.writeXlsx(response, "学生主数据.xlsx", studentHandler.export(reqVO));
    }

    @GetMapping("/class/student/export")
    @Operation(summary = "导出班级花名册")
    @PreAuthorize("@ss.hasPermission('edu:class:query')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportRoster(@RequestParam("classId") Long classId, HttpServletResponse response) throws IOException {
        EduDataExchangeController.writeXlsx(response, "班级花名册.xlsx", rosterHandler.export(classId));
    }

}
