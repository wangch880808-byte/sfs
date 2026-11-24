package cn.nus.zknu.module.edu.controller.admin.courseresource;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.framework.idempotent.core.annotation.Idempotent;
import cn.nus.zknu.framework.idempotent.core.keyresolver.impl.RequestHeaderIdempotentKeyResolver;
import cn.nus.zknu.module.edu.controller.admin.courseresource.vo.EduCourseResourcePageReqVO;
import cn.nus.zknu.module.edu.controller.admin.courseresource.vo.EduCourseResourceRespVO;
import cn.nus.zknu.module.edu.controller.admin.courseresource.vo.EduCourseResourceReviewReqVO;
import cn.nus.zknu.module.edu.controller.admin.courseresource.vo.EduCourseResourceSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.courseresource.EduCourseResourceDO;
import cn.nus.zknu.module.edu.service.courseresource.EduCourseResourceService;
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

@Tag(name = "管理后台 - 课程资源管理")
@RestController
@RequestMapping("/edu/course-resource")
@Validated
public class EduCourseResourceController {

    @Resource
    private EduCourseResourceService eduCourseResourceService;

    /**
     * 上传课程资源。
     */
    @PostMapping("/upload")
    @Operation(summary = "上传课程资源")
    @PreAuthorize("@ss.hasPermission('edu:course-resource:create')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "资源正在提交中，请勿重复操作")
    public CommonResult<Long> uploadResource(@Valid @RequestBody EduCourseResourceSaveReqVO createReqVO) {
        return success(eduCourseResourceService.createResource(createReqVO));
    }

    /**
     * 删除课程资源（软删除）。
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除课程资源")
    @Parameter(name = "id", description = "资源 ID", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('edu:course-resource:delete')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "资源正在删除中，请勿重复操作")
    public CommonResult<Boolean> deleteResource(@RequestParam("id") Long id) {
        eduCourseResourceService.deleteResource(id);
        return success(true);
    }

    /**
     * 获取课程资源列表（分页）。
     */
    @GetMapping("/list")
    @Operation(summary = "获取课程资源列表")
    @PreAuthorize("@ss.hasPermission('edu:course-resource:query')")
    public CommonResult<PageResult<EduCourseResourceRespVO>> listResources(@Validated EduCourseResourcePageReqVO reqVO) {
        PageResult<EduCourseResourceDO> pageResult = eduCourseResourceService.getResourcePage(reqVO);
        return success(BeanUtils.toBean(pageResult, EduCourseResourceRespVO.class));
    }

    /**
     * 下载课程资源（返回资源详情，前端根据 fileUrl 下载）。
     */
    @GetMapping("/download")
    @Operation(summary = "下载课程资源")
    @Parameter(name = "id", description = "资源 ID", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('edu:course-resource:query')")
    public CommonResult<EduCourseResourceRespVO> downloadResource(@RequestParam("id") Long id) {
        EduCourseResourceDO resource = eduCourseResourceService.getResource(id);
        return success(BeanUtils.toBean(resource, EduCourseResourceRespVO.class));
    }

    @GetMapping("/preview-text")
    @Operation(summary = "获取课程资源文本预览")
    @Parameter(name = "id", description = "资源 ID", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('edu:course-resource:query')")
    public CommonResult<String> previewResourceText(@RequestParam("id") Long id) {
        return success(eduCourseResourceService.getResourcePreviewText(id));
    }

    /**
     * 待审核课程资源分页（reviewStatus = 0）。
     */
    @GetMapping("/review/page")
    @Operation(summary = "获取待审核课程资源列表")
    @PreAuthorize("@ss.hasPermission('edu:course-resource:review')")
    public CommonResult<PageResult<EduCourseResourceRespVO>> reviewPage(@Validated EduCourseResourcePageReqVO reqVO) {
        PageResult<EduCourseResourceDO> pageResult = eduCourseResourceService.getReviewPage(reqVO);
        return success(BeanUtils.toBean(pageResult, EduCourseResourceRespVO.class));
    }

    /**
     * 审核课程资源（通过 / 驳回）。
     */
    @PostMapping("/{id}/review")
    @Operation(summary = "审核课程资源")
    @Parameter(name = "id", description = "资源 ID", required = true, example = "1024")
    @PreAuthorize("@ss.hasAnyPermissions('edu:course-resource:approve', 'edu:course-resource:reject')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "资源正在审核中，请勿重复操作")
    public CommonResult<Boolean> reviewResource(@PathVariable("id") Long id,
            @Valid @RequestBody EduCourseResourceReviewReqVO reqVO) {
        eduCourseResourceService.reviewResource(id, reqVO);
        return success(true);
    }

}
