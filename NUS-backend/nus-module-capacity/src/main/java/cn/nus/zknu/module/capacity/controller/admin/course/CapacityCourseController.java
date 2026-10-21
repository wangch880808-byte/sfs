package cn.nus.zknu.module.capacity.controller.admin.course;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.framework.idempotent.core.annotation.Idempotent;
import cn.nus.zknu.framework.idempotent.core.keyresolver.impl.RequestHeaderIdempotentKeyResolver;
import cn.nus.zknu.module.capacity.controller.admin.course.vo.CapacityCourseAuditReqVO;
import cn.nus.zknu.module.capacity.controller.admin.course.vo.CapacityCoursePageReqVO;
import cn.nus.zknu.module.capacity.controller.admin.course.vo.CapacityCourseRespVO;
import cn.nus.zknu.module.capacity.controller.admin.course.vo.CapacityCourseSaveReqVO;
import cn.nus.zknu.module.capacity.dal.dataobject.course.CapacityCourseDO;
import cn.nus.zknu.module.capacity.service.course.CapacityCourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 实训课程")
@RestController
@RequestMapping("/capacity/course")
@Validated
public class CapacityCourseController {

    @Resource
    private CapacityCourseService capacityCourseService;

    /**
     * 创建课程；课程 JSON 与封面文件通过 multipart 同请求提交。
     */
    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "创建实训课程")
    @PreAuthorize("@ss.hasPermission('capacity:indicator:create')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "课程正在提交中，请勿重复操作")
    public CommonResult<Long> createCourse(@Valid @RequestPart("course") CapacityCourseSaveReqVO createReqVO,
                                           @RequestPart(value = "courseImageFile", required = false) MultipartFile courseImageFile) throws Exception {
        // 课程基础数据与图片文件同请求提交，后端统一走 service 内部上传逻辑
        return success(capacityCourseService.createCourse(createReqVO, courseImageFile));
    }

    /**
     * 更新课程；支持仅更新字段或同时替换封面文件。
     */
    @PutMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "更新实训课程")
    // 教师凭“课程新增”权限维护本人课程，服务层限定只能修改本人创建的课程
    @PreAuthorize("@ss.hasAnyPermissions('capacity:indicator:update', 'capacity:indicator:create')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "课程正在处理中，请勿重复操作")
    public CommonResult<Boolean> updateCourse(@Valid @RequestPart("course") CapacityCourseSaveReqVO updateReqVO,
                                              @RequestPart(value = "courseImageFile", required = false) MultipartFile courseImageFile) throws Exception {
        // 支持“仅改字段不换图”与“改字段并换图”两种场景
        capacityCourseService.updateCourse(updateReqVO, courseImageFile);
        return success(true);
    }

    /**
     * 删除单个课程。
     */
    @DeleteMapping("/delete")
    @Operation(summary = "封存实训课程")
    @Parameter(name = "id", description = "课程编号", required = true, example = "1024")
    // 教师凭“课程新增”权限删除本人课程，服务层限定只能删除本人创建的课程
    @PreAuthorize("@ss.hasAnyPermissions('capacity:indicator:delete', 'capacity:indicator:create')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "课程正在封存中，请勿重复操作")
    public CommonResult<Boolean> deleteCourse(@RequestParam("id") Long id) {
        capacityCourseService.deleteCourse(id);
        return success(true);
    }

    /**
     * 批量删除课程。
     */
    @DeleteMapping("/delete-list")
    @Operation(summary = "批量封存实训课程")
    @Parameter(name = "ids", description = "课程编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('capacity:indicator:delete')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "课程正在封存中，请勿重复操作")
    public CommonResult<Boolean> deleteCourseList(@RequestParam("ids") List<Long> ids) {
        capacityCourseService.deleteCourseList(ids);
        return success(true);
    }

    @PutMapping("/restore")
    @Operation(summary = "恢复实训课程")
    @PreAuthorize("@ss.hasAnyPermissions('capacity:indicator:delete', 'capacity:indicator:create')")
    public CommonResult<Boolean> restoreCourse(@RequestParam("id") Long id) {
        capacityCourseService.restoreCourse(id);
        return success(true);
    }

    @PutMapping("/restore-list")
    @Operation(summary = "批量恢复实训课程")
    @PreAuthorize("@ss.hasPermission('capacity:indicator:delete')")
    public CommonResult<Boolean> restoreCourseList(@RequestParam("ids") List<Long> ids) {
        capacityCourseService.restoreCourseList(ids);
        return success(true);
    }

    /**
     * 获取课程详情。
     */
    @GetMapping("/get")
    @Operation(summary = "获得实训课程详情")
    @Parameter(name = "id", description = "课程编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('capacity:indicator:query')")
    public CommonResult<CapacityCourseRespVO> getCourse(@RequestParam("id") Long id) {
        CapacityCourseDO course = capacityCourseService.getCourse(id);
        return success(BeanUtils.toBean(course, CapacityCourseRespVO.class));
    }

    /**
     * 分页查询课程。
     */
    @GetMapping("/page")
    @Operation(summary = "获得实训课程分页")
    @PreAuthorize("@ss.hasPermission('capacity:indicator:query')")
    public CommonResult<PageResult<CapacityCourseRespVO>> getCoursePage(@Validated CapacityCoursePageReqVO pageReqVO) {
        PageResult<CapacityCourseDO> pageResult = capacityCourseService.getCoursePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CapacityCourseRespVO.class));
    }

    /**
     * 审批公共课申请，需要“公共课审批”权限（capacity:course:audit），在角色管理中按需授予。
     */
    @PostMapping("/audit-public")
    @Operation(summary = "审批公共实训课程")
    @PreAuthorize("@ss.hasPermission('capacity:course:audit')")
    public CommonResult<Boolean> auditPublicCourse(@Valid @RequestBody CapacityCourseAuditReqVO reqVO) {
        capacityCourseService.auditPublicCourse(reqVO);
        return success(true);
    }

}
