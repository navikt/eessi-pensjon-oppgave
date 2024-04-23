package no.nav.eessi.pensjon.services

import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.listeners.OppgaveListener
import no.nav.eessi.pensjon.models.OppgaveMelding
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OppgaveListenerTest {

    val oppgaveListener =  OppgaveListener(OppgaveService(mockk()))

    @Test
    fun `BehandleSedBeskrivelse for behandling av vedlegg som ikke støttes`() {
        val oppgaveMelding = OppgaveMelding(null, P11000, null, "", null, "BEHANDLE_SED", "654654321", MOTTATT, "bogus.doc")
        val expected = """
            Mottatt vedlegg: bogus.doc tilhørende RINA sakId: 654654321 mangler filnavn eller er i et format som ikke kan journalføres. Be avsenderland/institusjon sende SED med vedlegg på nytt, i støttet filformat ( pdf, jpeg, jpg, png eller tiff ) og filnavn angitt
        """.trimIndent()

        assertEquals(expected, oppgaveListener.behandleSedBeskrivelse(oppgaveMelding))
    }

    @Test
    fun `Ved journalføring på utgående sed settes oppgavetypen til JFR_UT`() {
        val oppgaveMelding = OppgaveMelding(
            null, P11000, null, "", null, "JOURNALFORING_UT", "654654321", MOTTATT, "bogus.doc", tema = "UFO"
        )

        assertEquals("JFR_UT", oppgaveListener.opprettOppgave(oppgaveMelding).oppgavetype)
    }

    @Test
    fun `Ved journalføring på innkommende sed settes oppgavetypen til JFR`() {
        val oppgaveMelding = OppgaveMelding(
            null, P11000, null, "", null, "JOURNALFORING", "654654321", MOTTATT, "bogus.doc"
        )

        assertEquals("JFR", oppgaveListener.opprettOppgave(oppgaveMelding).oppgavetype)
    }

    @Test
    fun `BehandleSedBeskrivelse for behandling av kravSED P2200 for autojournalføring`() {
        val oppgaveMelding = OppgaveMelding(null, P2200, "654616", "", "1234567891234", "BEHANDLE_SED", "654654321", MOTTATT, null)
        val expected = """
            Det er mottatt P2200 - Krav om uførepensjon, med tilhørende RINA sakId: 654654321
        """.trimIndent()

        assertEquals(expected, oppgaveListener.behandleSedBeskrivelse(oppgaveMelding))
    }

    @Test
    fun `BehandleSedBeskrivelse for behandling av kravSED P2200 for autojournalføring filnavn er tomstreng`() {
        val oppgaveMelding = OppgaveMelding(null, P2200, "654616", "", "1234567891234", "BEHANDLE_SED", "654654321", MOTTATT, "")
        val expected = """
            Det er mottatt P2200 - Krav om uførepensjon, med tilhørende RINA sakId: 654654321
        """.trimIndent()

        assertEquals(expected, oppgaveListener.behandleSedBeskrivelse(oppgaveMelding))
    }

    @Test
    fun `BehandleSedBeskrivelse for behandling av kravSED P2000 for autojournalføring filnavn er tomstreng`() {
        val oppgaveMelding = OppgaveMelding(null, P2000, "654616", "", "1234567891234", "BEHANDLE_SED", "654654321", MOTTATT, "")
        val expected = """
            Det er mottatt P2000 - Krav om alderspensjon, med tilhørende RINA sakId: 654654321
        """.trimIndent()

        assertEquals(expected, oppgaveListener.behandleSedBeskrivelse(oppgaveMelding))
    }

    @Test
    fun `BehandleSedBeskrivelse for behandling av P5000 for autojournalføring`() {
        val oppgaveMelding = OppgaveMelding(null, P5000, "654616", "", "1234567891234", "BEHANDLE_SED", "654654321", MOTTATT, null)
        val expected = """
            Det er mottatt P5000 - Oversikt TT, med tilhørende RINA sakId: 654654321
        """.trimIndent()

        assertEquals(expected, oppgaveListener.behandleSedBeskrivelse(oppgaveMelding))
    }

    @Test
    fun `BehandleSedBeskrivelse for behandling av P6000 for autojournalføring`() {
        val oppgaveMelding = OppgaveMelding(null, P6000, "654616", "", "1234567891234", "BEHANDLE_SED", "654654321", MOTTATT, null)
        val expected = """
            Det er mottatt P6000 - Melding om vedtak, med tilhørende RINA sakId: 654654321
        """.trimIndent()

        assertEquals(expected, oppgaveListener.behandleSedBeskrivelse(oppgaveMelding))
    }

    @Test
    fun `BehandleSedBeskrivelse for behandling av SED der vi får en Runtime Exception når ingen parametre treffer`() {
        val oppgaveMelding = OppgaveMelding(null, P11000, null, "", null, "BEHANDLE_SED", "", MOTTATT, null)
        org.junit.jupiter.api.assertThrows<RuntimeException> {
            oppgaveListener.behandleSedBeskrivelse(oppgaveMelding)
        }
    }

    @Test
    fun `BehandleSedBeskrivelse returnerer tom streng dersom oppgavetype ikke er BEHANDLE_SED` () {
        assertEquals("", oppgaveListener.behandleSedBeskrivelse(OppgaveMelding(null, P11000, null, "", null, "JOURNALFORING", "", MOTTATT, null)))
    }
}