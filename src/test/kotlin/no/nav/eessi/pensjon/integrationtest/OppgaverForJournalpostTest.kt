package no.nav.eessi.pensjon.integrationtest

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.journalforing.saf.SafClient
import no.nav.eessi.pensjon.models.JournalpostResponse
import no.nav.eessi.pensjon.models.Journalstatus
import no.nav.eessi.pensjon.services.Oppgave
import no.nav.eessi.pensjon.services.OppgaveService
import no.nav.eessi.pensjon.services.gcp.GcpStorageService
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime

private const val OPPGAVE_TOPIC = "privat-eessipensjon-oppgave-v1-test"

class OppgaverForJournalpostTest {

    private val gcpStorageService = mockk<GcpStorageService>()
    private val safClient =  mockk<SafClient>()

    lateinit var oppgaveService: OppgaveService
    var oppgaveOAuthRestTemplate: RestTemplate = mockk(relaxed = true)

    val listAppender = ListAppender<ILoggingEvent>()
    val logger: Logger = LoggerFactory.getLogger("no.nav.eessi") as Logger

    @BeforeEach
    fun setup() {
        listAppender.start()
        logger.addAppender(listAppender)
        oppgaveService = OppgaveService(oppgaveOAuthRestTemplate, gcpStorageService, safClient)
    }

    @Test
    fun `gitt at vi har en ferdigstilt oppgave på en journalpost som er i status D så skal vi opprette en ny oppgave på samme journalpost`() {

        //TODO: Hente listen fra gcp
        every { gcpStorageService.hentJournalpostFilfraS3() } returns """
            645601988,
            645950501
        """.trimIndent()

        //TODO: Kalle joark for å sjekke oppgavestatus (verifisering) sjekker om faktisk status på oppgacve er D

        val journalpostId1 = "645601988"
        val journalpostResponse = JournalpostResponse(
            journalpostId1,
            Oppgave.Tema.PENSJON,
            Journalstatus.UNDER_ARBEID,
            true,
            null,
            Oppgave.Behandlingstema.UFORE_UTLAND,
            "4303",
            "Alderspensjon",
            null,
            LocalDateTime.now()
        )

        //TODO: Sjekk om den er ferdigstilt (sjekker mot joark)
        every { safClient.hentJournalpost(eq("645601988")) } returns journalpostResponse

        val mockOppgave = """
            {
              "id": 192136,
              "tildeltEnhetsnr": "4303",
              "opprettetAvEnhetsnr": "9999",
              "journalpostId": "645601988",
              "beskrivelse": "Inngående P8000 - Forespørsel om informasjon / Rina saksnr: 1447360",
              "tema": "PEN",
              "oppgavetype": "JFR",
              "versjon": 1,
              "opprettetAv": "eessi-pensjon-oppgave-q2",
              "prioritet": "NORM",
              "status": "FERDIGSTILT",
              "metadata": {},
              "fristFerdigstillelse": "2024-02-07",
              "aktivDato": "2024-02-06",
              "opprettetTidspunkt": "2024-02-06T11:58:37.984+01:00"
            }
        """.trimIndent()

        every { oppgaveOAuthRestTemplate.getForEntity("/api/v1/oppgaver?statuskategori=AVSLUTTET&journalpostId=$journalpostId1", String::class.java) } returns ResponseEntity(
            mockOppgave,
            HttpStatus.OK
        )

        //TODO: kalle oppgave for å hente inn oppgaven
        //TODO: Opprette ny oppgave med samme journalpostid
        //TODO: Sjekke at den faktiske oppgaven blir sendt

        oppgaveService.lagOppgaveForJournalpost()
        val actualResult = mapJsonToAny<Oppgave>(
            """{         
              "tildeltEnhetsnr" : "4303",
              "opprettetAvEnhetsnr" : "9999",
              "journalpostId" : "645601988",
              "beskrivelse" : "Inngående P8000 - Forespørsel om informasjon / Rina saksnr: 1447360",
              "tema" : "PEN",
              "oppgavetype" : "JFR",
              "prioritet" : "NORM",
              "fristFerdigstillelse" : "2024-03-06",
              "aktivDato" : "2024-03-05"            
        }""", false
        )

        verify (exactly = 1) {
            oppgaveOAuthRestTemplate.exchange(
                "/", HttpMethod.POST,
                HttpEntity(mapAnyToJson(actualResult, true)), String::class.java
            )
        }
    }
}