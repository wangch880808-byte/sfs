package cn.nus.zknu.module.edu.architecture;

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
                .importPackages("cn.nus.zknu.module.edu");
        assertTrue(classes.size() > 0, "必须导入本模块的生产类");
        INJECTION_RULE.check(classes);
    }

    @Test
    void guardRejectsMissingAnnotationsForAllFourBeanStereotypes() {
        for (Class<?> bean : new Class<?>[]{MissingService.class, MissingComponent.class,
                MissingController.class, MissingConfiguration.class}) {
            assertTrue(INJECTION_RULE.evaluate(new ClassFileImporter().importClasses(bean)).hasViolation(),
                    bean.getSimpleName() + " 必须被注入守护捕获");
        }
    }

    @Test
    void guardAllowsBothAnnotationsAndIgnoresStaticAndFinalFields() {
        org.junit.jupiter.api.Assertions.assertFalse(INJECTION_RULE.evaluate(
                new ClassFileImporter().importClasses(ValidBean.class)).hasViolation());
    }

    private interface Dependency {
    }

    @Service
    private static class MissingService {
        private Dependency arbitraryName;
    }

    @Component
    private static class MissingComponent {
        private Runnable taskExecutor;
    }

    @RestController
    private static class MissingController {
        private Dependency dependency;
    }

    @Configuration
    private static class MissingConfiguration {
        private Dependency dependency;
    }

    @Service
    private static class ValidBean {
        @Resource
        private Dependency resourceDependency;
        @Autowired
        private Runnable taskExecutor;
        private static Dependency staticDependency;
        private final Dependency finalDependency = null;
    }
}
