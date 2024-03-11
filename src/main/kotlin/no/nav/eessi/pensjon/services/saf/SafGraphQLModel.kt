package no.nav.eessi.pensjon.services.saf

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.eessi.pensjon.models.BehandlingTema
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.utils.mapAnyToJson
import java.time.LocalDateTime

/**
 * Request og responsemodell for SAF GraphQL tjeneste
 * se https://confluence.adeo.no/display/BOA/saf+-+Utviklerveiledning
 */

data class SafRequest(
    val journalpostId: String,
    val query: String = """
        query {journalpost(journalpostId:  "$journalpostId") {              
                  journalpostId
                  bruker {
                    id
                    type
                  }  
                  tittel
                  journalposttype
                  journalstatus
                  tema
                  behandlingstema
                  journalforendeEnhet
                  eksternReferanseId
                  tilleggsopplysninger {
                    nokkel
                    verdi
                  }
                  datoOpprettet
                }
        }""".trimIndent()
) {
    fun toJson(): String {
        return mapAnyToJson(this, false)
    }
}
@JsonIgnoreProperties(ignoreUnknown = true)
data class Journalpost(
    val journalpostId: String,
    val bruker: Bruker?,
    val tittel: String? = null,
    val journalposttype: String? = null,
    val journalstatus: Journalstatus?,
    val tema: Tema?,
    val behandlingstema: BehandlingTema?,
    val journalforendeEnhet: String?,
    val eksternReferanseId: String? = null,
    val tilleggsopplysninger: List<Map<String, String>> = emptyList(),
    val datoOpprettet: LocalDateTime?,
)

enum class Journalstatus {
    UKJENT, OPPLASTING_DOKUMENT, RESERVERT, UKJENT_BRUKER, AVBRUTT, UTGAAR, FEILREGISTRERT, UNDER_ARBEID, EKSPEDERT, FERDIGSTILT, JOURNALFOERT, MOTTATT
}

data class HentJournalPoster (val data: Data) {
    fun toJson(): String {
        return mapAnyToJson(this, false)
    }
}

data class Data(val journalpost: Journalpost)

data class Bruker(
    val id: String,
    val type: BrukerIdType
)

enum class BrukerIdType {
    FNR,
    AKTOERID
}

/**
 * https://confluence.adeo.no/display/BOA/Enum%3A+Variantformat
 */
enum class VariantFormat {
    ARKIV,
    FULLVERSJON,
    PRODUKSJON,
    PRODUKSJON_DLF,
    SLADDET,
    ORIGINAL
}




