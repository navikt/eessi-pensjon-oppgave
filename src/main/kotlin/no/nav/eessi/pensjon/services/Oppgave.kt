package no.nav.eessi.pensjon.services

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class Oppgave(
        val id: Long? = null,
        val tildeltEnhetsnr: String? = null,
        val endretAvEnhetsnr: String? = null,
        val opprettetAvEnhetsnr: String? = null,
        val journalpostId: String? = null,
        val journalpostkilde: String? = null,
        val behandlesAvApplikasjon: String? = null,
        val saksreferanse: String? = null,
        val bnr: String? = null,
        val samhandlernr: String? = null,
        val aktoerId: String? = null,
        val orgnr: String? = null,
        val tilordnetRessurs: String? = null,
        val beskrivelse: String? = null,
        val temagruppe: String? = null,
        val tema: String? = null,
        val behandlingstema: String? = null,
        val oppgavetype: String? = null,
        val behandlingstype: String? = null,
        val prioritet: String? = null,
        val versjon: String? = null,
        val mappeId: String? = null,
        val fristFerdigstillelse: String? = null,
        val aktivDato: String? = null,
        val opprettetTidspunkt: String? = null,
        val opprettetAv: String? = null,
        val endretAv: String? = null,
        val ferdigstiltTidspunkt: String? = null,
        val endretTidspunkt: String? = null,
        val status: String? = null,
        val metadata: Map<String, String>? = null
) {

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
        };

    }

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

    enum class Prioritet {
        HOY,
        NORM,
        LAV
    }

    interface Code {
        fun decode(): String
    }
}