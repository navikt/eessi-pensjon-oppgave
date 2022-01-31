package no.nav.eessi.pensjon.services.oppgave

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.json.toEmptyJson
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.OppgaveMelding
import no.nav.eessi.pensjon.models.SedType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.annotation.PostConstruct

/**
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Service
class OppgaveService(
        private val oppgaveOAuthRestTemplate: RestTemplate,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {
    private val logger = LoggerFactory.getLogger(OppgaveService::class.java)
    private lateinit var opprettoppgave: MetricsHelper.Metric
    private lateinit var enhetsCounter: Counter


    @PostConstruct
    fun initMetrics() {
        opprettoppgave = metricsHelper.init("opprettoppgave")
    }

    // https://oppgave.nais.preprod.local/?url=https://oppgave.nais.preprod.local/api/swagger.json#/v1oppgaver/opprettOppgave
    fun opprettOppgaveSendOppgaveInn(oppgaveMelding: OppgaveMelding) {
        opprettoppgave.measure {

            val oppgave = opprettOppgave(oppgaveMelding)

            try {
                val requestBody = oppgave.toEmptyJson()
                logger.info("Oppretter oppgave: $requestBody")

                countEnthet(oppgaveMelding.tildeltEnhetsnr)

                val httpEntity = HttpEntity(requestBody)
                oppgaveOAuthRestTemplate.exchange("/", HttpMethod.POST, httpEntity, String::class.java)

                logger.info("Opprettet journalforingsoppgave med tildeltEnhetsnr:  ${oppgaveMelding.tildeltEnhetsnr}")
            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under opprettelse av oppgave ex: $ex body: ${ex.responseBodyAsString}")
                throw java.lang.RuntimeException("En feil oppstod under opprettelse av oppgave ex: ${ex.message} body: ${ex.responseBodyAsString}")
            } catch(ex: Exception) {
                logger.error("En feil oppstod under opprettelse av oppgave ex: $ex")
                throw java.lang.RuntimeException("En feil oppstod under opprettelse av oppgave ex: ${ex.message}")
            }
        }
    }

    private fun opprettOppgave(opprettOppgave: OppgaveMelding): Oppgave {
        return try {

            val oppgaveTypeMap = mapOf(
                "GENERELL" to Oppgave.OppgaveType.GENERELL,
                "JOURNALFORING" to Oppgave.OppgaveType.JOURNALFORING,
                "BEHANDLE_SED" to Oppgave.OppgaveType.BEHANDLE_SED,
                "KRAV" to Oppgave.OppgaveType.KRAV,
                "PDL" to Oppgave.OppgaveType.PDL
            )

            val beskrivelse = when (oppgaveTypeMap[opprettOppgave.oppgaveType]) {
                Oppgave.OppgaveType.JOURNALFORING -> opprettGenerellBeskrivelse(opprettOppgave)
                Oppgave.OppgaveType.KRAV -> opprettGenerellBeskrivelse(opprettOppgave)
                Oppgave.OppgaveType.GENERELL -> opprettGenerellBeskrivelse(opprettOppgave)
                Oppgave.OppgaveType.BEHANDLE_SED -> behandleSedBeskrivelse(opprettOppgave)
                Oppgave.OppgaveType.PDL -> behandleSedPdlUidBeskrivelse(opprettOppgave)
                else -> throw RuntimeException("Ukjent eller manglende oppgavetype under opprettelse av oppgave")
            }

            val oppgave = opprettGeneriskOppgave(oppgaveTypeMap, opprettOppgave, beskrivelse)
            oppgave

        } catch (ex: Exception) {
            logger.error("En feil oppstod under opprettelse av oppgave", ex)
            throw RuntimeException(ex)
        }
    }

    private fun opprettGenerellBeskrivelse(opprettOppgave: OppgaveMelding): String {
        val generellbeskrivelse =
            opprettOppgave.sedType?.let { sedType -> genererBeskrivelseTekst(sedType, opprettOppgave.rinaSakId, opprettOppgave.hendelseType) } ?: throw RuntimeException("feiler med sedtype")
        return generellbeskrivelse
    }

    private fun opprettGeneriskOppgave(oppgaveTypeMap: Map<String, Oppgave.OppgaveType>, opprettOppgave: OppgaveMelding, beskrivelse: String): Oppgave {
        return Oppgave(
            oppgavetype = oppgaveTypeMap[opprettOppgave.oppgaveType].toString(),
            tema = Oppgave.Tema.PENSJON.toString(),
            prioritet = Oppgave.Prioritet.NORM.toString(),
            aktoerId = opprettOppgave.aktoerId,
            aktivDato = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
            journalpostId = opprettOppgave.journalpostId,
            opprettetAvEnhetsnr = "9999",
            tildeltEnhetsnr = opprettOppgave.tildeltEnhetsnr,
            fristFerdigstillelse = LocalDate.now().plusDays(1).toString(),
            beskrivelse = beskrivelse
        )
    }

    fun countEnthet(tildeltEnhetsnr: String?) {
        try {
            Metrics.counter("TildeltEnhet",   "enhet", tildeltEnhetsnr).increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet på enhet: $tildeltEnhetsnr")
        }
    }

    /**
     * Genererer beskrivelse i format:
     * Utgående PXXXX - [nav på SEDen] / Rina saksnr: xxxxxx
     */
    private fun genererBeskrivelseTekst(sedType: SedType, rinaSakId: String, hendelseType: HendelseType): String {
        return if(hendelseType == HendelseType.MOTTATT) {
            "Inngående $sedType / Rina saksnr: $rinaSakId"
        } else {
            "Utgående $sedType / Rina saksnr: $rinaSakId"
        }
    }

    fun behandleSedBeskrivelse(oppgaveMelding: OppgaveMelding): String {
        if (oppgaveMelding.oppgaveType != "BEHANDLE_SED") return ""
        logger.info("Genererer beskrivelse for oppgaveType behandle SED")

        val filnavn = oppgaveMelding.filnavn
        val journalpostId = oppgaveMelding.journalpostId
        val rinaSakId = oppgaveMelding.rinaSakId
        val aktoerId = oppgaveMelding.aktoerId
        val sedType = oppgaveMelding.sedType
        val behandlePBUC01eller03 = (filnavn == null || filnavn.isEmpty()) && journalpostId != null && aktoerId != null

        //behandlePBUC01eller03 && (sedType == SedType.P2200 || sedType == SedType.P2000) -> "Det er mottatt $sedType, med tilhørende RINA sakId: $rinaSakId"
        return when {
            behandlePBUC01eller03 -> "Det er mottatt $sedType, med tilhørende RINA sakId: $rinaSakId"
            filnavn != null && journalpostId == null -> "Mottatt vedlegg: $filnavn tilhørende RINA sakId: $rinaSakId mangler filnavn eller er i et format som ikke kan journalføres. Be avsenderland/institusjon sende SED med vedlegg på nytt, i støttet filformat ( pdf, jpeg, jpg, png eller tiff ) og filnavn angitt"
            else -> throw RuntimeException("Ukjent eller manglende parametere under opprettelse av beskrivelse for behandle SED")
        }
    }

    fun behandleSedPdlUidBeskrivelse(oppgaveMelding: OppgaveMelding): String =
        "Avvik i utenlandsk ID i PDL. I RINA saksnummer ${oppgaveMelding.rinaSakId} er det mottatt en SED med utenlandsk ID som er forskjellig fra den som finnes i PDL. Avklar hvilken som er korrekt eller om det skal legges til en utenlandsk ID."
}

private class Oppgave(
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
