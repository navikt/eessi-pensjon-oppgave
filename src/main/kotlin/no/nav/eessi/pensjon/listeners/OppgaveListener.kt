package no.nav.eessi.pensjon.listeners

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.OppgaveMelding
import no.nav.eessi.pensjon.services.oppgave.OppgaveService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.PartitionOffset
import org.springframework.kafka.annotation.TopicPartition
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.CountDownLatch

@Service
class OppgaveListener(private val oppgaveService: OppgaveService,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(OppgaveListener::class.java)
    private val latch = CountDownLatch(3)
    private val X_REQUEST_ID = "x_request_id"

    fun getLatch(): CountDownLatch {
        return latch
    }


    @KafkaListener(groupId = "\${kafka.oppgave.groupid}",
            topicPartitions = [TopicPartition(topic = "\${eessi-topic}",
                    partitionOffsets = [PartitionOffset(partition = "0", initialOffset = "5")])])
//    @KafkaListener(topics = ["\${eessi-topic}"], groupId = "\${kafka.oppgave.groupid}")
    fun consumeOppgaveMelding(cr: ConsumerRecord<String, String>,  acknowledgment: Acknowledgment, @Payload melding: String) {
        if(cr.offset() > 6) {
            acknowledgment.acknowledge()

            logger.info("******************************************************************\n" +
                    "Acket oppgavemelding med offset: ${cr.offset()} i partisjon ${cr.partition()} \n" +
                    "******************************************************************")
            return
        }

        MDC.putCloseable("x_request_id", createUUID(cr)).use {
            metricsHelper.measure("consumeOppgavemelding") {

                logger.info("******************************************************************\r\n" +
                        "Innkommet oppgave hendelse i partisjon: ${cr.partition()}, med offset: ${cr.offset()} \r\n" +
                        "******************************************************************")

                try {
                    logger.info("mottatt oppgavemelding : $melding")
                    val oppgaveMelding = OppgaveMelding.fromJson(melding)

                    oppgaveService.opprettOppgaveFraMelding(oppgaveMelding)
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
