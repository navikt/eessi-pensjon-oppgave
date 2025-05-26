package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
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
    val tema: String? = Tema.PENSJON.kode,
    val sendeAdvarsel: Boolean? = false,
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

enum class IdType {
    UTL_ORG
}