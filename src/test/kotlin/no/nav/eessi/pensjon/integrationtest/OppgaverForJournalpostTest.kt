package no.nav.eessi.pensjon.integrationtest

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.Bucket
import com.google.cloud.storage.Storage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.services.saf.SafClient
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Oppgave
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.services.OppgaveService
import no.nav.eessi.pensjon.services.gcp.GcpStorageService
import no.nav.eessi.pensjon.services.saf.JournalpostResponse
import no.nav.eessi.pensjon.services.saf.Journalstatus
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.time.LocalDateTime

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
        oppgaveService = OppgaveService(
            oppgaveOAuthRestTemplate,
            gcpStorageService,
            safClient,
            mockk<Environment>().apply { every { activeProfiles } returns arrayOf("test") })
    }

    @Test
    fun `Gitt at det ikke finnes en fil paa gcp saa skal det ikke lages oppgave og avslutte uten feil`() {
        every { gcpStorageService.hentJournalpostFilfraS3() } returns null

        oppgaveService.lagOppgaveForJournalpost()

        verify (exactly = 0) {
            oppgaveOAuthRestTemplate.exchange("/", HttpMethod.POST, any(), String::class.java )
        }
    }

    @Test
    fun `Gitt at vi har en ferdigstilt oppgave paa en journalpost som er i status D saa skal vi opprette en ny oppgave på samme journalpost`() {

        // Hente listen fra gcp
        val journalpostIds = listOf("645601988", "645950501")
        every { gcpStorageService.hentJournalpostFilfraS3() } returns journalpostIds.joinToString(separator = ",")

        // Kalle joark for å sjekke oppgavestatus (verifisering) sjekker om faktisk status på oppgacve er D
        val journalpostResponse = journalpostResponse(journalpostIds)

        // Sjekk om den er ferdigstilt (sjekker mot joark)
        every { safClient.hentJournalpost(eq(journalpostIds[0])) } returns journalpostResponse
        every { oppgaveOAuthRestTemplate.getForEntity("/api/v1/oppgaver?statuskategori=AVSLUTTET&journalpostId=${journalpostIds[0]}", String::class.java) } returns ResponseEntity(
            lagJournalpost(journalpostIds),
            HttpStatus.OK
        )

        // kalle oppgave for å hente inn oppgaven, opprette ny oppgave med samme journalpostid
        oppgaveService.lagOppgaveForJournalpost()
        val actualResult = forventetResulatFraOppgave(journalpostIds)

        // Sjekke at den faktiske oppgaven blir sendt
        verify (exactly = 1) {
            oppgaveOAuthRestTemplate.exchange(
                "/", HttpMethod.POST,
                HttpEntity(mapAnyToJson(actualResult, true)), String::class.java
            )
        }
    }

    private fun journalpostResponse(journalpostIds: List<String>): JournalpostResponse {
        val journalpostResponse = JournalpostResponse(
            journalpostIds[0],
            Tema.PENSJON,
            Journalstatus.UNDER_ARBEID,
            true,
            null,
            Behandlingstema.UFORE_UTLAND,
            "4303",
            "Alderspensjon",
            null,
            LocalDateTime.now()
        )
        return journalpostResponse
    }

    private fun forventetResulatFraOppgave(journalpostIds: List<String>): Oppgave {
        val actualResult = mapJsonToAny<Oppgave>(
            """{         
                  "tildeltEnhetsnr" : "4303",
                  "opprettetAvEnhetsnr" : "9999",
                  "journalpostId" : "${journalpostIds[0]}",
                  "beskrivelse" : "Inngående P8000 - Forespørsel om informasjon / Rina saksnr: 1447360",
                  "tema" : "PEN",
                  "oppgavetype" : "JFR",
                  "prioritet" : "NORM",
                  "fristFerdigstillelse" : "${LocalDate.now().plusDays(1)}",
                  "aktivDato" : "${LocalDate.now()}"            
            }""", false
        )
        return actualResult
    }

    private fun lagJournalpost(journalpostIds: List<String>): String {
        val mockOppgave = """
                {
                  "id": 192136,
                  "tildeltEnhetsnr": "4303",
                  "opprettetAvEnhetsnr": "9999",
                  "journalpostId": "${journalpostIds[0]}",
                  "beskrivelse": "Inngående P8000 - Forespørsel om informasjon / Rina saksnr: 1447360",
                  "tema": "PEN",
                  "oppgavetype": "JFR",
                  "versjon": 1,
                  "opprettetAv": "eessi-pensjon-oppgave-q2",
                  "prioritet": "NORM",
                  "status": "FERDIGSTILT",
                  "metadata": {},
                  "fristFerdigstillelse": "${LocalDate.now().plusDays(1)}",
                  "aktivDato": "${LocalDate.now()}",
                  "opprettetTidspunkt": "2024-02-06T11:58:37.984+01:00"
                }
            """.trimIndent()
        return mockOppgave
    }
}