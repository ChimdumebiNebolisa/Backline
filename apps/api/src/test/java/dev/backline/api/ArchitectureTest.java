package dev.backline.api;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "dev.backline", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule cliMustNotUseJdbc =
            noClasses().that().resideInAPackage("dev.backline.cli..").should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.jdbc..", "java.sql..", "javax.sql..", "org.springframework.data.jpa..");

    @ArchTest
    static final ArchRule reportingMustNotUsePersistence = noClasses()
            .that()
            .resideInAPackage("dev.backline.reporting..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.jdbc..", "org.springframework.data.jpa..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule workerMustNotExposeWebControllers = noClasses()
            .that()
            .resideInAPackage("dev.backline.worker..")
            .should()
            .beAnnotatedWith("org.springframework.web.bind.annotation.RestController");

    @ArchTest
    static final ArchRule controllersMustNotDependOnRepositories = noClasses()
            .that()
            .resideInAPackage("dev.backline.api.controller..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule workerMustNotDependOnApiWebLayer = noClasses()
            .that()
            .resideInAPackage("dev.backline.worker..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("dev.backline.api.controller..", "org.springframework.web.servlet..");
}
