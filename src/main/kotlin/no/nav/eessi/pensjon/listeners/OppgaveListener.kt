package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.*
import no.nav.eessi.pensjon.models.OppgaveType.*
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.services.OppgaveService
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.CountDownLatch

private const val X_REQUEST_ID = "x_request_id"

@Service
class OppgaveListener(
    private val oppgaveService: OppgaveService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {

    private val logger = LoggerFactory.getLogger(OppgaveListener::class.java)
    private val latch = CountDownLatch(6)

    private lateinit var consumeOppgavemelding: MetricsHelper.Metric

    init {
        consumeOppgavemelding = metricsHelper.init("consumeOppgavemelding")
    }

    fun getLatch(): CountDownLatch {
        return latch
    }

    @KafkaListener(
        containerFactory = "aivenKafkaListenerContainerFactory",
            topics = ["\${kafka.oppgave.topic}"],
            groupId = "\${kafka.oppgave.groupid}"
    )
    fun consumeOppgaveMelding(cr: ConsumerRecord<String, String>,  acknowledgment: Acknowledgment, @Payload melding: String) {
        MDC.putCloseable(X_REQUEST_ID, createUUID(cr)).use {
            consumeOppgavemelding.measure {
                logger.info("******************************************************************\r\n" +
                        "Innkommet oppgave hendelse i partisjon: ${cr.partition()}, med offset: ${cr.offset()} \r\n" +
                        "******************************************************************")

                try {
                    if (cr.offset() in listOf(70362L, 70648L)) {
                        logger.warn("Hopper over offset: ${cr.offset()} grunnet feil")
                    } else {
                        logger.info("Mottatt OpprettOppgave melding : $melding")
                        logger.info("Oppgavemelding er av type ")
                        oppgaveService.opprettOppgaveSendOppgaveInn(opprettOppgave(mapJsonToAny<OppgaveMelding>(melding)))
                            .also { logger.info("Acker opprett oppgave ${cr.offset()}") }
                    }
                    acknowledgment.acknowledge()
                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av oppgavemelding:\n $melding \n ${ex.message}", ex)
                    throw RuntimeException(ex.message)
                }
            latch.countDown()
            }
        }
    }

    @KafkaListener(
        containerFactory = "aivenKafkaListenerContainerFactory",
        topics = ["\${kafka.oppdateroppgave.topic}"],
        groupId = "\${kafka.oppgave.groupid}"
    )
    fun consumeOppdaterOppgaveMelding(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment, @Payload melding: String) {
        MDC.putCloseable(X_REQUEST_ID, createUUID(cr)).use {
            consumeOppgavemelding.measure {
                logger.info(
                    "******************************************************************\r\n" +
                            "Innkommet oppgave hendelse i partisjon: ${cr.partition()}, med offset: ${cr.offset()} \r\n" +
                            "******************************************************************"
                )
                try {
                    logger.info("Mottatt OppdaterOppgave melding : $melding")
                    val oppgaveMelding = mapJsonToAny<OppdaterOppgaveMelding>(melding)
                    val oppgave = oppgaveService.hentAapenOppgave(oppgaveMelding.id)

                    oppgaveService.oppdaterOppgave(oppgaveMelding.copy(id = oppgave?.id.toString()))
                        .also { logger.info("Acker oppdater oppgave med id: ${oppgave?.id} med offset: ${cr.offset()}") }
                    acknowledgment.acknowledge()

                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av oppdater oppgave melding:\n $melding \n ${ex.message}", ex)
                    throw RuntimeException(ex.message)
                }
                latch.countDown()
            }
        }
    }

    fun opprettOppgave(opprettOppgave: OppgaveMelding): Oppgave {
        return try {

            val oppgaveType = OppgaveType.valueOf(opprettOppgave.oppgaveType)
            val beskrivelse = when (oppgaveType) {
                PDL -> behandleSedPdlUidBeskrivelse(opprettOppgave)
                BEHANDLE_SED -> behandleSedBeskrivelse(opprettOppgave)
                KRAV, GENERELL, JOURNALFORING_UT, JOURNALFORING -> opprettGenerellBeskrivelse(opprettOppgave)
            }
            opprettGeneriskOppgave(oppgaveType, opprettOppgave, beskrivelse)

        } catch (ex: Exception) {
            logger.error("En feil oppstod under opprettelse av oppgave", ex)
            throw RuntimeException(ex)
        }
    }

    fun behandleSedPdlUidBeskrivelse(oppgaveMelding: OppgaveMelding): String {
        return "Avvik i utenlandsk ID i PDL. I RINA saksnummer ${oppgaveMelding.rinaSakId} " +
                "er det mottatt en SED med utenlandsk ID som er forskjellig fra den som finnes i PDL. " +
                "Avklar hvilken som er korrekt eller om det skal legges til en utenlandsk ID."
    }

    fun behandleSedBeskrivelse(oppgaveMelding: OppgaveMelding): String {
        if (oppgaveMelding.oppgaveType != "BEHANDLE_SED") return ""
        logger.info("Genererer beskrivelse for oppgaveType behandle SED")

        val filnavn = oppgaveMelding.filnavn
        val journalpostId = oppgaveMelding.journalpostId
        val rinaSakId = oppgaveMelding.rinaSakId
        val aktoerId = oppgaveMelding.aktoerId
        val sedType = oppgaveMelding.sedType
        val behandlePBUC01eller03 = filnavn.isNullOrEmpty() && journalpostId != null && aktoerId != null

        return when {
            behandlePBUC01eller03 -> "Det er mottatt $sedType - ${sedType?.beskrivelse}, med tilhørende RINA sakId: $rinaSakId"
            filnavn != null && journalpostId == null -> "Mottatt vedlegg: $filnavn tilhørende RINA sakId: $rinaSakId mangler filnavn eller er i et format som ikke kan journalføres. Be avsenderland/institusjon sende SED med vedlegg på nytt, i støttet filformat ( pdf, jpeg, jpg, png eller tiff ) og filnavn angitt"
            else -> throw RuntimeException("Ukjent eller manglende parametere under opprettelse av beskrivelse for behandle SED")
        }
    }

    private fun opprettGeneriskOppgave(oppgaveType: OppgaveType, opprettOppgave: OppgaveMelding, beskrivelse: String): Oppgave {
        return Oppgave(
            oppgavetype = oppgaveType.kode,
            tema = opprettOppgave.tema,
            prioritet = Prioritet.NORM.toString(),
            aktoerId = opprettOppgave.aktoerId,
            aktivDato = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
            journalpostId = opprettOppgave.journalpostId,
            opprettetAvEnhetsnr = "9999",
            tildeltEnhetsnr = opprettOppgave.tildeltEnhetsnr,
            fristFerdigstillelse = LocalDate.now().plusDays(1).toString(),
            beskrivelse = beskrivelse
        )
    }

    private fun opprettGenerellBeskrivelse(opprettOppgave: OppgaveMelding): String {
        return opprettOppgave.sedType?.let { sedType ->
            genererBeskrivelseTekst(
                sedType,
                opprettOppgave.rinaSakId,
                opprettOppgave.hendelseType
            )
        } ?: throw RuntimeException("feiler med sedtype")
    }

    /**
     * Genererer beskrivelse i format:
     * Utgående PXXXX - [nav på SEDen] / Rina saksnr: xxxxxx
     */
    private fun genererBeskrivelseTekst(sedType: SedType, rinaSakId: String?, hendelseType: HendelseType): String {
        return if(hendelseType == HendelseType.MOTTATT) {
            "Inngående $sedType - ${sedType.beskrivelse} / Rina saksnr: $rinaSakId"
        } else {
            "Utgående $sedType - ${sedType.beskrivelse} / Rina saksnr: $rinaSakId"
        }
    }

    private fun createUUID(cr: ConsumerRecord<String, String>): String {
        val key = cr.key() ?: UUID.randomUUID().toString()
        logger.debug("x-request_id : $key")
        return key
    }

}
