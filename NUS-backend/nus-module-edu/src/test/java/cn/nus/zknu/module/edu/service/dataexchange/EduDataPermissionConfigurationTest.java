package cn.nus.zknu.module.edu.service.dataexchange;

import cn.nus.zknu.framework.datapermission.core.rule.dept.DeptDataPermissionRuleCustomizer;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EduDataPermissionConfigurationTest {

    @Test
    void eduAndSystemDepartmentRulesCanBeRegisteredTogether() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setAllowBeanDefinitionOverriding(false);
            context.register(cn.nus.zknu.module.edu.framework.datapermission.config.DataPermissionConfiguration.class,
                    cn.nus.zknu.module.system.framework.datapermission.config.DataPermissionConfiguration.class);
            context.refresh();
            assertEquals(2, context.getBeansOfType(DeptDataPermissionRuleCustomizer.class).size());
        }
    }
}
