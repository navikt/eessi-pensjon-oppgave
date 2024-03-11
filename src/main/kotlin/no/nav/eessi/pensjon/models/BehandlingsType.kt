package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonValue

enum class BehandlingsType(@JsonValue val kode: String) {
    UTLAND ("ab0313"),
    UFORE_UTLAND ("ab0039")
}