package no.nav.eessi.pensjon.config

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.listener.ContainerAwareErrorHandler
import org.springframework.kafka.listener.ContainerStoppingErrorHandler
import org.springframework.kafka.listener.MessageListenerContainer
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Duration

@Configuration
class KafkaConfig {


    @Bean
    fun sedSendtAuthRetry(registry: KafkaListenerEndpointRegistry): ApplicationRunner? {
        return ApplicationRunner {
            val sedSendtListenerContainer = registry.getListenerContainer("oppgaveListener")
            sedSendtListenerContainer.containerProperties.authorizationExceptionRetryInterval = Duration.ofSeconds(4L)
            sedSendtListenerContainer.start()
        }
    }


    @Bean
    fun kafkaListenerContainerFactory(configurer: ConcurrentKafkaListenerContainerFactoryConfigurer,
                                      kafkaConsumerFactory: ConsumerFactory<Any, Any>,
                                      kafkaErrorHandler: KafkaCustomErrorHandler): ConcurrentKafkaListenerContainerFactory<*, *> {

        val factory = ConcurrentKafkaListenerContainerFactory<Any, Any>()
        configurer.configure(factory, kafkaConsumerFactory)
        factory.setErrorHandler(kafkaErrorHandler)
        return factory
    }

    @Bean
    fun kafkaCustomErrorHandlerBean() : KafkaCustomErrorHandler{
        return KafkaCustomErrorHandler()
    }

    open class KafkaCustomErrorHandler : ContainerAwareErrorHandler {
        private val logger = LoggerFactory.getLogger(KafkaCustomErrorHandler::class.java)

        private val stopper = ContainerStoppingErrorHandler()

        override fun handle(thrownException: Exception?,
                            records: MutableList<ConsumerRecord<*, *>>?,
                            consumer: Consumer<*, *>?,
                            container: MessageListenerContainer?) {
            val stacktrace = StringWriter()
            thrownException?.printStackTrace(PrintWriter(stacktrace))

            logger.error("En feil oppstod under kafka konsumering av meldinger: \n ${hentMeldinger(records)} \n" +
                    "Stopper containeren ! Restart er nødvendig for å fortsette konsumering, $stacktrace")
            stopper.handle(thrownException, records, consumer, container)
        }

        fun hentMeldinger(records: MutableList<ConsumerRecord<*, *>>?): String {
            return records?.joinToString(separator = "") {
                "--------------------------------------------------------------------------------\n$it\n"
            } ?: ""
        }
    }
}