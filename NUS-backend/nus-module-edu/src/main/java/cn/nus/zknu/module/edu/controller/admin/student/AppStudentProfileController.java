package cn.nus.zknu.module.edu.controller.admin.student;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentPasswordReqVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentProfileRespVO;
import cn.nus.zknu.module.edu.controller.admin.student.vo.AppStudentProfileUpdateReqVO;
import cn.nus.zknu.module.edu.dal.dataobject.classstudent.EduClassStudentDO;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.mysql.classstudent.EduClassStudentMapper;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.system.api.user.AdminUserApi;
import cn.nus.zknu.module.system.api.user.dto.AdminUserRespDTO;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.dal.dataobject.user.UserProfileDO;
import cn.nus.zknu.module.system.controller.admin.user.vo.profile.UserProfileUpdatePasswordReqVO;
import cn.nus.zknu.module.system.controller.admin.user.vo.profile.UserProfileUpdateReqVO;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import cn.nus.zknu.module.system.service.dept.DeptService;
import cn.nus.zknu.module.system.service.user.profile.UserProfileExtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "学生端 - 个人中心")
@RestController
@RequestMapping("/edu/student/profile")
@Validated
public class AppStudentProfileController {

    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private AdminUserService adminUserService;
    @Resource
    private EduClassStudentMapper classStudentMapper;
    @Resource
    private EduClassMapper classMapper;
    @Resource
    private DeptService deptService;
    @Resource
    private UserProfileExtService userProfileExtService;

    @GetMapping("/get")
    @Operation(summary = "获取个人信息")
    public CommonResult<AppStudentProfileRespVO> getProfile() {
        Long userId = getLoginUserId();
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        AppStudentProfileRespVO resp = new AppStudentProfileRespVO();
        resp.setUserId(userId);
        resp.setUsername(user.getUsername());
        resp.setNickname(user.getNickname());
        resp.setAvatar(user.getAvatar());
        resp.setMobile(user.getMobile());
        resp.setEmail(user.getEmail());
        resp.setDeptId(user.getDeptId());
        DeptDO dept = user.getDeptId() == null ? null : deptService.getDept(user.getDeptId());
        resp.setDeptName(dept == null ? null : dept.getName());
        resp.setMajorName(dept == null ? null : dept.getName());
        UserProfileDO profile = userProfileExtService.getUserProfileByUserId(userId);
        resp.setGradeYear(profile == null ? null : profile.getGradeYear());
        // 查询班级信息
        List<EduClassStudentDO> classStudents = classStudentMapper.selectListByStudentIds(
                Collections.singletonList(userId));
        List<Long> classIds = classStudents.stream().map(EduClassStudentDO::getClassId).distinct().toList();
        List<EduClassDO> classes = classIds.isEmpty() ? Collections.emptyList() : classMapper.selectByIds(classIds);
        List<String> classNames = classes.stream().map(EduClassDO::getClassName)
                .filter(Objects::nonNull).collect(Collectors.toList());
        resp.setClassIds(classIds);
        resp.setClassNames(classNames);
        resp.setClassName(String.join("、", classNames));
        return success(resp);
    }

    @PutMapping("/update")
    @Operation(summary = "更新个人信息")
    public CommonResult<Boolean> updateProfile(@Valid @RequestBody AppStudentProfileUpdateReqVO reqVO) {
        UserProfileUpdateReqVO updateReqVO = new UserProfileUpdateReqVO();
        updateReqVO.setNickname(reqVO.getNickname());
        updateReqVO.setAvatar(reqVO.getAvatar());
        updateReqVO.setMobile(reqVO.getMobile());
        updateReqVO.setEmail(reqVO.getEmail());
        adminUserService.updateUserProfile(getLoginUserId(), updateReqVO);
        return success(true);
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码")
    public CommonResult<Boolean> updatePassword(@Valid @RequestBody AppStudentPasswordReqVO reqVO) {
        UserProfileUpdatePasswordReqVO passwordReqVO = new UserProfileUpdatePasswordReqVO();
        passwordReqVO.setOldPassword(reqVO.getOldPassword());
        passwordReqVO.setNewPassword(reqVO.getNewPassword());
        adminUserService.updateUserPassword(getLoginUserId(), passwordReqVO);
        return success(true);
    }

}
