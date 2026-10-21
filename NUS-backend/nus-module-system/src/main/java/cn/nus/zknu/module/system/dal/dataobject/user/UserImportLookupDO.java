package cn.nus.zknu.module.system.dal.dataobject.user;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 批量查询时保留输入键；键的匹配由数据库按原字段的排序规则完成。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserImportLookupDO extends AdminUserDO {
    private String lookupKey;
}
