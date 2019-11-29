package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.config.KafkaErrorHandler
import no.nav.eessi.pensjon.services.oppgave.OppgaveMelding
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.annotation.EnableKafka
import kotlin.collections.HashMap


@Configuration
class OppgaveConfig {

    @Value("\${spring.kafka.bootstrap-servers}")
    lateinit var brokers: String

    @Value("\${kafka.oppgave.groupid}")
    lateinit var gruppeid: String

    @Value("\${app.name}")
    lateinit var appName: String

//    @Value("\${srveessipensjon.username}")
//    lateinit var username: String
//
//    @Value("\${srveessipensjon.password}")
//    lateinit var password: String
//

    @Bean
    fun oppgaveConsumerFactory(): ConsumerFactory<String, OppgaveMelding> {

        val props = HashMap<String, Any>()
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers)
        props.put(ConsumerConfig.GROUP_ID_CONFIG, gruppeid)
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)

        return DefaultKafkaConsumerFactory(
                props,
                StringDeserializer(),
                JsonDeserializer(OppgaveMelding::class.java))
    }

    @Bean
    fun oppgaveKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, OppgaveMelding> {

        val factory = ConcurrentKafkaListenerContainerFactory<String, OppgaveMelding>()
        //factory.setErrorHandler(kafkaErrorHandler)
        factory.setConsumerFactory(oppgaveConsumerFactory())
        return factory
    }
}