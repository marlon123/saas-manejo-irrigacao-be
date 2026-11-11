package com.irrigation;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Regras estruturais da arquitetura em camadas (Req 4.1, 6.1).
 */
@AnalyzeClasses(packages = "com.irrigation", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsIndependentOfFrameworksAndOuterLayers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "org.hibernate..", "com.fasterxml..",
                    "..application..", "..infrastructure..", "..presentation..")
            .as("o domínio não depende de frameworks nem de camadas externas");

    @ArchTest
    static final ArchRule calculationEngineIsIndependent = noClasses()
            .that().resideInAPackage("..domain.calculation..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "org.hibernate..",
                    "..application..", "..infrastructure..", "..presentation..", "..domain.repository..")
            .as("o motor de cálculo não depende de apresentação, infraestrutura, Spring, JPA nem de persistência");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnOuterLayers = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..presentation..")
            .as("a aplicação não depende de infraestrutura nem de apresentação");

    @ArchTest
    static final ArchRule presentationDoesNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage("..presentation..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .as("a apresentação não depende da infraestrutura");

    @ArchTest
    static final ArchRule infrastructureDoesNotDependOnPresentation = noClasses()
            .that().resideInAPackage("..infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("..presentation..")
            .as("a infraestrutura não depende da apresentação");

    @ArchTest
    static final ArchRule controllersAreMappedUnderApiV1 = classes()
            .that().areAnnotatedWith(RestController.class)
            .should(new ArchCondition<JavaClass>("estar mapeados sob /api/v1") {
                @Override
                public void check(JavaClass controller, ConditionEvents events) {
                    boolean underApiV1 = controller.tryGetAnnotationOfType(RequestMapping.class)
                            .map(mapping -> mapping.value().length > 0
                                    && Arrays.stream(mapping.value()).allMatch(path -> path.startsWith("/api/v1")))
                            .orElse(false);
                    events.add(new SimpleConditionEvent(controller, underApiV1,
                            controller.getName() + (underApiV1 ? " está sob /api/v1" : " não está mapeado sob /api/v1")));
                }
            });
}
