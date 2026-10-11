package net.linlan.dsh.domain.core.scope;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class ScopeArchitectureTest {

    @Test
    void coreScopeMustNotDependOnInfrastructure() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh.domain.core.scope");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("net.linlan.dsh.domain.core.scope..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "net.linlan.dsh.infrastructure..")
                .check(classes);
    }

    @Test
    void coreScopeMustNotDependOnSpring() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh.domain.core.scope");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("net.linlan.dsh.domain.core.scope..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                .check(classes);
    }
}
