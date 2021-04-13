package no.nav.eessi.pensjon.services.oppgave

import io.mockk.mockk
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.OppgaveMelding
import no.nav.eessi.pensjon.models.SedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestTemplate

internal class OppgaveServiceTest {

    val restTemplate = mockk<RestTemplate>()
    val oppgaveservice = OppgaveService(restTemplate)

    @Test
    fun `BehandleSedBeskrivelse for behandling av vedlegg som ikke støttes`() {
        val oppgaveMelding = OppgaveMelding(null, SedType.P11000, null, "", null, "BEHANDLE_SED", "654654321", HendelseType.MOTTATT, "bogus.doc")
        val expected = """
            Mottatt vedlegg: bogus.doc tilhørende RINA sakId: 654654321 mangler filnavn eller er i et format som ikke kan journalføres. Be avsenderland/institusjon sende SED med vedlegg på nytt, i støttet filformat ( pdf, jpeg, jpg, png eller tiff ) og filnavn angitt
        """.trimIndent()

        assertEquals(expected, oppgaveservice.behandleSedBeskrivelse(oppgaveMelding))

    }

    @Test
    fun `BehandleSedBeskrivelse for behandling av kravSED P2200 for autojournalføring`() {
        val oppgaveMelding = OppgaveMelding(null, SedType.P2200, "654616", "", "1234567891234", "BEHANDLE_SED", "654654321", HendelseType.MOTTATT, null)
        val expected = """
            Det er mottatt P2200 - Krav om uførepensjon, med tilhørende RINA sakId: 654654321
        """.trimIndent()

        assertEquals(expected, oppgaveservice.behandleSedBeskrivelse(oppgaveMelding))
    }

    @Test
    fun `BehandleSedBeskrivelse for behandling av kravSED P2200 for autojournalføring filnavn er tomstreng`() {
        val oppgaveMelding = OppgaveMelding(null, SedType.P2200, "654616", "", "1234567891234", "BEHANDLE_SED", "654654321", HendelseType.MOTTATT, "")
        val expected = """
            Det er mottatt P2200 - Krav om uførepensjon, med tilhørende RINA sakId: 654654321
        """.trimIndent()

        assertEquals(expected, oppgaveservice.behandleSedBeskrivelse(oppgaveMelding))
    }

    @Test
    fun `BehandleSedBeskrivelse for behandling av kravSED P2000 for autojournalføring filnavn er tomstreng`() {
        val oppgaveMelding = OppgaveMelding(null, SedType.P2000, "654616", "", "1234567891234", "BEHANDLE_SED", "654654321", HendelseType.MOTTATT, "")
        val expected = """
            Det er mottatt P2000 - Krav om alderspensjon, med tilhørende RINA sakId: 654654321
        """.trimIndent()

        assertEquals(expected, oppgaveservice.behandleSedBeskrivelse(oppgaveMelding))
    }

    @Test
    fun `BehandleSedBeskrivelse for behandling av P5000 for autojournalføring`() {
        val oppgaveMelding = OppgaveMelding(null, SedType.P5000, "654616", "", "1234567891234", "BEHANDLE_SED", "654654321", HendelseType.MOTTATT, null)
        val expected = """
            Det er mottatt P5000 - Oversikt TT, med tilhørende RINA sakId: 654654321
        """.trimIndent()

        assertEquals(expected, oppgaveservice.behandleSedBeskrivelse(oppgaveMelding))
    }

    @Test
    fun `BehandleSedBeskrivelse for behandling av P6000 for autojournalføring`() {
        val oppgaveMelding = OppgaveMelding(null, SedType.P6000, "654616", "", "1234567891234", "BEHANDLE_SED", "654654321", HendelseType.MOTTATT, null)
        val expected = """
            Det er mottatt P6000 - Melding om vedtak, med tilhørende RINA sakId: 654654321
        """.trimIndent()

        assertEquals(expected, oppgaveservice.behandleSedBeskrivelse(oppgaveMelding))
    }

    @Test
    fun `BehandleSedBeskrivelse for behandling av SED der vi får en Runtime Exception når ingen parametre treffer`() {
        val oppgaveMelding = OppgaveMelding(null, SedType.P11000, null, "", null, "BEHANDLE_SED", "", HendelseType.MOTTATT, null)
        org.junit.jupiter.api.assertThrows<RuntimeException> {
            oppgaveservice.behandleSedBeskrivelse(oppgaveMelding)
        }

    }

    @Test
    fun `BehandleSedBeskrivelse returnerer tom streng dersom oppgavetype ikke er BEHANDLE_SED` () {
        assertEquals("", oppgaveservice.behandleSedBeskrivelse(OppgaveMelding(null, SedType.P11000, null, "", null, "JOURNALFORING", "", HendelseType.MOTTATT, null)))
    }


}