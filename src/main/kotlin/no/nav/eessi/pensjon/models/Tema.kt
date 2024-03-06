package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.models.Oppgave

enum class Tema : Code {
    PENSJON {
        override fun toString() = "PEN"
        override fun decode() = "Pensjon"
    },
    UFORETRYGD {
        override fun toString() = "UFO"
        override fun decode() = "Uføretrygd"
    },
    OMSTILLING {
        override fun toString() = "EYO"
        override fun decode() = "Omstilling"
    },
    BARNEPENSJON {
        override fun toString() = "EYB"
        override fun decode() = "Barnepensjon"
    }
}
enum class Temagruppe : Code {
    PENSJON {
        override fun toString() = "PENS"
        override fun decode() = "Pensjon"
    },
    UFORETRYDG {
        override fun toString() = "UFRT"
        override fun decode() = "Uføretrydg"
    }
}
enum class Behandlingstype : Code {
    MOTTA_SOKNAD_UTLAND {
        override fun toString() = "ae0110"
        override fun decode() = "Motta søknad utland"
    },
    UTLAND {
        override fun toString() = "ae0106"
        override fun decode() = "Utland"
    }
}
