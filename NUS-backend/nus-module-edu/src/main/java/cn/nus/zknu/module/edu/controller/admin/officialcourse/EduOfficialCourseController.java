package cn.nus.zknu.module.edu.controller.admin.officialcourse;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.framework.idempotent.core.annotation.Idempotent;
import cn.nus.zknu.framework.idempotent.core.keyresolver.impl.RequestHeaderIdempotentKeyResolver;
import cn.nus.zknu.module.edu.controller.admin.officialcourse.vo.EduOfficialCoursePageReqVO;
import cn.nus.zknu.module.edu.controller.admin.officialcourse.vo.EduOfficialCourseRespVO;
import cn.nus.zknu.module.edu.controller.admin.officialcourse.vo.EduOfficialCourseSaveReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.officialcourse.EduOfficialCourseDO;
import cn.nus.zknu.module.edu.service.officialcourse.EduOfficialCourseService;
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

@Tag(name = "管理后台 - 正式课程管理")
@RestController
@RequestMapping("/edu/official-course")
@Validated
public class EduOfficialCourseController {

    @Resource
    private EduOfficialCourseService eduOfficialCourseService;

    /**
     * 创建正式课程。
     */
    @PostMapping("/create")
    @Operation(summary = "创建正式课程")
    @PreAuthorize("@ss.hasPermission('edu:official-course:create')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "正式课程正在提交中，请勿重复操作")
    public CommonResult<Long> createOfficialCourse(@Valid @RequestBody EduOfficialCourseSaveReqVO createReqVO) {
        return success(eduOfficialCourseService.createOfficialCourse(createReqVO));
    }

    /**
     * 更新正式课程。
     */
    @PutMapping("/update")
    @Operation(summary = "更新正式课程")
    @PreAuthorize("@ss.hasPermission('edu:official-course:update')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "正式课程正在处理中，请勿重复操作")
    public CommonResult<Boolean> updateOfficialCourse(@Valid @RequestBody EduOfficialCourseSaveReqVO updateReqVO) {
        eduOfficialCourseService.updateOfficialCourse(updateReqVO);
        return success(true);
    }

    /**
     * 归档正式课程。
     */
    @PutMapping("/archive")
    @Operation(summary = "归档正式课程")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('edu:official-course:archive')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "正式课程正在归档中，请勿重复操作")
    public CommonResult<Boolean> archiveOfficialCourse(@RequestParam("id") Long id) {
        eduOfficialCourseService.archiveOfficialCourse(id);
        return success(true);
    }

    /**
     * 获取正式课程详情。
     */
    @GetMapping("/get")
    @Operation(summary = "获得正式课程详情")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('edu:official-course:list')")
    public CommonResult<EduOfficialCourseRespVO> getOfficialCourse(@RequestParam("id") Long id) {
        EduOfficialCourseDO course = eduOfficialCourseService.getOfficialCourse(id);
        return success(BeanUtils.toBean(course, EduOfficialCourseRespVO.class));
    }

    /**
     * 分页查询正式课程。
     */
    @GetMapping("/page")
    @Operation(summary = "获得正式课程分页")
    @PreAuthorize("@ss.hasPermission('edu:official-course:list')")
    public CommonResult<PageResult<EduOfficialCourseRespVO>> getOfficialCoursePage(@Validated EduOfficialCoursePageReqVO pageReqVO) {
        PageResult<EduOfficialCourseDO> pageResult = eduOfficialCourseService.getOfficialCoursePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, EduOfficialCourseRespVO.class));
    }

}
