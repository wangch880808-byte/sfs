package cn.nus.zknu.module.edu.controller.admin.semester;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterAutoModeReqVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterOptionRespVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterRespVO;
import cn.nus.zknu.module.edu.controller.admin.semester.vo.EduSemesterSaveReqVO;
import cn.nus.zknu.module.edu.service.semester.EduSemesterOptionService;
import cn.nus.zknu.module.edu.service.semester.EduSemesterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 学期管理")
@RestController
@RequestMapping("/edu/semester")
@Validated
public class EduSemesterController {

    @Resource
    private EduSemesterService eduSemesterService;
    @Resource
    private EduSemesterOptionService semesterOptionService;

    @GetMapping("/current")
    @Operation(summary = "获取当前学期信息")
    public CommonResult<EduSemesterRespVO> getCurrentSemester() {
        return success(eduSemesterService.getCurrentSemester());
    }

    @GetMapping("/options")
    @Operation(summary = "可选学期：当前学期和班级课程里用过的学期")
    @PreAuthorize("@ss.hasAnyPermissions('edu:class-course:bind', 'edu:homework:copy', 'edu:semester:wrap-up')")
    public CommonResult<List<EduSemesterOptionRespVO>> listSemesterOptions() {
        return success(semesterOptionService.listOptions());
    }

    @PutMapping("/current")
    @Operation(summary = "保存当前学期信息")
    @PreAuthorize("@ss.hasPermission('edu:semester:update')")
    public CommonResult<Boolean> saveCurrentSemester(@Valid @RequestBody EduSemesterSaveReqVO reqVO) {
        eduSemesterService.saveCurrentSemester(reqVO);
        return success(true);
    }

    @PutMapping("/auto-mode")
    @Operation(summary = "设置学期自动更新模式")
    @PreAuthorize("@ss.hasPermission('edu:semester:update')")
    public CommonResult<Boolean> setAutoMode(@Valid @RequestBody EduSemesterAutoModeReqVO reqVO) {
        eduSemesterService.setAutoEnabled(reqVO.getAutoEnabled());
        return success(true);
    }
}
