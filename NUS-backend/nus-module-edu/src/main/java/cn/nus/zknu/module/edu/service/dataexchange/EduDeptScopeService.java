package cn.nus.zknu.module.edu.service.dataexchange;

import cn.hutool.json.JSONUtil;
import cn.nus.zknu.module.edu.framework.EduRequestCache;
import cn.nus.zknu.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.nus.zknu.framework.datapermission.core.util.DataPermissionUtils;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import cn.nus.zknu.module.edu.dal.mysql.clazz.EduClassMapper;
import cn.nus.zknu.module.edu.enums.importbatch.EduImportRowActionEnum;
import cn.nus.zknu.module.edu.service.importbatch.EduImportPreviewRow;
import cn.nus.zknu.module.system.api.permission.EduRoleApi;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.service.dept.DeptService;
import cn.nus.zknu.module.system.service.permission.PermissionService;
import cn.nus.zknu.module.system.service.user.UserDeptScopeService;
import cn.nus.zknu.module.system.service.user.UserDeptScopeRequestCache;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import static cn.nus.zknu.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.nus.zknu.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.nus.zknu.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** 学院教务维护权限；课程、任务和统计不在此范围内。 */
@Service
public class EduDeptScopeService {

    public static final String OUT_OF_SCOPE_REASON = "不在本学院";

    @Resource
    private UserDeptScopeService userDeptScopeService;
    @Resource
    private PermissionService permissionService;
    @Resource
    private EduRoleApi eduRoleApi;
    @Resource
    private DeptService deptService;
    @Resource
    private EduClassMapper classMapper;

    public boolean isDeptScopedAdmin() {
        Long userId = getLoginUserId();
        return UserDeptScopeRequestCache.get(EduDeptScopeService.class.getName() + ".admin", userId,
                () -> computeDeptScopedAdmin(userId));
    }

    private boolean computeDeptScopedAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        Set<String> adminCodes = new LinkedHashSet<>(eduRoleApi.getAdminRoleCodes());
        adminCodes.add("college_admin");
        return permissionService.hasAnyRoles(userId, adminCodes.toArray(String[]::new))
                && userDeptScopeService.isRestricted();
    }

    public DeptDataPermissionRespDTO getDeptDataPermission() {
        return userDeptScopeService.getDeptDataPermission();
    }

    public boolean isRestricted() {
        return userDeptScopeService.isRestricted();
    }

    public void validateDeptInScope(Long deptId) {
        userDeptScopeService.validateDeptInScope(deptId);
    }

    public boolean isDeptCodeInScope(String deptCode) {
        if (!isRestricted()) {
            return true;
        }
        DeptDO dept = deptCode == null ? null
                : EduRequestCache.get("deptByCode", deptCode,
                        () -> DataPermissionUtils.executeIgnore(() -> deptService.getDeptByCode(deptCode)));
        return dept != null && userDeptScopeService.isDeptInScope(dept.getId());
    }

    public boolean isExistingDeptCodeInScope(String deptCode) {
        if (!isRestricted() || deptCode == null) {
            return true;
        }
        DeptDO dept = EduRequestCache.get("deptByCode", deptCode,
                        () -> DataPermissionUtils.executeIgnore(() -> deptService.getDeptByCode(deptCode)));
        return dept == null || userDeptScopeService.isDeptInScope(dept.getId());
    }

    public boolean isUsernameInScope(String username) {
        return userDeptScopeService.isUsernameInScope(username);
    }

    public boolean isClassInScope(Long classId) {
        if (!isRestricted()) {
            return true;
        }
        EduClassDO clazz = classId == null ? null
                : EduRequestCache.get("class", classId,
                        () -> DataPermissionUtils.executeIgnore(() -> classMapper.selectById(classId)));
        return clazz != null && userDeptScopeService.isDeptInScope(clazz.getDeptId());
    }

    /** 批量预检已加载的实体，避免范围校验再次逐行查询。 */
    public boolean isLoadedClassInScope(EduClassDO clazz) {
        return !isRestricted() || (clazz != null && userDeptScopeService.isDeptInScope(clazz.getDeptId()));
    }

    public boolean isLoadedUsernameInScope(AdminUserDO user) {
        return !isRestricted() || user == null || userDeptScopeService.isDeptInScope(user.getDeptId());
    }

    public boolean isLoadedDeptCodeInScope(DeptDO dept) {
        return !isRestricted() || (dept != null && userDeptScopeService.isDeptInScope(dept.getId()));
    }

    public void validateClassInScope(Long classId) {
        if (!isClassInScope(classId)) {
            throw exception(FORBIDDEN);
        }
    }

    public void validateUserInScope(Long userId) {
        userDeptScopeService.validateUserInScope(userId);
    }

    public void validateAffectedRows(int affectedRows) {
        userDeptScopeService.validateAffectedRows(affectedRows);
    }

    public void markOutOfScope(EduImportPreviewRow row) {
        markSkipped(row, OUT_OF_SCOPE_REASON);
    }

    public void markSkipped(EduImportPreviewRow row, String reason) {
        row.setAction(EduImportRowActionEnum.SKIP);
        row.setErrors(new ArrayList<>());
        row.setNormalizedData(JSONUtil.parseObj(row.getNormalizedData()).set("skipReason", reason).toString());
        // 外院行不回传原数据，确认时也不会对跳过行执行快照检查。
        row.setBeforeData(null);
        row.setAfterData(null);
    }
}
