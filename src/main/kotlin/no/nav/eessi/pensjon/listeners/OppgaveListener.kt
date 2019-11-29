package no.nav.eessi.pensjon.listeners

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.services.oppgave.OppgaveMelding
import no.nav.eessi.pensjon.services.oppgave.OppgaveService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.Message
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.CountDownLatch

@Service
class OppgaveListener(private val oppgaveService: OppgaveService,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(OppgaveListener::class.java)
    private val latch = CountDownLatch(1)
    private val X_REQUEST_ID = "x_request_id"

    fun getLatch(): CountDownLatch {
        return latch
    }


    @KafkaListener(topics = ["\${kafka.oppgave.topic}"], groupId = "\${kafka.oppgave.groupid}")
    fun consumeOppgaveMelding(@Payload melding: OppgaveMelding, cr: ConsumerRecord<String, OppgaveMelding>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            metricsHelper.measure("consumeOutgoingSed") {
                logger.info("Innkommet oppgave hendelse i partisjon: ${cr.partition()}, med offset: ${cr.offset()}")
                logger.debug("oppgave melding : $melding")
                try {
//                    val oppgaveMelding = OppgaveMelding.fromJson(melding)
                    val oppgaveMelding = melding
                    oppgaveService.opprettOppgaveFraMelding(oppgaveMelding)
                    acknowledgment.acknowledge()
                    logger.info("Acket oppgavemelding med offset: ${cr.offset()} i partisjon ${cr.partition()}")

                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av oppgavemelding:\n $melding \n ${ex.message}", ex)
                    throw RuntimeException(ex.message)
                }
            latch.countDown()
            }
        }
    }

}
