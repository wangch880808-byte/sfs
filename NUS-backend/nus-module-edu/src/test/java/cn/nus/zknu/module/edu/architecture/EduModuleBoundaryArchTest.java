package cn.nus.zknu.module.edu.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模块边界：edu 只能经 capacity、ai 的 api 包（接口与 DTO）访问这两个模块，
 * 不能注入它们的 Mapper、DO、Service、配置类或工具类。
 * <p>
 * MyBatis XML / 注解 SQL 里只读联表 capacity_course 等不在本测试范围内，见 V1.0.1.7 报告。
 */
class EduModuleBoundaryArchTest {

    private static final String EDU = "cn.nus.zknu.module.edu..";
    private static final String CAPACITY = "cn.nus.zknu.module.capacity..";
    private static final String CAPACITY_API = "cn.nus.zknu.module.capacity.api..";
    private static final String AI = "cn.nus.zknu.module.ai..";
    private static final String AI_API = "cn.nus.zknu.module.ai.api..";

    private static JavaClasses eduClasses;

    @BeforeAll
    static void importEduClasses() {
        eduClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("cn.nus.zknu.module.edu");
        assertTrue(eduClasses.size() > 100, "应导入 edu 模块的全部生产类，实际只有 " + eduClasses.size());
    }

    @Test
    void eduReachesCapacityAndAiOnlyThroughTheirApiPackages() {
        ArchRule rule = noClasses().that().resideInAPackage(EDU)
                .should().dependOnClassesThat(resideInAnyPackage(CAPACITY, AI)
                        .and(not(resideInAnyPackage(CAPACITY_API, AI_API))))
                .because("edu 只能经 capacity、ai 的 api 包访问这两个模块（V1.0.1.7 模块边界）");
        rule.check(eduClasses);
    }

    @Test
    void eduNeverTouchesCapacityOrAiDataAccessLayer() {
        ArchRule rule = noClasses().that().resideInAPackage(EDU)
                .should().dependOnClassesThat(resideInAPackage("cn.nus.zknu.module.capacity.dal..")
                        .or(resideInAPackage("cn.nus.zknu.module.ai.dal..")))
                .because("capacity、ai 的表只能由各自模块读写");
        rule.check(eduClasses);
    }

}
