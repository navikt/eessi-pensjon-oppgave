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
    var oppgaveOAuthRestTemplate: RestTemplate = mockk()

    val listAppender = ListAppender<ILoggingEvent>()
    val logger: Logger = LoggerFactory.getLogger("no.nav.eessi") as Logger
    val feilendeJournalposter = listOf("12345")

    @BeforeEach
    fun setup() {
        listAppender.start()
        logger.addAppender(listAppender)
        oppgaveService = OppgaveService(oppgaveOAuthRestTemplate)
        oppgaveForJournalpost = OppgaveForJournalpost(
            gcpStorageService,
            safClient,
            oppgaveService)

        justRun { gcpStorageService.lagre(any(), any()) }
        feilendeJournalposter.forEach { id ->
            every { safClient.hentJournalpost(any()) } returns journalpostResponse(id)
            every { gcpStorageService.journalpostenErIkkeLagret(id) } returns true
            every {
                oppgaveOAuthRestTemplate.getForEntity("/api/v1/oppgaver?statuskategori=AVSLUTTET&journalpostId=$id", String::class.java )
            } returns ResponseEntity( lagOppgaveRespons(id), HttpStatus.OK )
            every {
                oppgaveOAuthRestTemplate.exchange( "/api/v1/oppgaver", HttpMethod.POST, any(), String::class.java )
            } returns ResponseEntity( HttpStatus.OK )
        }
    }

    @Test
    fun `Gitt at vi har en ferdigstilt oppgave paa en journalpost som er i status D saa skal vi opprette en ny oppgave på samme journalpost`() {

        oppgaveForJournalpost.lagOppgaveForJournalpost(feilendeJournalposter).also {
            assertEquals(it.size, 1)
            assertEquals(it[0], feilendeJournalposter[0])
        }

        verify(exactly = 1) {
            oppgaveOAuthRestTemplate.exchange( "/api/v1/oppgaver", HttpMethod.POST,  match {
                it.body == """
                    {
                      "tildeltEnhetsnr" : "4303",
                      "opprettetAvEnhetsnr" : "9999",
                      "journalpostId" : "12345",
                      "aktoerId" : "2356709109499",
                      "beskrivelse" : "Utgående P9000 - Svar på forespørsel om informasjon / Rina saksnr: 1447748",
                      "tema" : "PEN",
                      "oppgavetype" : "JFR",
                      "prioritet" : "NORM",
                      "fristFerdigstillelse" : "2024-03-20",
                      "aktivDato" : "2024-03-19"
                    }
                """.trimIndent()
            }, String::class.java )
        }
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