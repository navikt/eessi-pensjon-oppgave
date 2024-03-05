package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.services.Oppgave
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class OppgaveMelding(
    val sakNr: String?,
    val sedType: SedType?,
    val journalpostId: String?,
    val tildeltEnhetsnr: String,
    val aktoerId: String?,
    val oppgaveType: String,
    val rinaSakId: String,
    val hendelseType: HendelseType,
    val filnavn: String? = null,
    val tema: String? = "PEN"
    ) {
    override fun toString(): String {
        return toJson()
    }
    companion object {
        fun fromJson(json: String) = mapJsonToAny<OppgaveMelding>(json)
    }
}

data class OppgaveMeldingResponse(
    val id: String,
    val tildeltEnhetsnr: String,
    val opprettetAvEnhetsnr: String,
    val journalpostId: String,
    val beskrivelse: String,
    val tema: String,
    val oppgavetype: String,
    val versjon: Int,
    val opprettetAv: String,
    val prioritet: String,
    val status: String,
    val metadata: List<String>,
    val fristFerdigstillelse: LocalDateTime,
    val aktivDato: LocalDateTime,
    val opprettetTidspunkt: LocalDateTime,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JournalpostResponse(
    val journalpostId: String?,
    val tema: Oppgave.Tema?,
    val journalstatus: Journalstatus?,
    val journalpostferdigstilt: Boolean?,
    val avsenderMottaker: AvsenderMottaker?,
    val behandlingstema: Oppgave.Behandlingstema?,
    val journalforendeEnhet: String?,
    val temanavn: String?,
    val bruker: Bruker?,
    val datoOpprettet: LocalDateTime? = null
)

enum class Journalstatus {
    UKJENT, OPPLASTING_DOKUMENT, RESERVERT, UKJENT_BRUKER, AVBRUTT, UTGAAR, FEILREGISTRERT, UNDER_ARBEID, EKSPEDERT, FERDIGSTILT, JOURNALFOERT, MOTTATT
}

data class AvsenderMottaker(
    val id: String? = null,
    val idType: IdType? = IdType.UTL_ORG,
    val navn: String? = null,
    val land: String? = null
)

enum class IdType {
    UTL_ORG
}