package no.nav.eessi.pensjon


import no.nav.eessi.pensjon.listeners.OppgaveListener
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.*
import org.mockserver.model.HttpRequest.request
import org.mockserver.verify.VerificationTimes
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
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
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit
import javax.ws.rs.HttpMethod

private const val OPPGAVE_TOPIC = "privat-eessipensjon-oppgave-v1-test"

private lateinit var mockServer : ClientAndServer

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@SpringBootTest(classes = [ OppgaveIntegrationTest.TestConfig::class])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(count = 1, controlledShutdown = true, topics = [OPPGAVE_TOPIC])
class OppgaveIntegrationTest {

    @Autowired
    lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    lateinit var oppgaveListener: OppgaveListener

    @Test
    fun `Når en oppgavehendelse blir konsumert skal det opprettes en oppgave`() {

        // Vent til kafka er klar
        val container = settOppUtitlityConsumer(OPPGAVE_TOPIC)
        container.start()
        ContainerTestUtils.waitForAssignment(container, embeddedKafka.partitionsPerTopic)

        // Sett opp producer
        val oppgaveProducerTemplate = settOppProducerTemplate(OPPGAVE_TOPIC)

        produserOppgaveHendelser(oppgaveProducerTemplate)

        // Venter på at sedListener skal consumeSedSendt meldingene
        oppgaveListener.getLatch().await(15000, TimeUnit.MILLISECONDS)

        // Verifiserer alle kall
        verifiser()

        // Shutdown
        shutdown(container)
    }

    private fun produserOppgaveHendelser(template: KafkaTemplate<String, String>) {

        val key1 = UUID.randomUUID().toString()
        val data1 = String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/oppgavemeldingP2000.json")))
        template.send(OPPGAVE_TOPIC, key1, data1)

        val key2 = UUID.randomUUID().toString()
        val data2 = String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/oppgavemeldingP2000_feilfil.json")))
        template.send(OPPGAVE_TOPIC, key2, data2)

        val key3 = UUID.randomUUID().toString()
        val data3 = String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/oppgavemeldingP3000_NO.json")))
        template.send(OPPGAVE_TOPIC, key3, data3)

    }

    private fun shutdown(container: KafkaMessageListenerContainer<String, String>) {
        mockServer.stop()
        container.stop()
        embeddedKafka.kafkaServers.forEach { it.shutdown() }
    }

    private fun settOppProducerTemplate(topicNavn: String): KafkaTemplate<String, String> {
        val senderProps = KafkaTestUtils.senderProps(embeddedKafka.brokersAsString)

        val pf = DefaultKafkaProducerFactory<String, String>(senderProps, StringSerializer(), StringSerializer())
        val template = KafkaTemplate<String, String>(pf)
        template.defaultTopic = topicNavn
        return template
    }

    private fun settOppUtitlityConsumer(topicNavn: String): KafkaMessageListenerContainer<String, String> {
        val consumerProperties = KafkaTestUtils.consumerProps("eessi-pensjon-group2", "false", embeddedKafka)
        consumerProperties["auto.offset.reset"] = "earliest"

        val consumerFactory = DefaultKafkaConsumerFactory<String, String>(consumerProperties, StringDeserializer(), StringDeserializer())
        val containerProperties = ContainerProperties(topicNavn)
        val container = KafkaMessageListenerContainer<String, String>(consumerFactory, containerProperties)
        val messageListener = MessageListener<String, String> { record -> println("Konsumerer melding:  $record") }
        container.setupMessageListener(messageListener)
        return container
    }


    companion object {

        init {
            // Start Mockserver in memory
            val lineSeparator = System.lineSeparator()
            val port = randomFrom()
            mockServer = ClientAndServer.startClientAndServer(port)
            System.setProperty("mockServerport", port.toString())

            // Mocker oppgavetjeneste
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.POST)
                            .withPath("/")
                            .withBody("{$lineSeparator"+
                                    "  \"tildeltEnhetsnr\" : \"4808\",$lineSeparator"  +
                                    "  \"opprettetAvEnhetsnr\" : \"9999\",$lineSeparator" +
                                    "  \"journalpostId\" : \"429434380\",$lineSeparator" +
                                    "  \"aktoerId\" : \"2000101917358\",$lineSeparator" +
                                    "  \"beskrivelse\" : \"Utgående P3000_NO - Landsspesifikk informasjon - Norge / Rina saksnr: 24242424\",$lineSeparator" +
                                    "  \"tema\" : \"PEN\",$lineSeparator" +
                                    "  \"oppgavetype\" : \"JFR\",$lineSeparator" +
                                    "  \"prioritet\" : \"NORM\",$lineSeparator" +
                                    "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," + lineSeparator +
                                    "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" + lineSeparator +
                                    "}"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveResponse.json"))))
                    )
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.POST)
                            .withPath("/")
                            .withBody("{$lineSeparator"+
                                    "  \"tildeltEnhetsnr\" : \"4303\",$lineSeparator"  +
                                    "  \"opprettetAvEnhetsnr\" : \"9999\",$lineSeparator" +
                                    "  \"journalpostId\" : \"429434378\",$lineSeparator" +
                                    "  \"aktoerId\" : \"1000101917358\",$lineSeparator" +
                                    "  \"beskrivelse\" : \"Utgående P2000 - Krav om alderspensjon / Rina saksnr: 148161\",$lineSeparator" +
                                    "  \"tema\" : \"PEN\",$lineSeparator" +
                                    "  \"oppgavetype\" : \"JFR\",$lineSeparator" +
                                    "  \"prioritet\" : \"NORM\",$lineSeparator" +
                                    "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," + lineSeparator +
                                    "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" + lineSeparator +
                                    "}"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveResponse.json"))))
                    )
            mockServer.`when`(
                    HttpRequest.request()
                            .withMethod(HttpMethod.POST)
                            .withPath("/")
                            .withBody("{$lineSeparator" +
                                    "  \"tildeltEnhetsnr\" : \"4803\",$lineSeparator" +
                                    "  \"opprettetAvEnhetsnr\" : \"9999\",$lineSeparator" +
                                    "  \"aktoerId\" : \"1000101917358\",$lineSeparator" +
                                    "  \"beskrivelse\" : \"Mottatt vedlegg: etWordDokument.doxc tilhørende RINA sakId: 147666 er i et format som ikke kan journalføres. Be avsenderland/institusjon sende SED med vedlegg på nytt, i støttet filformat ( pdf, jpeg, jpg, png eller tiff )\",$lineSeparator" +
                                    "  \"tema\" : \"PEN\",$lineSeparator" +
                                    "  \"oppgavetype\" : \"BEH_SED\",$lineSeparator" +
                                    "  \"prioritet\" : \"NORM\",$lineSeparator" +
                                    "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," + lineSeparator +
                                    "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" + lineSeparator +
                                    "}"))
                    .respond(HttpResponse.response()
                            .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                            .withStatusCode(HttpStatusCode.OK_200.code())
                            .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveResponse.json"))))
                    )

        }

        private fun randomFrom(from: Int = 2024, to: Int = 55535): Int {
            val random = Random()
            return random.nextInt(to - from) + from
        }
    }

    private fun verifiser() {
        val lineSeparator = System.lineSeparator()

        assertEquals(0, oppgaveListener.getLatch().count)

        // Verifiserer at det har blitt forsøkt å opprette PEN oppgave med aktørid
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.POST)
                        .withPath("/")
                        .withBody("{$lineSeparator" +
                                "  \"tildeltEnhetsnr\" : \"4303\",$lineSeparator" +
                                "  \"opprettetAvEnhetsnr\" : \"9999\",$lineSeparator" +
                                "  \"journalpostId\" : \"429434378\",$lineSeparator" +
                                "  \"aktoerId\" : \"1000101917358\",$lineSeparator" +
                                "  \"beskrivelse\" : \"Utgående P2000 - Krav om alderspensjon / Rina saksnr: 148161\",$lineSeparator" +
                                "  \"tema\" : \"PEN\",$lineSeparator" +
                                "  \"oppgavetype\" : \"JFR\",$lineSeparator" +
                                "  \"prioritet\" : \"NORM\",$lineSeparator" +
                                "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," + lineSeparator +
                                "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" + lineSeparator +
                                "}"),
                VerificationTimes.exactly(1)
        )

        mockServer.verify(
                request()
                        .withMethod(HttpMethod.POST)
                        .withPath("/")
                        .withBody("{$lineSeparator" +
                                "  \"tildeltEnhetsnr\" : \"4303\",$lineSeparator" +
                                "  \"opprettetAvEnhetsnr\" : \"9999\",$lineSeparator" +
                                "  \"journalpostId\" : \"429434378\",$lineSeparator" +
                                "  \"aktoerId\" : \"1000101917358\",$lineSeparator" +
                                "  \"beskrivelse\" : \"Utgående P2000 - Krav om alderspensjon / Rina saksnr: 148161\",$lineSeparator" +
                                "  \"tema\" : \"PEN\",$lineSeparator" +
                                "  \"oppgavetype\" : \"JFR\",$lineSeparator" +
                                "  \"prioritet\" : \"NORM\",$lineSeparator" +
                                "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," + lineSeparator +
                                "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" + lineSeparator +
                                "}"),
                VerificationTimes.exactly(1)
        )
        mockServer.verify(
                request()
                        .withMethod(HttpMethod.POST)
                        .withPath("/")
                        .withBody("{$lineSeparator" +
                                "  \"tildeltEnhetsnr\" : \"4803\",$lineSeparator" +
                                "  \"opprettetAvEnhetsnr\" : \"9999\",$lineSeparator" +
                                "  \"aktoerId\" : \"1000101917358\",$lineSeparator" +
                                "  \"beskrivelse\" : \"Mottatt vedlegg: etWordDokument.doxc tilhørende RINA sakId: 147666 er i et format som ikke kan journalføres. Be avsenderland/institusjon sende SED med vedlegg på nytt, i støttet filformat ( pdf, jpeg, jpg, png eller tiff )\",$lineSeparator" +
                                "  \"tema\" : \"PEN\",$lineSeparator" +
                                "  \"oppgavetype\" : \"BEH_SED\",$lineSeparator" +
                                "  \"prioritet\" : \"NORM\",$lineSeparator" +
                                "  \"fristFerdigstillelse\" : " + "\"" + LocalDate.now().plusDays(1).toString() + "\"," + lineSeparator +
                                "  \"aktivDato\" : " + "\"" + LocalDate.now().toString() + "\"" + lineSeparator +
                                "}"),
                VerificationTimes.exactly(1)
        )

    }

    @TestConfiguration
    class TestConfig{

    }
}
