package no.nav.eessi.pensjon.services

import no.nav.eessi.pensjon.models.Oppgave
import no.nav.eessi.pensjon.models.Prioritet
import no.nav.eessi.pensjon.services.gcp.GcpStorageService
import no.nav.eessi.pensjon.services.saf.Journalstatus
import no.nav.eessi.pensjon.services.saf.SafClient
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

private const val X_REQUEST_ID = "x_request_id"
@Component
class OppgaveForJournalpost(
    private val gcpStorageService: GcpStorageService,
    private val safClient: SafClient,
    private val oppgaveService: OppgaveService,
    @Autowired private val env: Environment,
) {

    private val logger = LoggerFactory.getLogger(OppgaveService::class.java)

    init {
        MDC.putCloseable(X_REQUEST_ID, UUID.randomUUID().toString()).use {
            val journalposterSomIkkeBleBehandlet = if (env.activeProfiles[0] == "test") {
                lagOppgaveForJournalpost(JournalposterSomInneholderFeil.feilendeJournalposterTest())
            } else if (env.activeProfiles[0] == "prod") {
                logger.info("Sjekker ${JournalposterSomInneholderFeil.feilendeJournalposterProd().size} journalposter som ikke er prossesert")
                lagOppgaveForJournalpost(JournalposterSomInneholderFeil.feilendeJournalposterProd())
            } else {
                emptyList()
            }
            logger.warn("Det ble laget oppgave på journalpostene: ${journalposterSomIkkeBleBehandlet.toJson()}")
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
                val oppgaveMelding = oppgaveService.hentOppgave(journalpostId).also { logger.info("Oppgave \n" + it?.toJson()) }

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
                        oppgaveService.opprettOppgaveSendOppgaveInn(oppgave)
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
        return (journalpost.journalstatus == Journalstatus.UNDER_ARBEID).also { logger.warn("Journalposten finnes, men har status: ${journalpost.journalstatus}") }
    }
}