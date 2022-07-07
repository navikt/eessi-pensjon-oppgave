package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.OppgaveMelding
import no.nav.eessi.pensjon.services.OppgaveService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.annotation.PostConstruct

private const val X_REQUEST_ID = "x_request_id"

@Service
class OppgaveListener(private val oppgaveService: OppgaveService,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {

    private val logger = LoggerFactory.getLogger(OppgaveListener::class.java)
    private val latch = CountDownLatch(6)

    private lateinit var consumeOppgavemelding: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
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
                    logger.info("mottatt oppgavemelding : $melding")
                    val oppgaveMelding = OppgaveMelding.fromJson(melding)

                    oppgaveService.opprettOppgaveSendOppgaveInn(oppgaveMelding)
                    acknowledgment.acknowledge()

                    logger.info("******************************************************************\n" +
                            "Acket oppgavemelding med offset: ${cr.offset()} i partisjon ${cr.partition()} \n" +
                            "******************************************************************")

                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av oppgavemelding:\n $melding \n ${ex.message}", ex)
                    throw RuntimeException(ex.message)
                }
            latch.countDown()
            }
        }
    }

    private fun createUUID(cr: ConsumerRecord<String, String>): String {
        val key = cr.key() ?: UUID.randomUUID().toString()
        logger.debug("x-request_id : $key")
        return key
    }

}
