package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson

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