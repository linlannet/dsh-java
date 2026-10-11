package net.linlan.dsh.api;

import net.linlan.dsh.api.response.Response;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ApiArchitectureTest {

    @Test
    void apiMustNotDependOnDomainOrInfrastructure() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("net.linlan.dsh.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "net.linlan.dsh.domain..",
                        "net.linlan.dsh.infrastructure.."
                )
                .check(classes);
    }

    @Test
    void apiContractsMustUseDtoNaming() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh.api");

        ArchRuleDefinition.classes()
                .that().resideInAPackage("net.linlan.dsh.api.dto")
                .should().haveSimpleNameEndingWith("DTO")
                .check(classes);
    }

    @Test
    void apiResponseMustOnlyContainWrappers() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh.api");

        ArchRuleDefinition.classes()
                .that().resideInAPackage("net.linlan.dsh.api.response")
                .should().haveSimpleName("Response")
                .orShould().haveSimpleName("ResponsePage")
                .check(classes);
    }

    @Test
    void apiInterfaceMethodsMustReturnResponseWrapper() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh.api");

        ArchRuleDefinition.methods()
                .that().areDeclaredInClassesThat().resideInAPackage("net.linlan.dsh.api")
                .should().haveRawReturnType(Response.class)
                .check(classes);
    }

    @Test
    void streamContractsMustReturnSseEmitter() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh.api.stream");

        ArchRuleDefinition.methods()
                .that().areDeclaredInClassesThat()
                .resideInAPackage("net.linlan.dsh.api.stream")
                .should().haveRawReturnType(SseEmitter.class)
                .check(classes);
    }
}
