package cn.nus.zknu.module.edu.controller.admin.classstudent;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.module.edu.controller.admin.classstudent.vo.EduClassStudentBatchReqVO;
import cn.nus.zknu.module.edu.controller.admin.classstudent.vo.EduClassStudentListReqVO;
import cn.nus.zknu.module.edu.controller.admin.classstudent.vo.EduClassStudentPageReqVO;
import cn.nus.zknu.module.edu.controller.admin.classstudent.vo.EduClassStudentRespVO;
import cn.nus.zknu.module.edu.service.classstudent.EduClassStudentService;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 班级学生关联")
@RestController
@RequestMapping("/edu/class/student")
@Validated
public class EduClassStudentController {

    @Resource
    private EduClassStudentService eduClassStudentService;

    /**
     * 获取可加入班级的学生分页。
     */
    @GetMapping("/page-available")
    @Operation(summary = "获得可加入学生分页")
    @PreAuthorize("@ss.hasPermission('edu:class:query')")
    public CommonResult<PageResult<EduClassStudentRespVO>> getAvailableStudentPage(@Valid EduClassStudentPageReqVO reqVO) {
        PageResult<AdminUserDO> pageResult = eduClassStudentService.getAvailableStudentPage(reqVO);
        return success(BeanUtils.toBean(pageResult, EduClassStudentRespVO.class));
    }

    /**
     * 获取已加入班级的学生列表。
     */
    @GetMapping("/list-assigned")
    @Operation(summary = "获得已加入班级学生列表")
    @PreAuthorize("@ss.hasPermission('edu:class:query')")
    public CommonResult<List<EduClassStudentRespVO>> getAssignedStudentList(@Valid EduClassStudentListReqVO reqVO) {
        return success(BeanUtils.toBean(eduClassStudentService.getAssignedStudentList(reqVO), EduClassStudentRespVO.class));
    }

    /**
     * 批量加入班级。
     */
    @PostMapping("/add")
    @Operation(summary = "批量加入班级")
    @PreAuthorize("@ss.hasPermission('edu:class:update')")
    public CommonResult<Boolean> addStudents(@Valid @RequestBody EduClassStudentBatchReqVO reqVO) {
        eduClassStudentService.addStudents(reqVO.getClassId(), reqVO.getStudentIds(), reqVO.getRemark());
        return success(true);
    }

    /**
     * 校验当前批量加入是否必须填写备注。
     */
    @PostMapping("/check-add-remark")
    @Operation(summary = "检查批量加入班级是否需要备注")
    @PreAuthorize("@ss.hasPermission('edu:class:update')")
    public CommonResult<Boolean> checkAddRemarkRequired(@Valid @RequestBody EduClassStudentBatchReqVO reqVO) {
        return success(eduClassStudentService.isAddRemarkRequired(reqVO.getClassId(), reqVO.getStudentIds()));
    }

    /**
     * 批量移出班级。
     */
    @PostMapping("/remove")
    @Operation(summary = "批量移出班级")
    @PreAuthorize("@ss.hasPermission('edu:class:update')")
    public CommonResult<Boolean> removeStudents(@Valid @RequestBody EduClassStudentBatchReqVO reqVO) {
        eduClassStudentService.removeStudents(reqVO.getClassId(), reqVO.getStudentIds());
        return success(true);
    }

}
