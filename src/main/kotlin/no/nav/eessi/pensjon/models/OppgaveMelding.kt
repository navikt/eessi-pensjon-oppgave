package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
open class OppgaveMelding(
    val sakNr: String? = null,
    val sedType: SedType? = null,
    val journalpostId: String? = null,
    val tildeltEnhetsnr: String? = null,
    val aktoerId: String? = null,
    val oppgaveType: String,
    val rinaSakId: String? = null,
    val hendelseType: HendelseType = HendelseType.SENDT,
    val filnavn: String? = null,
    val tema: String? = "PEN",
    val oppgaveMeldingType: OppgaveMeldingType  = OppgaveMeldingType.OPPRETT_OPPGAVE
    ) {
    override fun toString(): String {
        return toJson()
    }
    companion object {
        fun fromJson(json: String) = mapJsonToAny<OppgaveMelding>(json)
    }
}

data class OppdaterOppgaveMelding (
    val id: String,
    val status: String
    ) : OppgaveMelding(oppgaveType = "BEHANDLE_SED", oppgaveMeldingType = OppgaveMeldingType.OPPDATER_OPPGAVE) {
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

enum class IdType {
    UTL_ORG
}

enum class OppgaveMeldingType {
    OPPDATER_OPPGAVE,
    OPPRETT_OPPGAVE
}