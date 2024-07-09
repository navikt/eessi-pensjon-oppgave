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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.kafka.support.Acknowledgment

class OppgaveListenerTest {

    private val oppgaveService: OppgaveService = mockk()
    private val acknowledgment: Acknowledgment = mockk()
    private val oppgaveListener = OppgaveListener(oppgaveService)

    @Test
    fun `skal opprette en oppdater oppgave gitt en gyldig oppdater oppgave melding fra journalforing`() {
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

    @Test
    fun `skal kaste RuntimeException naar det er mangler ved oppgave fra journalforing`() {
        //given
        val consumerRecordMock = mockk<ConsumerRecord<String, String>>()
        val acknowledgmentMock = mockk<Acknowledgment>()
        val oppgaveMelding = OppdaterOppgaveMelding("id", "status", Enhet.UFORE_UTLAND, "tema", "aktoerId", "rinaSakId")

        //when
        every { oppgaveService.hentAapenOppgave(eq(oppgaveMelding.id))} returns Oppgave()

        //then
        assertThrows<RuntimeException> {
            oppgaveListener.consumeOppdaterOppgaveMelding(
                consumerRecordMock,
                acknowledgmentMock,
                oppgaveMelding.toJson()
            )
        }
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