package no.nav.eessi.pensjon.config

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler
import org.springframework.kafka.listener.ContainerAwareErrorHandler
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.stereotype.Component
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.Exception

@Component
class KafkaCustomErrorHandler : ContainerAwareErrorHandler {
    private val logger = LoggerFactory.getLogger(KafkaCustomErrorHandler::class.java)

    private val stopper = CommonContainerStoppingErrorHandler()

    override fun handle(
        thrownException: Exception,
        records: MutableList<ConsumerRecord<*, *>>?,
        consumer: Consumer<*, *>,
        container: MessageListenerContainer
    ) {
        val stacktrace = StringWriter()
        thrownException.printStackTrace(PrintWriter(stacktrace))

        logger.error("En feil oppstod under kafka konsumering av meldinger: \n ${hentMeldinger(records)} \n" +
                "Stopper containeren ! Restart er nødvendig for å fortsette konsumering, $stacktrace")
        stopper.handleRemaining(thrownException, records?: emptyList(), consumer, container)
    }

    fun hentMeldinger(records: MutableList<ConsumerRecord<*, *>>?): String {
        return records?.joinToString(separator = "") {
            "--------------------------------------------------------------------------------\n$it\n"
        } ?: ""
    }
}
