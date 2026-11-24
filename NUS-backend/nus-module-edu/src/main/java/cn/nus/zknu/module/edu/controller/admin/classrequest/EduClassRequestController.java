package cn.nus.zknu.module.edu.controller.admin.classrequest;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.idempotent.core.annotation.Idempotent;
import cn.nus.zknu.framework.idempotent.core.keyresolver.impl.RequestHeaderIdempotentKeyResolver;
import cn.nus.zknu.module.edu.controller.admin.classrequest.vo.EduClassRequestPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.classrequest.vo.EduClassRequestRejectReqVO;
import cn.nus.zknu.module.edu.controller.admin.classrequest.vo.EduClassRequestRespVO;
import cn.nus.zknu.module.edu.controller.admin.classrequest.vo.EduClassRequestSaveReqVO;
import cn.nus.zknu.module.edu.service.classrequest.EduClassRequestService;
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

@Tag(name = "管理后台 - 建班申请审核")
@RestController
@RequestMapping("/edu/class-request")
@Validated
public class EduClassRequestController {

    @Resource
    private EduClassRequestService eduClassRequestService;

    /**
     * 建班申请分页查询（status / courseId / applicantId 筛选，按创建时间降序）。
     */
    @GetMapping("/page")
    @Operation(summary = "建班申请分页查询")
    @PreAuthorize("@ss.hasPermission('edu:class-request:list')")
    public CommonResult<PageResult<EduClassRequestRespVO>> page(@Validated EduClassRequestPageReqVO reqVO) {
        return success(eduClassRequestService.getClassRequestPage(reqVO));
    }

    /**
     * 建班申请详情。
     */
    @GetMapping("/get")
    @Operation(summary = "建班申请详情")
    @Parameter(name = "id", description = "申请 ID", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('edu:class-request:list')")
    public CommonResult<EduClassRequestRespVO> get(@RequestParam("id") Long id) {
        return success(eduClassRequestService.getClassRequest(id));
    }

    /**
     * 教师提交建班申请。
     */
    @PostMapping("/submit")
    @Operation(summary = "提交建班申请")
    @PreAuthorize("@ss.hasPermission('edu:class-request:list')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "建班申请正在提交中，请勿重复操作")
    public CommonResult<Long> submit(@Valid @RequestBody EduClassRequestSaveReqVO reqVO) {
        return success(eduClassRequestService.submitClassRequest(reqVO));
    }

    /**
     * 审核通过：创建班课关联。
     */
    @PostMapping("/{id}/approve")
    @Operation(summary = "审核通过建班申请")
    @Parameter(name = "id", description = "申请 ID", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('edu:class-request:approve')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "建班申请正在审核中，请勿重复操作")
    public CommonResult<Boolean> approve(@PathVariable("id") Long id) {
        eduClassRequestService.approveClassRequest(id);
        return success(true);
    }

    /**
     * 审核拒绝：需填写拒绝原因。
     */
    @PostMapping("/{id}/reject")
    @Operation(summary = "审核拒绝建班申请")
    @Parameter(name = "id", description = "申请 ID", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('edu:class-request:reject')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "建班申请正在审核中，请勿重复操作")
    public CommonResult<Boolean> reject(@PathVariable("id") Long id,
            @Valid @RequestBody EduClassRequestRejectReqVO reqVO) {
        eduClassRequestService.rejectClassRequest(id, reqVO.getReason());
        return success(true);
    }

}
