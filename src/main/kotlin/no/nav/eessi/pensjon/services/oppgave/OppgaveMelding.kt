package no.nav.eessi.pensjon.services.oppgave

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs

data class OppgaveMelding(
        val sedType : String,
        val journalpostId : String?,
        val tildeltEnhetsnr : String,
        val aktoerId : String?,
        val oppgaveType : String,
        val rinaSakId : String,
        val hendelseType : String?,
        var filnavn : String?
    ) {
    companion object {
        fun fromJson(json: String) = mapJsonToAny(json, typeRefs<OppgaveMelding>())
    }
}