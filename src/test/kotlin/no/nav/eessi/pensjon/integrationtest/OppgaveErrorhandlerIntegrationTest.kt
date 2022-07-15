package no.nav.eessi.pensjon.integrationtest

/*
import no.nav.eessi.pensjon.config.KafkaCustomErrorHandler
*/
import com.ninjasquad.springmockk.MockkBean
import io.mockk.verify
import no.nav.eessi.pensjon.EessiPensjonOppgaveApplicationTest
import no.nav.eessi.pensjon.config.KafkaStoppingErrorHandler
import no.nav.eessi.pensjon.listeners.OppgaveListener
import no.nav.eessi.pensjon.services.OppgaveService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Test
import org.mockserver.integration.ClientAndServer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils.*
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit

private const val OPPGAVE_TOPIC = "privat-eessipensjon-oppgave-v1-test"

private lateinit var mockServer: ClientAndServer

@SpringBootTest(classes = [EessiPensjonOppgaveApplicationTest::class ], value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [OPPGAVE_TOPIC]
)
class OppgaveErrorhandlerIntegrationTest {

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    lateinit var embeddedKafka: EmbeddedKafkaBroker

    @MockkBean
    lateinit var kafkaCustomErrorHandler: KafkaStoppingErrorHandler

    @MockkBean
    lateinit var oppgaveOAuthRestTemplate: RestTemplate

    @Autowired
    lateinit var oppgaveService : OppgaveService

    @Autowired
    lateinit var oppgaveListener: OppgaveListener

    @Test
    fun `Når en exception skjer så skal kafka-konsumering stoppe`() {
        // Vent til kafka er klar
        val container = settOppUtitlityConsumer()
        container.start()
        ContainerTestUtils.waitForAssignment(container, embeddedKafka.partitionsPerTopic)

        // Sett opp producer
        val oppgaveProducerTemplate = settOppProducerTemplate()

        produserOppgaveHendelser(oppgaveProducerTemplate)

        // Venter på at sedListener skal consumeSedSendt meldingene
        oppgaveListener.getLatch().await(15000, TimeUnit.MILLISECONDS)

        verify(exactly = 1) {kafkaCustomErrorHandler.handle(any(), any(), any(), any())  }

        // Shutdown
        shutdown(container)
    }

    private fun produserOppgaveHendelser(template: KafkaTemplate<String, String>) {
        val key1 = UUID.randomUUID().toString()
        val data1 = String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/oppgavemeldingP2000.json")))
        template.send(OPPGAVE_TOPIC, key1, data1)
    }

    private fun shutdown(container: KafkaMessageListenerContainer<String, String>) {
        mockServer.stop()
        container.stop()
        embeddedKafka.kafkaServers.forEach { it.shutdown() }
    }

    private fun settOppProducerTemplate(): KafkaTemplate<String, String> {
        return KafkaTemplate(DefaultKafkaProducerFactory(producerProps(embeddedKafka.brokersAsString), StringSerializer(), StringSerializer())).apply {
                defaultTopic = OPPGAVE_TOPIC
            }
    }

    companion object {
        init {
            // Start Mockserver in memory
            System.lineSeparator()
            val port = randomFrom()
            mockServer = ClientAndServer.startClientAndServer(port)
            System.setProperty("mockServerport", port.toString())

        }

        private fun randomFrom(from: Int = 2024, to: Int = 55535): Int {
            val random = Random()
            return random.nextInt(to - from) + from
        }
    }

        private fun settOppUtitlityConsumer(): KafkaMessageListenerContainer<String, String> {
            val consumerProperties = consumerProps("eessi-pensjon-group2", "false", embeddedKafka)
            consumerProperties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"

            val consumerFactory = DefaultKafkaConsumerFactory(consumerProperties, StringDeserializer(), StringDeserializer())
            val container = KafkaMessageListenerContainer(consumerFactory, ContainerProperties(OPPGAVE_TOPIC)).apply {
                setupMessageListener(MessageListener<String, String> { record -> println("Konsumerer melding:  $record") })
            }
            return container
        }
    }
