package no.nav.eessi.pensjon.listeners

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.models.OppdaterOppgaveMelding
import no.nav.eessi.pensjon.models.Oppgave
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.services.OppgaveService
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.kafka.listener.AcknowledgingConsumerAwareMessageListener
import org.springframework.kafka.support.Acknowledgment

class OppgaveListenerTest {

    private val oppgaveService: OppgaveService = mockk()
    private val acknowledgment: Acknowledgment = mockk()
    private val oppgaveListener = OppgaveListener(oppgaveService)

    @Test
    fun `should consume oppdater oppgave melding correctly`() {
        // Given
        val consumerRecord = ConsumerRecord("topic", 0, 0L, "key", "value")
        val oppgaveMelding = oppgaveFraJson()
        every { oppgaveService.hentAapenOppgave(any()) } returns oppgaveMelding
        justRun { oppgaveService.oppdaterOppgave(any()) }
        every { acknowledgment.acknowledge() } returns Unit

        // When
        oppgaveListener.consumeOppdaterOppgaveMelding(consumerRecord, acknowledgment, meldingFraJournalf())

        // Then
        verify { oppgaveService.hentAapenOppgave("222246") }
        verify { oppgaveService.oppdaterOppgave(mapJsonToAny(meldingFraJournalf())) }
        verify { acknowledgment.acknowledge() }
    }


    private fun meldingFraJournalf(): String {
        return """
            {
              "id" : "222246",
              "status" : "OPPRETTET",
              "tildeltEnhetsnr" : "4475",
              "tema" : "PEN",
              "aktoerId" : "1112323222",
              "bruker": {
                "ident": "1212121212",
                "type": "PERSON"
              }
            }
        """
    }

    private fun oppgaveFraJson(): Oppgave {
        return mapJsonToAny("""
            {
              "id" : 222246,
              "tildeltEnhetsnr" : "4303",
              "endretAvEnhetsnr" : null,
              "opprettetAvEnhetsnr" : "9999",
              "journalpostId" : "453877716",
              "journalpostkilde" : null,
              "behandlesAvApplikasjon" : null,
              "saksreferanse" : null,
              "bnr" : null,
              "samhandlernr" : null,
              "aktoerId" : null,
              "orgnr" : null,
              "tilordnetRessurs" : null,
              "beskrivelse" : "Inngående P8000 - Forespørsel om informasjon / Rina saksnr: 1449147",
              "temagruppe" : null,
              "tema" : "PEN",
              "behandlingstema" : null,
              "oppgavetype" : "JFR",
              "behandlingstype" : null,
              "prioritet" : "NORM",
              "versjon" : "1",
              "mappeId" : null,
              "fristFerdigstillelse" : "2024-07-09",
              "aktivDato" : "2024-07-08",
              "opprettetTidspunkt" : "2024-07-08T09:35:17.273+02:00",
              "opprettetAv" : "eessi-pensjon-oppgave-q2",
              "endretAv" : null,
              "ferdigstiltTidspunkt" : null,
              "endretTidspunkt" : null,
              "status" : "OPPRETTET",
              "metadata" : { }
            }
        """.trimIndent())
    }
}