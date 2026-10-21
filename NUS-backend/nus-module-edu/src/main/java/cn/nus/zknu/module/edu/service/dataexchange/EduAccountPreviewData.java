package cn.nus.zknu.module.edu.service.dataexchange;

import cn.nus.zknu.module.system.dal.dataobject.dept.DeptDO;
import cn.nus.zknu.module.system.dal.dataobject.dept.DeptImportLookupDO;
import cn.nus.zknu.module.system.dal.dataobject.user.AdminUserDO;
import cn.nus.zknu.module.system.dal.dataobject.user.UserImportLookupDO;
import cn.nus.zknu.module.system.dal.dataobject.user.UserProfileDO;
import java.util.*;

/** 单次预检的批量数据；输入键由 SQL 匹配，Map 使用原始输入键。 */
public class EduAccountPreviewData {
    public final Map<String, AdminUserDO> users = new HashMap<>();
    public final Map<String, AdminUserDO> scopeUsers = new HashMap<>();
    public final Map<Long, UserProfileDO> profiles = new HashMap<>();
    public final Map<Long, DeptDO> depts = new HashMap<>();
    public final Map<String, DeptDO> deptCodes = new HashMap<>();
    public final Map<String, DeptDO> scopeDeptCodes = new HashMap<>();
    private final Map<String, List<AdminUserDO>> mobiles = new HashMap<>();
    private final Map<String, List<AdminUserDO>> emails = new HashMap<>();

    public static Map<String, AdminUserDO> usersByKey(List<UserImportLookupDO> rows) {
        Map<String, AdminUserDO> map = new HashMap<>();
        rows.forEach(row -> map.putIfAbsent(row.getLookupKey(), row));
        return map;
    }

    public static Map<String, DeptDO> deptsByKey(List<DeptImportLookupDO> rows) {
        Map<String, DeptDO> map = new HashMap<>();
        rows.forEach(row -> map.putIfAbsent(row.getLookupKey(), row));
        return map;
    }

    public void setContactOwners(List<UserImportLookupDO> mobileRows, List<UserImportLookupDO> emailRows) {
        mobileRows.forEach(row -> mobiles.computeIfAbsent(row.getLookupKey(), key -> new ArrayList<>()).add(row));
        emailRows.forEach(row -> emails.computeIfAbsent(row.getLookupKey(), key -> new ArrayList<>()).add(row));
    }

    public AdminUserDO mobileOwner(String value, AdminUserDO existing) {
        if (value == null || (existing != null && value.equals(existing.getMobile()))) return null;
        return otherOwner(mobiles.get(value), existing);
    }

    public AdminUserDO emailOwner(String value, AdminUserDO existing) {
        if (value == null || (existing != null && value.equalsIgnoreCase(existing.getEmail()))) return null;
        return otherOwner(emails.get(value), existing);
    }

    private AdminUserDO otherOwner(List<AdminUserDO> owners, AdminUserDO existing) {
        if (owners == null) return null;
        return owners.stream().filter(owner -> existing == null || !Objects.equals(owner.getId(), existing.getId()))
                .findFirst().orElse(null);
    }

    public void validateContactUniqueness(String username, String mobile, String email, List<String> errors) {
        AdminUserDO existing = users.get(username);
        AdminUserDO mobileOwner = mobileOwner(mobile, existing);
        if (mobileOwner != null) errors.add("手机号已被账号「" + mobileOwner.getUsername() + "」使用");
        AdminUserDO emailOwner = emailOwner(email, existing);
        if (emailOwner != null) errors.add("邮箱已被账号「" + emailOwner.getUsername() + "」使用");
    }
}
