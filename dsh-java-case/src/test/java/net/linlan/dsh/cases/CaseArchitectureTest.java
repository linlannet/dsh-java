package net.linlan.dsh.cases;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class CaseArchitectureTest {

    @Test
    void casesMustNotDependOnInfrastructure() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("net.linlan.dsh.cases..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "net.linlan.dsh.infrastructure.."
                )
                .check(classes);
    }

    @Test
    void casesMustNotDeclareBusinessObjects() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh.cases");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("net.linlan.dsh.cases..")
                .and().doNotHaveFullyQualifiedName(
                        "net.linlan.dsh.cases.plugin.lifecycle.PluginRuntimeCommand")
                .should().haveSimpleNameEndingWith("Command")
                .orShould().haveSimpleNameEndingWith("Result")
                .orShould().haveSimpleNameEndingWith("View")
                .orShould().haveSimpleNameEndingWith("Query")
                .check(classes);
    }
}
