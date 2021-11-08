
/*
import no.nav.eessi.pensjon.config.KafkaCustomErrorHandler
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SslConfigs
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.test.EmbeddedKafkaBroker
import java.time.Duration
*/

/*
@TestConfiguration
class IntegrasjonsTestConfig(@Autowired val kafkaErrorHandler: KafkaCustomErrorHandler,
        @Autowired private val aivenKafkaConsumerMap: MutableMap<String, Any> ) {
    @Value("\${" + EmbeddedKafkaBroker.SPRING_EMBEDDED_KAFKA_BROKERS + "}")
    private lateinit var brokerAddresses: String


    @Bean
    fun aivenKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String>? {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()

        val configMap: MutableMap<String, Any> = populerAivenCommonConfig(aivenKafkaConsumerMap)
        factory.consumerFactory =  DefaultKafkaConsumerFactory(configMap)

        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.containerProperties.authorizationExceptionRetryInterval =  Duration.ofSeconds(4L)
        if (kafkaErrorHandler != null) {
            factory.setErrorHandler(kafkaErrorHandler)
        }
        return factory
    }

    private fun populerAivenCommonConfig(configMap: MutableMap<String, Any>): MutableMap<String, Any> {
        configMap[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = brokerAddresses
        return configMap
    }
}*/
