package cn.nus.zknu.module.system.controller.admin.user;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.util.object.BeanUtils;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserProfileExtRespVO;
import cn.nus.zknu.module.system.dal.dataobject.user.UserProfileDO;
import cn.nus.zknu.module.system.service.user.profile.UserProfileExtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.nus.zknu.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 用户扩展信息")
@RestController
@RequestMapping("/system/user-profile")
@Validated
public class UserProfileExtController {

    @Resource
    private UserProfileExtService userProfileExtService;

    @GetMapping({"/list-all-simple", "/simple-list"})
    @Operation(summary = "获取用户扩展信息精简列表")
    @Parameter(name = "userType", description = "用户类型", example = "1")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public CommonResult<List<UserProfileExtRespVO>> getSimpleList(
            @RequestParam(value = "userType", required = false) Integer userType) {
        List<UserProfileDO> list = userProfileExtService.getSimpleList(userType);
        return success(BeanUtils.toBean(list, UserProfileExtRespVO.class));
    }

}
