package no.nav.eessi.pensjon.services

import io.micrometer.core.instrument.Metrics
import jakarta.annotation.PostConstruct
import no.nav.eessi.pensjon.services.saf.SafClient
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.Oppgave
import no.nav.eessi.pensjon.models.OppgaveResponse
import no.nav.eessi.pensjon.models.Prioritet
import no.nav.eessi.pensjon.services.JournalposterSomInneholderFeil.Companion.feilendeJournalposterTest
import no.nav.eessi.pensjon.services.gcp.GcpStorageService
import no.nav.eessi.pensjon.services.saf.Journalstatus
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Service
class OppgaveService(
    private val oppgaveOAuthRestTemplate: RestTemplate,
    private val gcpStorageService: GcpStorageService,
    private val safClient: SafClient,
    @Autowired private val env: Environment,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger = LoggerFactory.getLogger(OppgaveService::class.java)
    private lateinit var opprettoppgave: MetricsHelper.Metric
    init {
        opprettoppgave = metricsHelper.init("opprettoppgave")
    }

    @PostConstruct
    fun startJournalpostAnalyse(){

        val journalposterSomIkkeBleBehandlet = if (env.activeProfiles[0] == "test") {
            lagOppgaveForJournalpost(feilendeJournalposterTest())
        } else {
            logger.info("Sjekker ${JournalposterSomInneholderFeil.feilendeJournalposterProd().size} journalposter som ikke er prossesert")
            lagOppgaveForJournalpost(JournalposterSomInneholderFeil.feilendeJournalposterProd())
        }
        logger.warn("Det ble laget oppgave på journalpostene: ${journalposterSomIkkeBleBehandlet.toJson()}")
    }

    // https://oppgave.nais.preprod.local/?url=https://oppgave.nais.preprod.local/api/swagger.json#/v1oppgaver/opprettOppgave
    fun opprettOppgaveSendOppgaveInn(oppgave: Oppgave) {
        opprettoppgave.measure {

            try {
                val requestBody = mapAnyToJson(oppgave, true)
                logger.info("Oppretter oppgave: $requestBody")

                countEnthet(oppgave.tildeltEnhetsnr)

                val httpEntity = HttpEntity(requestBody)
                oppgaveOAuthRestTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java)

                logger.info("Opprettet oppgave av type ${oppgave.oppgavetype} med tildeltEnhetsnr:  ${oppgave.tildeltEnhetsnr}")
            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under opprettelse av oppgave ex: $ex body: ${ex.responseBodyAsString}")
                throw RuntimeException("En feil oppstod under opprettelse av oppgave ex: ${ex.message} body: ${ex.responseBodyAsString}", ex)
            } catch(ex: Exception) {
                logger.error("En feil oppstod under opprettelse av oppgave ex: $ex")
                throw RuntimeException("En feil oppstod under opprettelse av oppgave ex: ${ex.message}", ex)
            }
        }
    }

    private fun hentOppgave(journalpostId: String): Oppgave? {
        //TODO kalle oppgave for å hente inn oppgave vhja journalpostId
        try {
            val oppgaveResponse = oppgaveOAuthRestTemplate.getForEntity("/api/v1/oppgaver?statuskategori=AVSLUTTET&journalpostId=$journalpostId", String::class.java).body?.let { it ->
                mapJsonToAny<OppgaveResponse>(it).also {
                    logger.info("Hentet oppgave for journalpostId, antall treff: ${it.antallTreffTotalt}") }
            }
            return oppgaveResponse?.oppgaver?.firstOrNull().also { logger.debug("Hentet oppgave for journalpostId: $journalpostId, oppgave: ${it?.toJson()}")}

        } catch (ex: Exception) {
            logger.error("En feil oppstod under henting av oppgave", ex)
            throw RuntimeException(ex)
        }
    }

    /**
     * Skal opprette oppgaver på alle journalposter som er ferdigstilt og har en oppgave som er avsluttet
     * Hente liste over journalposter som er under arbeid, men har avsluttede oppgaver på seg, fra gcpStorage
     * Kalle Joark for å hente journalpostene
     * Sjekke om oppgavene på journalpostene er ferdigstilt
     * kalle oppgave for å hente inn oppgaven ved hjelp av journalpostIden
     * Opprette nye oppgaver på journalpostene
     */
    final fun lagOppgaveForJournalpost(feilendeJournalposter: List<String>): List<String> {
        val ferdigBehandledeJournalposter = ArrayList<String>()
        feilendeJournalposter
            .forEach { journalpostId ->
                logger.info("Sjekker journalpost: $journalpostId")
                // ser om vi allerede har laget en oppgave på denne journalpoosten
                val oppgaveErIkkeOpprettet = gcpStorageService.journalpostenErIkkeLagret(journalpostId)
                val oppgaveMelding = hentOppgave(journalpostId).also { logger.info("Oppgave \n" + it?.toJson()) }

                if (oppgaveErIkkeOpprettet && erJournalpostenUnderArbeid(journalpostId)) {
                    if (oppgaveMelding?.status == "FERDIGSTILT") {
                        val oppgave = Oppgave(
                            oppgavetype = "JFR",
                            tema = oppgaveMelding.tema,
                            prioritet = Prioritet.NORM.toString(),
                            aktoerId = oppgaveMelding.aktoerId,
                            aktivDato = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
                            journalpostId = oppgaveMelding.journalpostId,
                            opprettetAvEnhetsnr = "9999",
                            tildeltEnhetsnr = oppgaveMelding.tildeltEnhetsnr,
                            fristFerdigstillelse = LocalDate.now().plusDays(1).toString(),
                            beskrivelse = oppgaveMelding.beskrivelse
                        )
                        opprettOppgaveSendOppgaveInn(oppgave)
                        gcpStorageService.lagre(journalpostId, oppgave.toJsonSkipEmpty())
                        ferdigBehandledeJournalposter.add(journalpostId)
                        logger.info("Journalposten $journalpostId har en ferdigstilt oppgave" + oppgave.toJson())
                    }
                }
            }
        return ferdigBehandledeJournalposter
    }

    private fun erJournalpostenUnderArbeid(journalpostId: String): Boolean {
        val journalpost = safClient.hentJournalpost(journalpostId)
        if (journalpost == null) {
            logger.error("Journalposten $journalpostId finnes ikke i Joark")
            return false
        }

        logger.info(journalpost.toJson())
        return journalpost.journalstatus == Journalstatus.UNDER_ARBEID
    }

    fun countEnthet(tildeltEnhetsnr: String?) {
        try {
            Metrics.counter("TildeltEnhet",   "enhet", tildeltEnhetsnr).increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet på enhet: $tildeltEnhetsnr")
        }
    }
}

