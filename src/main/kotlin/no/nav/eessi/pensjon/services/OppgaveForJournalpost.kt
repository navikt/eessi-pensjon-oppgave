package no.nav.eessi.pensjon.services

import no.nav.eessi.pensjon.models.Oppgave
import no.nav.eessi.pensjon.models.Prioritet
import no.nav.eessi.pensjon.services.gcp.GcpStorageService
import no.nav.eessi.pensjon.services.saf.Journalpost
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

@Component
class OppgaveForJournalpost(
    private val gcpStorageService: GcpStorageService,
    private val safClient: SafClient,
    private val oppgaveService: OppgaveService,
    @Autowired private val env: Environment
) {

    private val logger = LoggerFactory.getLogger(OppgaveService::class.java)

    init {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            if (env.activeProfiles[0] == "prod") {
                try {
                    logger.info("Oppretter nye oppgaver")
                    val oppgaverStream = this::class.java.classLoader.getResourceAsStream("oppgaver.json")
                    val listOfLines = oppgaverStream?.bufferedReader()?.use { it.readLines() }

                    listOfLines?.also {
                        lagOppgaveForJournalpost(it)
                        logger.info("Det ble prosessert ${it.size} nye oppgaver")
                    }
                } catch (e: Exception) {
                    logger.error("Uthenting av oppgaver feilet", e)
                }
            }
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
                if (!gcpStorageService.journalpostenErIkkeLagret(journalpostId)) {
                    logger.warn("Oppgaven er lagret: $journalpostId")
                    return@forEach
                }

                // journalposten må være under arbeid
                val journalpost = hentJournalposten(journalpostId)
                if (journalpost?.journalstatus != Journalstatus.UNDER_ARBEID) {
                    logger.warn("Journalposten er ikke under arbeid: $journalpostId")
                    return@forEach
                }
                oppgaveService.hentAvsluttetOppgave(journalpostId)?.also { oppgaveMelding ->
                    if (oppgaveMelding.tema != journalpost.tema?.kode) {
                        logger.warn("Temaet på oppgaven ${oppgaveMelding.tema} er forskjellig fra tema på ${journalpost.tema?.kode} på journalpostId: $journalpostId")
                        return@forEach
                    }

                    val oppgType = if (oppgaveMelding.oppgavetype == "JOURNALFORING_UT" || oppgaveMelding.beskrivelse?.contains("Utg") == true) "JFR_UT" else "JFR"

                    if (oppgaveMelding.status == "FERDIGSTILT") {
                        Oppgave(
                            oppgavetype = oppgType,
                            tema = oppgaveMelding.tema,
                            prioritet = Prioritet.NORM.toString(),
                            aktoerId = oppgaveMelding.aktoerId,
                            aktivDato = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
                            journalpostId = oppgaveMelding.journalpostId,
                            opprettetAvEnhetsnr = "9999",
                            tildeltEnhetsnr = oppgaveMelding.tildeltEnhetsnr,
                            fristFerdigstillelse = LocalDate.now().plusDays(1).toString(),
                            beskrivelse = oppgaveMelding.beskrivelse)
                        .also { oppgave ->
                            oppgaveService.opprettOppgaveSendOppgaveInn(oppgave)
                            gcpStorageService.lagre(journalpostId, oppgave.toJsonSkipEmpty())
                            ferdigBehandledeJournalposter.add(journalpostId)
                            logger.info("Journalposten $journalpostId har en ferdigstilt oppgave${oppgave.toJson()}")
                        }
                    } else {
                        logger.warn("Oppgaven er ikke ferdigstilt: $journalpostId")
                    }
                } ?: logger.warn("Ingen oppgave funnet for journalpostId: $journalpostId")
            }
        return ferdigBehandledeJournalposter
    }

    private fun hentJournalposten(journalpostId: String): Journalpost? {
        val journalpost = safClient.hentJournalpost(journalpostId)
        if (journalpost == null) {
            logger.error("Journalposten $journalpostId finnes ikke i Joark")
            return null
        }

        logger.info(journalpost.toJson())
        return journalpost.also { logger.warn("Journalposten finnes, og har status: ${journalpost.journalstatus}") }
    }
}