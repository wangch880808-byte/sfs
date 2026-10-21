package cn.nus.zknu.module.edu.controller.admin.clazz;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.framework.idempotent.core.annotation.Idempotent;
import cn.nus.zknu.framework.idempotent.core.keyresolver.impl.RequestHeaderIdempotentKeyResolver;
import cn.nus.zknu.module.edu.controller.admin.clazz.vo.EduClassPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.clazz.vo.EduClassRespVO;
import cn.nus.zknu.module.edu.controller.admin.clazz.vo.EduClassSaveReqVO;
import cn.nus.zknu.module.edu.controller.admin.clazz.vo.EduClassSimpleRespVO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.service.clazz.EduClassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 班级管理")
@RestController
@RequestMapping("/edu/class")
@Validated
public class EduClassController {

    @Resource
    private EduClassService eduClassService;

    /**
     * 创建班级。
     */
    @PostMapping("/create")
    @Operation(summary = "创建班级")
    @PreAuthorize("@ss.hasPermission('edu:class:create')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "班级正在提交中，请勿重复操作")
    public CommonResult<Long> createClass(@Valid @RequestBody EduClassSaveReqVO createReqVO) {
        return success(eduClassService.createClass(createReqVO));
    }

    /**
     * 更新班级。
     */
    @PutMapping("/update")
    @Operation(summary = "更新班级")
    @PreAuthorize("@ss.hasPermission('edu:class:update')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "班级正在处理中，请勿重复操作")
    public CommonResult<Boolean> updateClass(@Valid @RequestBody EduClassSaveReqVO updateReqVO) {
        eduClassService.updateClass(updateReqVO);
        return success(true);
    }

    /**
     * 删除单个班级。
     */
    @DeleteMapping("/delete")
    @Operation(summary = "封存班级")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('edu:class:delete')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "班级正在封存中，请勿重复操作")
    public CommonResult<Boolean> deleteClass(@RequestParam("id") Long id) {
        eduClassService.deleteClass(id);
        return success(true);
    }

    /**
     * 批量删除班级。
     */
    @DeleteMapping("/delete-list")
    @Operation(summary = "批量封存班级")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('edu:class:delete')")
    @Idempotent(timeout = 2, timeUnit = TimeUnit.MINUTES,
            keyResolver = RequestHeaderIdempotentKeyResolver.class,
            message = "班级正在封存中，请勿重复操作")
    public CommonResult<Boolean> deleteClassList(@RequestParam("ids") List<Long> ids) {
        eduClassService.deleteClassList(ids);
        return success(true);
    }

    @PutMapping("/restore")
    @Operation(summary = "恢复班级")
    @PreAuthorize("@ss.hasPermission('edu:class:delete')")
    public CommonResult<Boolean> restoreClass(@RequestParam("id") Long id) {
        eduClassService.restoreClass(id);
        return success(true);
    }

    @PutMapping("/restore-list")
    @Operation(summary = "批量恢复班级")
    @PreAuthorize("@ss.hasPermission('edu:class:delete')")
    public CommonResult<Boolean> restoreClassList(@RequestParam("ids") List<Long> ids) {
        eduClassService.restoreClassList(ids);
        return success(true);
    }

    /**
     * 获取班级详情。
     */
    @GetMapping("/get")
    @Operation(summary = "获得班级详情")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('edu:class:query')")
    public CommonResult<EduClassRespVO> getClass(@RequestParam("id") Long id) {
        EduClassDO eduClass = eduClassService.getClass(id);
        return success(BeanUtils.toBean(eduClass, EduClassRespVO.class));
    }

    /**
     * 分页查询班级。
     */
    @GetMapping("/page")
    @Operation(summary = "获得班级分页")
    @PreAuthorize("@ss.hasPermission('edu:class:query')")
    public CommonResult<PageResult<EduClassRespVO>> getClassPage(@Validated EduClassPageReqVO pageReqVO) {
        PageResult<EduClassDO> pageResult = eduClassService.getClassPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, EduClassRespVO.class));
    }

    /**
     * 获取班级精简列表。
     */
    @GetMapping({"/list-all-simple", "/simple-list"})
    @Operation(summary = "获得班级精简列表")
    @PreAuthorize("@ss.hasAnyPermissions('system:user:query', 'edu:homework:query')")
    public CommonResult<List<EduClassSimpleRespVO>> getSimpleClassList(
            @RequestParam(value = "deptId", required = false) Long deptId,
            @RequestParam(value = "gradeYear", required = false) Integer gradeYear) {
        return success(BeanUtils.toBean(eduClassService.getSimpleClassList(deptId, gradeYear), EduClassSimpleRespVO.class));
    }

}
