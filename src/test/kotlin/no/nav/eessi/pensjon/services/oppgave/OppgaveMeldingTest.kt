package no.nav.eessi.pensjon.services.oppgave

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.OppgaveMelding
import org.junit.jupiter.api.Test

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

        mapJsonToAny(json, typeRefs<OppgaveMelding>())
    }
}