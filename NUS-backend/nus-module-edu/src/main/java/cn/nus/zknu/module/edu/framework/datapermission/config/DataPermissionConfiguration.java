package cn.nus.zknu.module.edu.framework.datapermission.config;

import cn.nus.zknu.framework.datapermission.core.rule.dept.DeptDataPermissionRuleCustomizer;
import cn.nus.zknu.module.edu.dal.dataobject.clazz.EduClassDO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** edu 模块的部门数据权限。 */
@Configuration(value = "eduDataPermissionConfiguration", proxyBeanMethods = false)
public class DataPermissionConfiguration {

    @Bean
    public DeptDataPermissionRuleCustomizer eduDeptDataPermissionRuleCustomizer() {
        return rule -> rule.addDeptColumn(EduClassDO.class);
    }
}
