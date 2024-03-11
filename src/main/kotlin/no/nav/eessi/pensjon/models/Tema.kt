package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.eessi.pensjon.models.Oppgave

enum class Tema(@JsonValue val kode: String) {
    PENSJON("PEN"),
    UFORETRYGD("UFO"),
    OMSTILLING("EYO"),
    EYBARNEP("EYB"),
}
enum class Temagruppe(@JsonValue val kode: String) {
    PENSJON ("PENS"),
    UFORETRYDG ("UFRT")
}
enum class Behandlingstype(@JsonValue val kode: String) {
    MOTTA_SOKNAD_UTLAND ("ae0110"),
    UTLAND ("ae0106")
}
