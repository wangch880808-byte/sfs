package cn.nus.zknu.module.ai.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止合并时丢失字段注入注解；@InjectMocks 并不能验证生产环境的注入。 */
class InjectionGuardArchTest {

    private static final Pattern DEPENDENCY_SUFFIX = Pattern.compile(
            ".*(Service|Mapper|Api|Notifier|Policy|Resolver|Executor)$", Pattern.CASE_INSENSITIVE);

    private static final ArchRule INJECTION_RULE = fields()
            .that(new DescribedPredicate<JavaField>("are mutable dependency fields in Spring beans") {
                @Override
                public boolean test(JavaField field) {
                    if (field.getModifiers().contains(JavaModifier.STATIC)
                            || field.getModifiers().contains(JavaModifier.FINAL)) {
                        return false;
                    }
                    // volatile 字段是类自己维护的运行时状态（如缓存），不是注入的依赖
                    if (field.getModifiers().contains(JavaModifier.VOLATILE)) {
                        return false;
                    }
                    var owner = field.getOwner();
                    if (!(owner.isAnnotatedWith(Service.class) || owner.isAnnotatedWith(Component.class)
                            || owner.isAnnotatedWith(RestController.class) || owner.isAnnotatedWith(Configuration.class))) {
                        return false;
                    }
                    // 日志写线程由该类自行创建并在 @PreDestroy 关闭；writer 初始化为 writerPool，仅测试时替换。
                    if (owner.getName().equals("cn.nus.zknu.module.ai.service.log.AiCallLogServiceImpl")
                            && field.getName().equals("writer")) {
                        return false;
                    }
                    return field.getRawType().getName().startsWith("cn.nus.zknu.")
                            || DEPENDENCY_SUFFIX.matcher(field.getName()).matches()
                            || DEPENDENCY_SUFFIX.matcher(field.getRawType().getSimpleName()).matches();
                }
            })
            .should(new ArchCondition<JavaField>("have @Resource or @Autowired") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    if (!field.isAnnotatedWith(Resource.class) && !field.isAnnotatedWith(Autowired.class)) {
                        events.add(SimpleConditionEvent.violated(field,
                                field.getFullName() + " 缺少 @Resource 或 @Autowired"));
                    }
                }
            });

    @Test
    void mutableBeanDependenciesRequireInjectionAnnotations() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("cn.nus.zknu.module.ai");
        assertTrue(classes.size() > 0, "必须导入本模块的生产类");
        INJECTION_RULE.check(classes);
    }
}
