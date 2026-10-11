package net.linlan.dsh.cases.orchestration;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class CaseOrchestrationArchitectureTest {

    @Test
    void factoriesMustExposeUnifiedPipelines() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh.cases");

        ArchRuleDefinition.classes()
                .that().haveSimpleNameEndingWith("Factory")
                .and().resideInAPackage("..cases..factory..")
                .should().implement(CasePipelineFactory.class)
                .check(classes);
    }

    @Test
    void nodesMustUseStrategyRouter() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh.cases");

        ArchRuleDefinition.classes()
                .that().haveSimpleNameEndingWith("Node")
                .and().resideInAPackage("..cases..node..")
                .should().beAssignableTo(AbstractStrategyRouter.class)
                .check(classes);
    }

    @Test
    void caseImplsMustDelegateThroughFactories() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh.cases");

        ArchRuleDefinition.classes()
                .that().haveSimpleNameEndingWith("CaseImpl")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Factory")
                .check(classes);
    }
}
