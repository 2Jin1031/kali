package kali;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.Slice;
import org.junit.jupiter.api.Test;

/** docs/adr/0011-package-structure.md의 패키지 구조와 의존 방향 규칙. */
class ArchitectureTest {

    private static final String COMMON = "kali.common..";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("kali");

    // kali.common은 레이어 규칙을 따르지 않으므로 kali.common.infra 같은 하위 패키지도 기능 레이어로 보지 않는다
    private static final DescribedPredicate<Slice> FEATURE_SLICE =
            describe("kali.common이 아닌 기능", slice -> !"common".equals(slice.getNamePart(1)));

    private static DescribedPredicate<JavaClass> featureLayer(String layer) {
        return resideInAPackage("kali.*." + layer + "..")
                .and(not(resideInAPackage(COMMON)))
                .as("kali.*." + layer + "..");
    }

    @Test
    void 루트_패키지에는_KaliApplication만_둔다() {
        classes()
                .that()
                .resideInAPackage("kali")
                .and()
                .areTopLevelClasses()
                .should()
                .haveSimpleName("KaliApplication")
                .check(CLASSES);
    }

    @Test
    void 기능_안의_클래스는_네_레이어_중_하나에_둔다() {
        classes()
                .that()
                .resideInAPackage("kali.*..")
                .and()
                .resideOutsideOfPackage(COMMON)
                .should()
                .resideInAnyPackage("kali.*.web..", "kali.*.application..", "kali.*.domain..", "kali.*.infra..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void 레이어는_정해진_방향으로만_의존한다() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .withOptionalLayers(true)
                .layer("Web")
                .definedBy(featureLayer("web"))
                .layer("Application")
                .definedBy(featureLayer("application"))
                .layer("Domain")
                .definedBy(featureLayer("domain"))
                .layer("Infra")
                .definedBy(featureLayer("infra"))
                .whereLayer("Web")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Infra")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Application")
                .mayOnlyBeAccessedByLayers("Web", "Infra")
                .whereLayer("Domain")
                .mayOnlyBeAccessedByLayers("Web", "Application", "Infra")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void 다른_기능의_web에_의존하지_않는다() {
        slices().matching("kali.(*).web..")
                .that(FEATURE_SLICE)
                .should()
                .notDependOnEachOther()
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void 다른_기능의_infra에_의존하지_않는다() {
        slices().matching("kali.(*).infra..")
                .that(FEATURE_SLICE)
                .should()
                .notDependOnEachOther()
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void 기능_사이에_순환_의존이_없다() {
        slices().matching("kali.(*)..")
                .should()
                .beFreeOfCycles()
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void common은_기능에_의존하지_않는다() {
        noClasses()
                .that()
                .resideInAPackage(COMMON)
                .should()
                .dependOnClassesThat(resideInAPackage("kali..").and(not(resideInAPackage(COMMON))))
                .allowEmptyShould(true)
                .check(CLASSES);
    }
}
