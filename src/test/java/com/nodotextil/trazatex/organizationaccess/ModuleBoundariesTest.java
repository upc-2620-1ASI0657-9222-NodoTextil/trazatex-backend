package com.nodotextil.trazatex.organizationaccess;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

import org.junit.jupiter.api.Test;


@AnalyzeClasses(packages = "com.nodotextil.trazatex",
		importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

	private static final String MODULE = "com.nodotextil.trazatex.organizationaccess..";

	private static final DescribedPredicate<JavaClass> MODULE_INTERNALS = resideInAPackage(MODULE)
			.and(not(resideInAnyPackage("..organizationaccess.application.contract..",
					"..organizationaccess.application.event..")))
			.as("internals of the organizationaccess module "
					+ "(everything but application.contract and application.event)");

	private static final DescribedPredicate<JavaClass> EXTERNAL_INTEGRATIONS = resideInAnyPackage(
			"..organizationaccess.infrastructure.external..")
			.or(JavaClass.Predicates.simpleNameEndingWith("TaxpayerValidationPort"))
			.or(JavaClass.Predicates.simpleNameEndingWith("CompromisedPasswordPort"))
			.as("the SUNAT and HIBP adapters and their ports");

	static final ArchRule ONLY_CONTRACT_AND_EVENTS_ARE_PUBLIC = noClasses().that()
			.resideOutsideOfPackage(MODULE).should().dependOnClassesThat(MODULE_INTERNALS)
			.because("other modules talk to this one only through application.contract "
					+ "and application.event");

	static final ArchRule SUNAT_AND_HIBP_STAY_INSIDE = noClasses().that()
			.resideOutsideOfPackage(MODULE).should().dependOnClassesThat(EXTERNAL_INTEGRATIONS)
			.because("SUNAT and Have I Been Pwned are only used by Organization & Access");

	@ArchTest
	static final ArchRule rule1 = ONLY_CONTRACT_AND_EVENTS_ARE_PUBLIC;

	@ArchTest
	static final ArchRule rule2 = SUNAT_AND_HIBP_STAY_INSIDE;

	
	@Test
	void theRulesDoDetectAnOutsiderUsingModuleInternals() {
		
		
		JavaClasses withTests = new ClassFileImporter().importPackages(
				"com.nodotextil.trazatex.shared.security",
				"com.nodotextil.trazatex.organizationaccess.infrastructure.security");

		EvaluationResult result = ONLY_CONTRACT_AND_EVENTS_ARE_PUBLIC.evaluate(withTests);

		assertThat(result.hasViolation()).isTrue();
	}

	@Test
	void theExternalIntegrationsRuleRecognizesTheAdaptersAndPorts() {
		JavaClasses classes = new ClassFileImporter().importPackages(
				"com.nodotextil.trazatex.organizationaccess.infrastructure.external",
				"com.nodotextil.trazatex.organizationaccess.application.port");

		assertThat(classes.stream().filter(EXTERNAL_INTEGRATIONS::test)
				.map(JavaClass::getSimpleName)).contains("SunatTaxpayerValidationAdapter",
						"HibpCompromisedPasswordAdapter", "TaxpayerValidationPort",
						"CompromisedPasswordPort");
	}
}
