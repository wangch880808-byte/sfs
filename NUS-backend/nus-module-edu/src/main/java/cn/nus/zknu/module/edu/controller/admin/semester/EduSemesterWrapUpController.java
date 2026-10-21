package cn.nus.zknu.module.edu.controller.admin.semester;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.idempotent.core.annotation.Idempotent;
import cn.nus.zknu.framework.idempotent.core.keyresolver.impl.RequestHeaderIdempotentKeyResolver;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterWrapUpArchiveReqVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterWrapUpArchiveRespVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterWrapUpPreviewRespVO;
import cn.nus.zknu.module.edu.service.semester.EduSemesterWrapUpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 学期收尾")
@RestController
@RequestMapping("/edu/semester/wrap-up")
@Validated
public class EduSemesterWrapUpController {

    @Resource
    private EduSemesterWrapUpService wrapUpService;

    @GetMapping("/preview")
    @Operation(summary = "预览学期收尾：在用班课、进行中的任务、待批改提交、公共课关联")
    @Parameter(name = "semesterName", description = "学期名称，为空时取当前学期", example = "2025-2026 下学期")
    @PreAuthorize("@ss.hasPermission('edu:semester:wrap-up')")
    public CommonResult<EduSemesterWrapUpPreviewRespVO> preview(
            @RequestParam(value = "semesterName", required = false) String semesterName) {
        return success(wrapUpService.preview(semesterName));
    }

    @PostMapping("/archive")
    @Operation(summary = "学期收尾：批量封存选中的班级课程和班级")
    @PreAuthorize("@ss.hasPermission('edu:semester:wrap-up')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "学期收尾正在处理中，请勿重复操作")
    public CommonResult<EduSemesterWrapUpArchiveRespVO> archive(
            @Valid @RequestBody EduSemesterWrapUpArchiveReqVO reqVO) {
        return success(wrapUpService.archive(reqVO));
    }
}
