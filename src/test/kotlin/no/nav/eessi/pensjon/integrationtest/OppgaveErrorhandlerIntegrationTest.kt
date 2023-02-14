package no.nav.eessi.pensjon.integrationtest

/*
import no.nav.eessi.pensjon.config.KafkaCustomErrorHandler
*/
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import no.nav.eessi.pensjon.EessiPensjonOppgaveApplicationTest
import no.nav.eessi.pensjon.config.KafkaStoppingErrorHandler
import no.nav.eessi.pensjon.listeners.OppgaveListener
import no.nav.eessi.pensjon.services.OppgaveService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockserver.integration.ClientAndServer
import org.mockserver.socket.PortFactory
import org.slf4j.LoggerFactory
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
import org.springframework.kafka.test.utils.KafkaTestUtils.consumerProps
import org.springframework.kafka.test.utils.KafkaTestUtils.producerProps
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
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

    @Autowired
    lateinit var kafkaCustomErrorHandler: KafkaStoppingErrorHandler

    @Autowired
    lateinit var oppgaveService : OppgaveService

    @Autowired
    lateinit var oppgaveListener: OppgaveListener

    private val debugLogger: Logger = LoggerFactory.getLogger("no.nav.eessi.pensjon") as Logger
    private val listAppender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun `setup`(){
        listAppender.start()
        debugLogger.addAppender(listAppender)
    }

    @AfterEach
    fun `afterTest`(){
        listAppender.stop()
        mockServer.reset()
    }

    @Test
    fun `Når exception skjer så skal kafka-konsumering stoppe`() {

        // Vent til kafka er klar
        val container = settOppUtitlityConsumer()
        container.start()
        ContainerTestUtils.waitForAssignment(container, embeddedKafka.partitionsPerTopic)

        // Sett opp producer
        val oppgaveProducerTemplate = settOppProducerTemplate()

        produserOppgaveHendelser(oppgaveProducerTemplate)

        // Venter på at sedListener skal consumeSedSendt meldingene
        oppgaveListener.getLatch().await(2, TimeUnit.SECONDS)

        val feilMelding = listAppender.list.find { message ->
            message.message.contains("En feil oppstod under kafka konsumering av meldinger")
        }?.message

        // har gjort journalpostId til 11 siffer - den blir da tolket som fødselsnr - og erstattet md *** ... men bedre med litt for mye vask enn for lite (?)
        assert(feilMelding!!.contains("""
            "sedType" : "P2000",
            "journalpostId" : "***",
            "tildeltEnhetsnr" : "4303",
            "aktoerId" : "1000101917111",
            "oppgaveType" : "JOURNALFORING",
            "rinaSakId" : "148161",
            "hendelseType" : "SENDT",
            "filnavn" : null
        """.trimIndent()))

        // Shutdown
        shutdown(container)
    }

    private fun produserOppgaveHendelser(template: KafkaTemplate<String, String>) {
        val key1 = UUID.randomUUID().toString()
        val data1 = String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/oppgavemeldingP2000_11sifre.json")))
        template.send(OPPGAVE_TOPIC, key1, data1)
    }

    private fun shutdown(container: KafkaMessageListenerContainer<String, String>) {
/*
        mockServer.stop()
*/
        container.stop()
/*        embeddedKafka.kafkaServers.forEach { it.shutdown() }*/
    }

    private fun settOppProducerTemplate(): KafkaTemplate<String, String> {
        return KafkaTemplate(DefaultKafkaProducerFactory(producerProps(embeddedKafka.brokersAsString), StringSerializer(), StringSerializer())).apply {
                defaultTopic = OPPGAVE_TOPIC
            }
    }

    companion object {
        init {
            mockServer = ClientAndServer.startClientAndServer(PortFactory.findFreePort())
            System.setProperty("mockServerport", mockServer.localPort.toString())
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
