package me.veselin.probity.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * Architectural tests to enforce hexagonal architecture boundaries.
 */
class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("me.veselin.probity");

    @Test
    void domainClassesShouldNotImportJpa() {
        // Only apply to newly split domain classes (Portfolio, PortfolioPosition, Simulation)
        // Not to existing entities like User, Asset, PriceBar
        ArchRule rule = noClasses()
                .that().resideInAPackage("..portfolio.domain..")
                .and().haveSimpleName("Portfolio")
                .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..");

        rule.check(classes);

        ArchRule rule2 = noClasses()
                .that().resideInAPackage("..portfolio.domain..")
                .and().haveSimpleName("PortfolioPosition")
                .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..");

        rule2.check(classes);

        ArchRule rule3 = noClasses()
                .that().resideInAPackage("..simulation.domain..")
                .and().haveSimpleName("Simulation")
                .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..");

        rule3.check(classes);
    }

    @Test
    void portsShouldNotReferenceBffDtos() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..port..")
                .should().dependOnClassesThat().resideInAPackage("..bff.dto..");

        rule.check(classes);
    }

    @Test
    void riskPackageShouldNotReferencePortfolioDomainDirectly() {
        // Risk package should go through RiskPort, not directly access portfolio.domain types
        ArchRule rule = noClasses()
                .that().resideInAPackage("..risk..")
                .should().dependOnClassesThat().resideInAPackage("..portfolio.domain..");

        rule.check(classes);
    }
}
