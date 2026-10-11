package net.linlan.dsh.trigger;

import net.linlan.dsh.api.response.Response;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.junit.jupiter.api.Test;

class TriggerArchitectureTest {

    @Test
    void triggerMustOnlyDependOnApiAndCase() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh");

        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("net.linlan.dsh.trigger..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "net.linlan.dsh.domain..",
                        "net.linlan.dsh.infrastructure.."
                )
                .check(classes);
    }

    @Test
    void triggerFacadeMethodsMustReturnResponseWrapper() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh");

        ArchRuleDefinition.methods()
                .that().areDeclaredInClassesThat()
                .resideInAnyPackage(
                        "net.linlan.dsh.trigger.service",
                        "net.linlan.dsh.trigger.service.command",
                        "net.linlan.dsh.trigger.service.query"
                )
                .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Api")
                .and().arePublic()
                .should().haveRawReturnType(Response.class)
                .check(classes);
    }

    @Test
    void triggerHttpMethodsMustReturnResponseWrapperOrSse() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("net.linlan.dsh");

        ArchRuleDefinition.methods()
                .that().arePublic().and().areDeclaredInClassesThat()
                .resideInAnyPackage(
                        "net.linlan.dsh.trigger.http",
                        "net.linlan.dsh.trigger.http.command",
                        "net.linlan.dsh.trigger.http.query"
                )
                .should(new ArchCondition<>("return Response or SseEmitter") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        String returnType = method.getRawReturnType().getName();
                        boolean allowed = returnType.equals(Response.class.getName())
                                || returnType.equals(SseEmitter.class.getName());
                        events.add(SimpleConditionEvent.satisfied(
                                allowed,
                                method.getFullName() + (allowed ? " is allowed" : " is not allowed")
                        ));
                    }
                })
                .check(classes);
    }
}
