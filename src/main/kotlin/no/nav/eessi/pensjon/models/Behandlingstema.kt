package no.nav.eessi.pensjon.models

enum class Behandlingstema : Code {
    UTLAND {
        override fun toString() = "ab0313"
        override fun decode() = "Utland"
    },
    UFORE_UTLAND {
        override fun toString() = "ab0039"
        override fun decode() = "Uføreytelser fra utlandet"
    }
}