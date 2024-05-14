package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonValue

enum class OppgaveType(@JsonValue val kode: String) {
    GENERELL ("GEN"),
    JOURNALFORING ("JFR"),
    JOURNALFORING_UT ("JFR_UT"),
    BEHANDLE_SED ("BEH_SED"),
    KRAV ("KRA"),
    PDL ("BEH_SED")
}