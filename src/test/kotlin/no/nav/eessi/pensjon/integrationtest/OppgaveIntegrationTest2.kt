package no.nav.eessi.pensjon.integrationtest

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import no.nav.eessi.pensjon.listeners.OppgaveListener
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockserver.integration.ClientAndServer
import org.mockserver.matchers.MatchType
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.HttpStatusCode
import org.mockserver.model.JsonBody.json
import org.mockserver.model.StringBody.subString
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
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit

private const val OPPGAVE_TOPIC = "privat-eessipensjon-oppgave-v1-test"

private lateinit var mockServer: ClientAndServer

@SpringBootTest(value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [OPPGAVE_TOPIC] ,
    brokerProperties= ["log.dir=out/kafkatestout/oppgaveintegrationtest-ChangeMe1"]
)

class OppgaveIntegrationTest2 {

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    lateinit var oppgaveListener: OppgaveListener

    lateinit var container: KafkaMessageListenerContainer<String, String>

    lateinit var oppgaveProducerTemplate: KafkaTemplate<String, String>

    val listAppender = ListAppender<ILoggingEvent>()
    val deugLogger: Logger = LoggerFactory.getLogger("no.nav.eessi") as Logger
    val today = LocalDate.now().toString()
    val tomorrrow = LocalDate.now().plusDays(1).toString()

    @BeforeEach
    fun setup() {
        listAppender.start()
        deugLogger.addAppender(listAppender)

        container = initConsumer(OPPGAVE_TOPIC)
        container.start()
        Thread.sleep(3000); // wait a bit for the container to start
        ContainerTestUtils.waitForAssignment(container, embeddedKafka.partitionsPerTopic)

        oppgaveProducerTemplate = settOppProducerTemplate(OPPGAVE_TOPIC)

    }

    @AfterEach
    fun after() {
        container.stop()
        listAppender.stop()
    }

    @Test
    fun `Gitt en P2000 oppgavehendelse med feil så skal den lage en tilsvarende oppgave`() {

        sendMessageWithDelay(oppgaveProducerTemplate, "src/test/resources/oppgave/oppgavemeldingP2000_feilfil.json")
        OppgaveMeldingVerification("1000101917222")
            .medAktivDato(today)
            .medFristFerdigstillelse(tomorrrow)
            .medBeskrivelse("Mottatt vedlegg: etWordDokument.doxc tilhørende RINA sakId: 147666 mangler filnavn eller er i et format som ikke kan journalføres. Be avsenderland/institusjon sende SED med vedlegg på nytt, i støttet filformat ( pdf, jpeg, jpg, png eller tiff ) og filnavn angitt")
            .medOppgavetype("BEH_SED")
            .medtildeltEnhetsnr("4803")
    }

    @Test
    fun `Gitt en P2200 oppgavehendelse så skal den lage en tilsvarende oppgave`() {

        sendMessageWithDelay(oppgaveProducerTemplate, "src/test/resources/oppgave/oppgavemeldingP2200.json")
        OppgaveMeldingVerification("1000101917333")
            .medAktivDato(today)
            .medFristFerdigstillelse(tomorrrow)
            .medBeskrivelse("Det er mottatt P2200 - Krav om uførepensjon, med tilhørende RINA sakId: 148161")
            .medtildeltEnhetsnr("4475")
            .medOppgavetype("BEH_SED")
            .medJournalpostId("429434322")
    }

    @Test
    fun `Gitt en P3000 oppgavehendelse så skal den lage en tilsvarende oppgave`() {

        sendMessageWithDelay(oppgaveProducerTemplate, "src/test/resources/oppgave/oppgavemeldingP3000_NO.json")
        OppgaveMeldingVerification("2000101917444")
            .medAktivDato(today)
            .medFristFerdigstillelse(tomorrrow)
            .medBeskrivelse("Utgående P3000_NO - Landsspesifikk informasjon - Norge / Rina saksnr: 24242424")
            .medOppgavetype("JFR")
            .medtildeltEnhetsnr("4808")
    }

    inner class OppgaveMeldingVerification(aktoerId: String): OppgaveMeldingVerificationMedType(aktoerId, "aktoerId")

    open inner class OppgaveMeldingVerificationMedType(value: String, keyword: String) {
        val logsList: List<ILoggingEvent> = listAppender.list
        val meldingFraLog =
            logsList.find { message ->
                message.message.contains("Oppretter oppgave:") && message.message.contains(
                    "\"$keyword\" : \"$value\""
                )
            }?.message
        fun medtildeltEnhetsnr(melding: String) = apply {
            assertTrue(meldingFraLog!!.contains("\"tildeltEnhetsnr\" : \"$melding\""))
        }
        fun medBeskrivelse(melding: String) = apply {
            assertTrue(meldingFraLog!!.contains("\"beskrivelse\" : \"$melding\""))
        }
        fun medOppgavetype(melding: String) = apply {
            assertTrue(meldingFraLog!!.contains("\"oppgavetype\" : \"$melding\""))
        }
        fun medFristFerdigstillelse(melding: String) = apply {
            assertTrue(meldingFraLog!!.contains("\"fristFerdigstillelse\" : \"$melding\""))
        }
        fun medAktivDato(melding: String) = apply {
            assertTrue(meldingFraLog!!.contains("\"aktivDato\" : \"$melding\""))
        }
        fun medJournalpostId(melding: String) = apply {
            assertTrue(meldingFraLog!!.contains("\"journalpostId\" : \"$melding\""))
        }
    }

    private fun sendMessageWithDelay(template: KafkaTemplate<String, String>, messagePath: String) {
        template.sendDefault(String(Files.readAllBytes(Paths.get(messagePath)))).get(10L, TimeUnit.SECONDS)
        oppgaveListener.getLatch().await(10, TimeUnit.SECONDS)
        Thread.sleep(10000)
    }

    private fun sendMessageFraJsonWithDelay(template: KafkaTemplate<String, String>, message: String) {
        template.sendDefault(message).get(10L, TimeUnit.SECONDS)
        oppgaveListener.getLatch().await(10, TimeUnit.SECONDS)
        Thread.sleep(10000)
    }

    private fun settOppProducerTemplate(topicNavn: String): KafkaTemplate<String, String> {
        val senderProps = KafkaTestUtils.producerProps(embeddedKafka.brokersAsString)

        val pf = DefaultKafkaProducerFactory<String, String>(senderProps, StringSerializer(), StringSerializer())
        val template = KafkaTemplate<String, String>(pf)
        template.defaultTopic = topicNavn
        return template
    }

    private fun initConsumer(topicNavn: String): KafkaMessageListenerContainer<String, String> {
        val consumerProperties = KafkaTestUtils.consumerProps(
            UUID.randomUUID().toString(),
            "false",
            embeddedKafka
        )
        consumerProperties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        consumerProperties[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1

        val consumerFactory =
            DefaultKafkaConsumerFactory(consumerProperties, StringDeserializer(), StringDeserializer())

        return KafkaMessageListenerContainer(consumerFactory, ContainerProperties(topicNavn)).apply {
            setupMessageListener(MessageListener<String, String> { record -> println("Oppgaveintegrasjonstest konsumerer melding:  $record") })
        }
    }

    companion object {
        init {
            // Start Mockserver in memory
            val port = PortFactory.findFreePort()
            mockServer = ClientAndServer.startClientAndServer(port)
            System.setProperty("mockServerport", port.toString())

            val today = LocalDate.now()
            val tomorrrow = LocalDate.now().plusDays(1).toString()
            mockServer.`when`(
                request()
                    .withMethod("GET")
                    .withQueryStringParameter("grant_type", "client_credentials"))
                .respond(response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/sts/STStoken.json"))))
                )

            // Mocker STS service discovery
            mockServer.`when`(
                request()
                    .withMethod("GET")
                    .withPath("/.well-known/openid-configuration"))
                .respond(response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody(
                        "{\n" +
                                "  \"issuer\": \"http://localhost:$port\",\n" +
                                "  \"token_endpoint\": \"http://localhost:$port/rest/v1/sts/token\",\n" +
                                "  \"exchange_token_endpoint\": \"http://localhost:$port/rest/v1/sts/token/exchange\",\n" +
                                "  \"jwks_uri\": \"http://localhost:$port/rest/v1/sts/jwks\",\n" +
                                "  \"subject_types_supported\": [\"public\"]\n" +
                                "}"
                    )
                )

            // Mocker oppgavetjeneste
            mockServer.`when`(
                request()
                    .withMethod("POST")
                    .withPath("/")
                    .withBody(subString("P3000_NO"))
                    .withBody(
                        json(
                            """{
                                  "tildeltEnhetsnr" : "4808",
                                  "opprettetAvEnhetsnr" : "9999",
                                  "journalpostId" : "429434333",
                                  "aktoerId" : "2000101917444",
                                  "tema" : "PEN",
                                  "oppgavetype" : "JFR",
                                  "prioritet" : "NORM",
                                  "fristFerdigstillelse" : "$tomorrrow",
                                  "aktivDato" : "$today"
                            }""".trimIndent() + MatchType.ONLY_MATCHING_FIELDS
                        )
                    )
            )
                .respond(
                    response()
                        .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveResponse.json"))))
                )

            mockServer.`when`(
                request()
                    .withMethod("POST")
                    .withPath("/")
                    .withBody(subString("P2200"))
                    .withBody(json("""{
                          "tildeltEnhetsnr" : "4475",
                          "opprettetAvEnhetsnr" : "9999",
                          "journalpostId" : "429434322",
                          "aktoerId" : "1000101917333",
                          "tema" : "PEN",
                          "oppgavetype" : "BEH_SED",
                          "prioritet" : "NORM",
                          "fristFerdigstillelse" : "$tomorrrow",
                          "aktivDato" : "$today"
                    }""".trimIndent()))
            )
                .respond(
                    response()
                        .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveResponse.json"))))
                )

            mockServer.`when`(
                request()
                    .withMethod("POST")
                    .withPath("/")
                    .withBody(subString("RINA sakId: 147666 mangler filnavn"))
                    .withBody(json(
                        """{
                          "tildeltEnhetsnr" : "4803",
                          "opprettetAvEnhetsnr" : "9999",
                          "aktoerId" : "1000101917222",
                          "tema" : "PEN",
                          "oppgavetype" : "BEH_SED",
                          "prioritet" : "NORM",
                          "fristFerdigstillelse" : "$tomorrrow",
                          "aktivDato" : "$today"
                       }""".trimIndent()
                    ))
            )
                .respond(
                    response()
                        .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/oppgave/opprettOppgaveResponse.json"))))
                )

        }
    }
}
