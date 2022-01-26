package no.nav.eessi.pensjon.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import no.nav.eessi.pensjon.EessiPensjonJournalforingApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled
class ArchitectureTest {

    companion object {

        @JvmStatic
        private val root = EessiPensjonJournalforingApplication::class.qualifiedName!!
                .replace("." + EessiPensjonJournalforingApplication::class.simpleName, "")

        @JvmStatic
        lateinit var classesToAnalyze: JavaClasses

        @BeforeAll
        @JvmStatic
        fun `extract classes`() {
            classesToAnalyze = ClassFileImporter().importPackages(root)

            assertTrue(classesToAnalyze.size > 150, "Sanity check on no. of classes to analyze")
            assertTrue(classesToAnalyze.size < 800, "Sanity check on no. of classes to analyze")
        }
    }

    @Test
    fun `Packages should not have cyclic depenedencies`() {
        slices().matching("$root.(*)..").should().beFreeOfCycles().check(classesToAnalyze)
    }


    @Test
    fun `Services should not depend on eachother`() {
        slices().matching("..$root.services.(**)").should().notDependOnEachOther().check(classesToAnalyze)
    }

    @Test
    fun `Check architecture`() {
        val ROOT = "oppgave"
        val Config = "oppgave.Config"
        val Health = "oppgave.Health"
        val Listeners = "oppgave.listeners"
        val JSON = "journalforing.json"
        val Logging = "oppgave.logging"
        val Metrics = "oppgave.metrics"
        val STS = "oppgave.security.sts"
        val OppgaveService = "oppgave.services.oppgave"
        val IntegrationTest = "oppgave.integrationtest"


        val packages: Map<String, String> = mapOf(
                ROOT to root,
                Config to "$root.config",
                Health to "$root.health",
                JSON to "$root.json",
                Listeners to "$root.listeners",
                STS to "$root.security.sts",
                Logging to "$root.logging",
                Metrics to "$root.metrics",
                OppgaveService to "$root.services.oppgave",
                IntegrationTest to "$root.integrationtest"
        )

        /*
        TODO do something about the dependencies surrounding STS, but there is a bit too much black magic there for me ...
        TODO look at/refactor the relationship between journalforing.JournalpostModel and services.journalpost.JournalpostService ...
         */
        layeredArchitecture()
                //Define components
                .layer(ROOT).definedBy(packages[ROOT])
                .layer(Config).definedBy(packages[Config])
                .layer(Health).definedBy(packages[Health])
                .layer(JSON).definedBy(packages[JSON])
                .layer(Listeners).definedBy(packages[Listeners])
                .layer(Logging).definedBy(packages[Logging])
                .layer(Metrics).definedBy(packages[Metrics])
                .layer(OppgaveService).definedBy(packages[OppgaveService])
                .layer(STS).definedBy(packages[STS])
                .layer(IntegrationTest).definedBy(packages[IntegrationTest])
                //define rules
                .whereLayer(ROOT).mayNotBeAccessedByAnyLayer()
                .whereLayer(Config).mayOnlyBeAccessedByLayers(IntegrationTest)
                .whereLayer(Health).mayNotBeAccessedByAnyLayer()
                .whereLayer(STS).mayOnlyBeAccessedByLayers(Config)
                .whereLayer(Listeners).mayOnlyBeAccessedByLayers(IntegrationTest)
                .whereLayer(Logging).mayOnlyBeAccessedByLayers(Config, STS)
                .whereLayer(OppgaveService).mayOnlyBeAccessedByLayers(Listeners)
                //Verify rules
                .check(classesToAnalyze)
    }

    @Test
    fun `avoid JUnit4-classes`() {
        val junitReason = "We use JUnit5 (but had to include JUnit4 because spring-kafka-test needs it to compile)"

        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.junit",
                        "org.junit.runners",
                        "org.junit.experimental..",
                        "org.junit.function",
                        "org.junit.matchers",
                        "org.junit.rules",
                        "org.junit.runner..",
                        "org.junit.validator",
                        "junit.framework.."
                ).because(junitReason)
                .check(classesToAnalyze)

                noClasses()
                        .should()
                        .beAnnotatedWith("org.junit.runner.RunWith")
                        .because(junitReason)
                        .check(classesToAnalyze)

                noMethods()
                        .should()
                        .beAnnotatedWith("org.junit.Test")
                        .orShould().beAnnotatedWith("org.junit.Ignore")
                        .because(junitReason)
                        .check(classesToAnalyze)
    }
}
