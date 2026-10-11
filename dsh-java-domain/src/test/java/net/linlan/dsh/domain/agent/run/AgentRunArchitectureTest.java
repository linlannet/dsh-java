package net.linlan.dsh.domain.agent.run;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class AgentRunArchitectureTest {

    @Test
    void domainMustNotDependOnInfrastructure() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("net.linlan.dsh.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "net.linlan.dsh.infrastructure..")
                .check(classes);
    }

    @Test
    void agentDomainMustNotDependOnSpring() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh.domain.agent");

        ArchRuleDefinition.noClasses()
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                .check(classes);
    }

    @Test
    void agentDomainMustNotDependOnJackson() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh.domain.agent");

        ArchRuleDefinition.noClasses()
                .should().dependOnClassesThat().resideInAnyPackage("com.fasterxml..")
                .check(classes);
    }
}
