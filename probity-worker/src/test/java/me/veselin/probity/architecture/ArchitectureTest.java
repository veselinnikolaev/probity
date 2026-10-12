package me.veselin.probity.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural rules for the worker module.
 *
 * <p>The worker hosts only the shared-kernel slices it needs — marketdata, portfolio,
 * risk, simulation, common — and the api's presentation surfaces (bff, security, web,
 * auth) must never be reachable from it. The worker's test classpath does not pull the
 * api module at all, so these rules pass today because the packages are simply absent;
 * they make the boundary explicit and regression-proof rather than incidental.
 *
 * <p>The worker legitimately uses {@code spring-security-core}'s
 * {@code AccessDeniedException} to guard portfolio ownership, but never the servlet
 * security stack, which stays in the api.
 */
class ArchitectureTest {

    private static final String[] API_CONCERN_PACKAGES = {
            "..probity.bff..",
            "..probity.security..",
            "..probity.web..",
            "..probity.auth..",
    };

    private final JavaClasses classes = new ClassFileImporter().importPackages("me.veselin.probity");

    @Test
    void workerClassesMustNotDependOnApiPresentationPackages() {
        for (String apiConcern : API_CONCERN_PACKAGES) {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("me.veselin.probity..")
                    .should().dependOnClassesThat().resideInAPackage(apiConcern);
            rule.check(classes);
        }
    }

    @Test
    void workerMustNotPullInTheServletSecurityStack() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("me.veselin.probity..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework.security.web..");
        rule.check(classes);
    }
}