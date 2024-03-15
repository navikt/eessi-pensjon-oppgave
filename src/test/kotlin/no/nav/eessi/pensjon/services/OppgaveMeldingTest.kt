package no.nav.eessi.pensjon.services

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.models.Oppgave
import no.nav.eessi.pensjon.models.OppgaveMelding
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Test
import org.springframework.core.env.Environment
import java.nio.file.Files
import java.nio.file.Paths

class OppgaveMeldingTest {

    @Test
    fun `Deserialisering av oppgavemelding`() {
        val json = """
        {
          "sedType" : "P5000",
          "journalpostId" : "515094484",
          "tildeltEnhetsnr" : "4303",
          "aktoerId" : "1000021339877",
          "rinaSakId" : "8298756",
          "hendelseType" : "SENDT",
          "filnavn" : null,
          "oppgaveType" : "JOURNALFORING"
        }
        """.trimIndent()
        OppgaveMelding.fromJson(json)

        mapJsonToAny<OppgaveMelding>(json)
    }

    @Test
    fun readAndProcessFile(){
        OppgaveForJournalpost(mockk(), mockk(), mockk(), mockk<Environment>().apply { every { activeProfiles } returns arrayOf("PROD") }).lagOppgaver()
    }
}