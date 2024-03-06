package no.nav.eessi.pensjon.models

enum class OppgaveType : Code {
    GENERELL {
        override fun toString() = "GEN"
        override fun decode() = "Generell"
    },
    JOURNALFORING {
        override fun toString() = "JFR"
        override fun decode() = "Journalføringsoppgave"
    },
    BEHANDLE_SED {
        override fun toString() = "BEH_SED"
        override fun decode() = "Behandle SED"
    },
    KRAV {
        override fun toString() = "KRA"
        override fun decode() = "Krav"
    },
    PDL {
        override fun toString() = "BEH_SED"
        override fun decode() = "Behandle SED"
    }
}