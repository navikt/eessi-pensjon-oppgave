package no.nav.eessi.pensjon.services

import io.micrometer.core.instrument.Metrics
import jakarta.annotation.PostConstruct
import no.nav.eessi.pensjon.services.saf.SafClient
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.Oppgave
import no.nav.eessi.pensjon.models.Prioritet
import no.nav.eessi.pensjon.services.gcp.GcpStorageService
import no.nav.eessi.pensjon.services.saf.Journalstatus
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
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
        logger.info("Sjekker om det er journalposter som ikke er prossesert")
        val journalposterSomIkkeBleBehandlet = lagOppgaveForJournalpost()
        if (journalposterSomIkkeBleBehandlet.isNotEmpty()) {
            logger.warn("Det ble ikke laget oppgave på journalpostene: ${journalposterSomIkkeBleBehandlet.toJson()}")
        }
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

    private fun hentOppgave(journalpostId: String): Oppgave {
        //TODO kalle oppgave for å hente inn oppgave vhja journalpostId
        try {
            return oppgaveOAuthRestTemplate.getForEntity("/api/v1/oppgaver?statuskategori=AVSLUTTET&journalpostId=$journalpostId", String::class.java)
                .also { logger.info("Hentet oppgave for journalpostId: $journalpostId") }.body?.let { mapJsonToAny(it) }
                    ?: throw RuntimeException("Feil ved henting av oppgave for journalpostId: $journalpostId")
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
    final fun lagOppgaveForJournalpost(): List<String> {
        val journalpostIds = gcpStorageService.hentJournalpostFilfraS3()?.split(",")
        journalpostIds
            ?.forEach { journalpostId ->
                logger.info("Sjekker journalpost: $journalpostId")
                if (erJournalpostenFerdigstilt(journalpostId)) {
                    val oppgaveMelding = hentOppgave(journalpostId)
                    if (oppgaveMelding.status == "FERDIGSTILT") {
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
                        if (env.activeProfiles[0] == "test") {
                            opprettOppgaveSendOppgaveInn(oppgave)
                        }
                        logger.info("Journalposten $journalpostId har en ferdigstilt oppgave" + oppgave.toJson())
                        journalpostIds.removeFirst()
                    }
                }
            }
        return journalpostIds ?: emptyList()
    }

    private fun erJournalpostenFerdigstilt(journalpostId: String): Boolean {
        val jp = safClient.hentJournalpost(journalpostId)
        if (jp != null && jp.journalstatus == Journalstatus.UNDER_ARBEID && jp.journalpostferdigstilt == true) {
            logger.error("Journalposten $journalpostId finnes ikke i Joark")
            return true
        }
        return false
    }

    fun countEnthet(tildeltEnhetsnr: String?) {
        try {
            Metrics.counter("TildeltEnhet",   "enhet", tildeltEnhetsnr).increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet på enhet: $tildeltEnhetsnr")
        }
    }
}

