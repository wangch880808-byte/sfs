package cn.nus.zknu.module.system.controller.admin.user;

import cn.nus.zknu.framework.common.pojo.CommonResult;
import cn.nus.zknu.framework.common.pojo.PageResult;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserPageReqVO;
import cn.nus.zknu.module.system.controller.admin.user.vo.user.UserRespVO;
import cn.nus.zknu.module.system.convert.user.UserConvert;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.service.dept.DeptService;
import cn.nus.zknu.module.system.service.user.AdminUserService;
import cn.nus.zknu.module.system.service.user.UserDeptScopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.common.pojo.CommonResult.success;
import static cn.nus.zknu.framework.common.util.collection.CollectionUtils.convertList;
import static cn.nus.zknu.module.system.enums.ErrorCodeConstants.USER_NOT_EXISTS;

/** 教务侧教师和学生账号归档；系统管理用户删除接口保持原语义。 */
@Tag(name = "管理后台 - 教务账号归档")
@RestController
@RequestMapping("/edu/user")
public class EduUserArchiveController {

    @Resource
    private AdminUserService userService;
    @Resource
    private DeptService deptService;
    @Resource
    private UserDeptScopeService deptScopeService;

    @GetMapping("/page")
    @Operation(summary = "教务侧教师或学生分页")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public CommonResult<PageResult<UserRespVO>> getUserPage(@Valid UserPageReqVO reqVO,
            @RequestParam(value = "includeArchived", defaultValue = "false") boolean includeArchived) {
        if (!Objects.equals(reqVO.getUserType(), 1) && !Objects.equals(reqVO.getUserType(), 2)) {
            throw exception(USER_NOT_EXISTS);
        }
        if (!includeArchived) {
            reqVO.setStatus(0);
        }
        PageResult<AdminUserDO> page = userService.getUserPage(reqVO);
        if (page.getList().isEmpty()) {
            return success(new PageResult<>(page.getTotal()));
        }
        Map<Long, DeptDO> deptMap = deptService.getDeptMap(convertList(page.getList(), AdminUserDO::getDeptId));
        return success(new PageResult<>(UserConvert.INSTANCE.convertList(page.getList(), deptMap), page.getTotal()));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "停用教师或学生账号")
    @PreAuthorize("@ss.hasPermission('system:user:delete')")
    public CommonResult<Boolean> deleteUser(@RequestParam("id") Long id) {
        setStatus(id, 1);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "批量停用教师或学生账号")
    @PreAuthorize("@ss.hasPermission('system:user:delete')")
    public CommonResult<Boolean> deleteUserList(@RequestParam("ids") List<Long> ids) {
        ids.forEach(id -> setStatus(id, 1));
        return success(true);
    }

    @PutMapping("/restore")
    @Operation(summary = "恢复教师或学生账号")
    @PreAuthorize("@ss.hasPermission('system:user:delete')")
    public CommonResult<Boolean> restoreUser(@RequestParam("id") Long id) {
        setStatus(id, 0);
        return success(true);
    }

    @PutMapping("/restore-list")
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "批量恢复教师或学生账号")
    @PreAuthorize("@ss.hasPermission('system:user:delete')")
    public CommonResult<Boolean> restoreUserList(@RequestParam("ids") List<Long> ids) {
        ids.forEach(id -> setStatus(id, 0));
        return success(true);
    }

    private void setStatus(Long id, int status) {
        AdminUserDO user = userService.getUser(id);
        // 受学院范围限制的管理员读不到外院账号：按越权处理，不提示“用户不存在”
        if (user == null && deptScopeService.isRestricted()) {
            throw exception(FORBIDDEN);
        }
        if (user == null || (!Objects.equals(user.getUserType(), 1)
                && !Objects.equals(user.getUserType(), 2))) {
            throw exception(USER_NOT_EXISTS);
        }
        userService.updateUserStatus(id, status);
    }
}
