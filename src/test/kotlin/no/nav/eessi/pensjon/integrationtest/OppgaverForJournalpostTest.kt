package no.nav.eessi.pensjon.integrationtest

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.services.saf.SafClient
import no.nav.eessi.pensjon.models.BehandlingTema
import no.nav.eessi.pensjon.models.OppgaveResponse
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.services.JournalposterSomInneholderFeil
import no.nav.eessi.pensjon.services.OppgaveForJournalpost
import no.nav.eessi.pensjon.services.OppgaveService
import no.nav.eessi.pensjon.services.gcp.GcpStorageService
import no.nav.eessi.pensjon.services.saf.Journalpost
import no.nav.eessi.pensjon.services.saf.Journalstatus
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime

class OppgaverForJournalpostTest {

    private val gcpStorageService = mockk<GcpStorageService>()
    private val safClient =  mockk<SafClient>()

    lateinit var oppgaveService: OppgaveService
    lateinit var oppgaveForJournalpost: OppgaveForJournalpost
    var oppgaveOAuthRestTemplate: RestTemplate = mockk(relaxed = true)

    val listAppender = ListAppender<ILoggingEvent>()
    val logger: Logger = LoggerFactory.getLogger("no.nav.eessi") as Logger

    @BeforeEach
    fun setup() {
        listAppender.start()
        logger.addAppender(listAppender)
        oppgaveService = OppgaveService(oppgaveOAuthRestTemplate)
        oppgaveForJournalpost = OppgaveForJournalpost(
            gcpStorageService,
            safClient,
            oppgaveService,
            mockk<Environment>().apply { every { activeProfiles } returns arrayOf("noe annet") })
    }

    @Test
    fun `Gitt at vi har en ferdigstilt oppgave paa en journalpost som er i status D saa skal vi opprette en ny oppgave på samme journalpost`() {

        // henter listen med journalpostIDer fra gcp
        val journalpostIds = JournalposterSomInneholderFeil.feilendeJournalposterTest()
        justRun { gcpStorageService.lagre(any(), any()) }

        journalpostIds.forEach { id ->

            // sjekker om den er ferdigstilt (sjekker mot joark)
            every { safClient.hentJournalpost(any()) } returns journalpostResponse(id)
            every { gcpStorageService.journalpostenErIkkeLagret(id) } returns true
            every {
                oppgaveOAuthRestTemplate.getForEntity("/api/v1/oppgaver?statuskategori=AVSLUTTET&journalpostId=${id}", String::class.java )
            } returns ResponseEntity( lagOppgaveRespons(journalpostIds.first()), HttpStatus.OK )
        }

        // kaller oppgave for å hente inn oppgaven, opprette ny oppgave med samme journalpostid
        val ferdigBehandledeJournalposter = oppgaveForJournalpost.lagOppgaveForJournalpost(JournalposterSomInneholderFeil.feilendeJournalposterTest())

        verify(exactly = 2) {
            oppgaveOAuthRestTemplate.exchange( "/", HttpMethod.POST, any(), String::class.java )
        }

        // har kun sjekket og kjørt en av oppgavene
        assertEquals(ferdigBehandledeJournalposter.size, 2)
        assertEquals(ferdigBehandledeJournalposter[0], journalpostIds[0])
    }

    private fun journalpostResponse(journalpostIds: String): Journalpost {
        val journalpostResponse = Journalpost(
            journalpostId =  journalpostIds,
            bruker = null,
            tema = Tema.OMSTILLING,
            journalstatus = Journalstatus.UNDER_ARBEID,
            behandlingstema = BehandlingTema.UFOREPENSJON,
            journalforendeEnhet = "4303",
            datoOpprettet = LocalDateTime.now()
        )
        return journalpostResponse
    }

    private fun lagOppgaveRespons(journalpostIds: String): String {
        val mockOppgave = """
            {
              "antallTreffTotalt":1,
              "oppgaver":[
                {
                  "id":195060,
                  "tildeltEnhetsnr":"4303",
                  "endretAvEnhetsnr":"2012",
                  "opprettetAvEnhetsnr":"9999",
                  "journalpostId":"$journalpostIds",
                  "aktoerId":"2356709109499",
                  "beskrivelse":"Utgående P9000 - Svar på forespørsel om informasjon / Rina saksnr: 1447748",
                  "tema":"PEN",
                  "oppgavetype":"JFR",
                  "versjon":2,
                  "opprettetAv":"eessi-pensjon-oppgave-q2",
                  "endretAv":"Z990724",
                  "prioritet":"NORM",
                  "status":"FERDIGSTILT",
                  "metadata":{
                  },
                  "fristFerdigstillelse":"2024-03-12",
                  "aktivDato":"2024-03-11",
                  "opprettetTidspunkt":"2024-03-11T15:17:59.963+01:00",
                  "ferdigstiltTidspunkt":"2024-03-12T08:27:54.949+01:00",
                  "endretTidspunkt":"2024-03-12T08:27:54.949+01:00"
                }
              ]
            }
            """.trimIndent()
        return mapJsonToAny<OppgaveResponse>(mockOppgave).toJson()
    }
}