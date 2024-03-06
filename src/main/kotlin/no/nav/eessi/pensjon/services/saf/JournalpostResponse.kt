package no.nav.eessi.pensjon.services.saf

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.IdType
import no.nav.eessi.pensjon.models.Tema
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class JournalpostResponse(
    val journalpostId: String?,
    val tema: Tema?,
    val journalstatus: Journalstatus?,
    val journalpostferdigstilt: Boolean?,
    val avsenderMottaker: AvsenderMottaker?,
    val behandlingstema: Behandlingstema?,
    val journalforendeEnhet: String?,
    val temanavn: String?,
    val bruker: Bruker?,
    val datoOpprettet: LocalDateTime? = null
)

data class AvsenderMottaker(
    val id: String? = null,
    val idType: IdType? = IdType.UTL_ORG,
    val navn: String? = null,
    val land: String? = null
)

enum class Journalstatus {
    UKJENT, OPPLASTING_DOKUMENT, RESERVERT, UKJENT_BRUKER, AVBRUTT, UTGAAR, FEILREGISTRERT, UNDER_ARBEID, EKSPEDERT, FERDIGSTILT, JOURNALFOERT, MOTTATT
}