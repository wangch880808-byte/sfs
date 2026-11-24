package cn.nus.zknu.module.edu.controller.admin.term;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.framework.idempotent.core.annotation.Idempotent;
import cn.nus.zknu.framework.idempotent.core.keyresolver.impl.RequestHeaderIdempotentKeyResolver;
import cn.nus.zknu.module.edu.controller.admin.term.vo.EduTermPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.term.vo.EduTermRespVO;
import cn.nus.zknu.module.edu.controller.admin.term.vo.EduTermSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.term.EduTermDO;
import cn.nus.zknu.module.edu.service.term.EduTermService;
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

@Tag(name = "管理后台 - 学期管理")
@RestController
@RequestMapping("/edu/term")
@Validated
public class EduTermController {

    @Resource
    private EduTermService eduTermService;

    /**
     * 创建学期。
     */
    @PostMapping("/create")
    @Operation(summary = "创建学期")
    @PreAuthorize("@ss.hasPermission('edu:term:create')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "学期正在提交中，请勿重复操作")
    public CommonResult<Long> createTerm(@Valid @RequestBody EduTermSaveReqVO createReqVO) {
        return success(eduTermService.createTerm(createReqVO));
    }

    /**
     * 更新学期。
     */
    @PutMapping("/update")
    @Operation(summary = "更新学期")
    @PreAuthorize("@ss.hasPermission('edu:term:update')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "学期正在处理中，请勿重复操作")
    public CommonResult<Boolean> updateTerm(@Valid @RequestBody EduTermSaveReqVO updateReqVO) {
        eduTermService.updateTerm(updateReqVO);
        return success(true);
    }

    /**
     * 归档学期。
     */
    @PutMapping("/archive")
    @Operation(summary = "归档学期")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('edu:term:archive')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "学期正在归档中，请勿重复操作")
    public CommonResult<Boolean> archiveTerm(@RequestParam("id") Long id) {
        eduTermService.archiveTerm(id);
        return success(true);
    }

    /**
     * 启用学期。
     */
    @PutMapping("/enable")
    @Operation(summary = "启用学期")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('edu:term:update')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "学期正在启用中，请勿重复操作")
    public CommonResult<Boolean> enableTerm(@RequestParam("id") Long id) {
        eduTermService.enableTerm(id);
        return success(true);
    }

    /**
     * 获取学期详情。
     */
    @GetMapping("/get")
    @Operation(summary = "获得学期详情")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('edu:term:list')")
    public CommonResult<EduTermRespVO> getTerm(@RequestParam("id") Long id) {
        EduTermDO term = eduTermService.getTerm(id);
        return success(BeanUtils.toBean(term, EduTermRespVO.class));
    }

    /**
     * 分页查询学期。
     */
    @GetMapping("/page")
    @Operation(summary = "获得学期分页")
    @PreAuthorize("@ss.hasPermission('edu:term:list')")
    public CommonResult<PageResult<EduTermRespVO>> getTermPage(@Validated EduTermPageReqVO pageReqVO) {
        PageResult<EduTermDO> pageResult = eduTermService.getTermPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, EduTermRespVO.class));
    }

}
