package no.nav.eessi.pensjon.integrationtest

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.ninjasquad.springmockk.MockkBean
import no.nav.eessi.pensjon.EessiPensjonOppgaveApplicationTest
import no.nav.eessi.pensjon.listeners.OppgaveListener
import no.nav.eessi.pensjon.services.gcp.GcpStorageService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.*
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.JsonBody.json
import org.mockserver.model.StringBody.subString
import org.mockserver.socket.PortFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
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

private const val ID_OG_FORDELING = "4303"
private const val NFP_UTLAND_OSLO = "4803"
private const val UFORE_UTLAND = "4475"
private var mockServerPort = PortFactory.findFreePort()

@SpringBootTest(classes = [EessiPensjonOppgaveApplicationTest::class ], value = ["SPRING_PROFILES_ACTIVE", "integrationtest="])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    topics = [OPPGAVE_TOPIC]
)
class OppgaveIntegrationTest {

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    lateinit var embeddedKafka: EmbeddedKafkaBroker

    @MockkBean(relaxed = true)
    lateinit var gcpStorageService: GcpStorageService

    @Autowired
    lateinit var oppgaveListener: OppgaveListener

    lateinit var container: KafkaMessageListenerContainer<String, String>

    lateinit var oppgaveProducerTemplate: KafkaTemplate<String, String>

    val listAppender = ListAppender<ILoggingEvent>()
    val logger: Logger = LoggerFactory.getLogger("no.nav.eessi") as Logger
    val today = LocalDate.now().toString()
    val tomorrrow = LocalDate.now().plusDays(1).toString()

    companion object {
        init {
            // mockserver krever at denne er satt under oppstart
            System.setProperty("mockServerport", mockServerPort.toString())
            // Start Mockserver in memory
            mockServer = ClientAndServer.startClientAndServer(mockServerPort)
            mockServer.`when`(
                HttpRequest.request()
                    .withMethod("GET")
                    .withPath("/api/v1/oppgaver")
            ).respond(
                HttpResponse.response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody("")
            )
        }
    }

    @BeforeEach
    fun setup() {

        listAppender.start()
        logger.addAppender(listAppender)

        container = initConsumer()
        container.start()
        Thread.sleep(5000) // wait a bit for the container to start
        ContainerTestUtils.waitForAssignment(container, embeddedKafka.partitionsPerTopic)

        oppgaveProducerTemplate = settOppProducerTemplate()
    }

    @AfterEach
    fun after() {
        container.stop()
        listAppender.stop()
        mockServer.reset()
    }

    @Test
    fun `Gitt det mottas en P2000 oppgavehendelse så skal den lage en tilsvarende oppgave`() {
        val json = "src/test/resources/oppgave/opprettOppgaveResponse.json"
        medRequest("P2000", mockOppgave(ID_OG_FORDELING, "429434311", "1000101917111", "JFR"), HttpMethod.POST, json)

        sendMessageWithDelay(oppgaveProducerTemplate, "src/test/resources/oppgave/oppgavemeldingP2000.json")

        OppgaveMeldingVerification("1000101917111")
            .medAktivDato(today)
            .medFristFerdigstillelse(tomorrrow)
            .medJournalpostId("429434311")
            .medtildeltEnhetsnr(ID_OG_FORDELING)
            .medBeskrivelse("Utgående P2000 - Krav om alderspensjon / Rina saksnr: 148161")
            .medOppgavetype("JFR")
    }

    @Test
    fun `Gitt det mottas en oppgavehendelse fra pdl-produsent så skal den lage en tilsvarende behandlesed oppgave`() {
        val response = "src/test/resources/oppgave/opprettOppgaveResponse.json"
        val body = "Avvik i utenlandsk ID i PDL. I RINA saksnummer 3442342342342"

        medRequest("/", subString(body), HttpMethod.POST, response)

        val meldingFraPdljson = """
            {
            "sedType" : null,
            "journalpostId" : null,
            "tildeltEnhetsnr" : "4303",
            "aktoerId" : "1000101917111",
            "oppgaveType" : "PDL",
            "rinaSakId" : "3442342342342",
            "hendelseType" : "MOTTATT",
            "filnavn" : null
            }
        """.trimIndent()

        // Sende meldinger på kafka
        sendMessageFraJsonWithDelay(oppgaveProducerTemplate, meldingFraPdljson)

        OppgaveMeldingVerification("1000101917111")
            .medBeskrivelse("Avvik i utenlandsk ID i PDL. I RINA saksnummer 3442342342342 er det mottatt en SED med utenlandsk ID som er forskjellig fra den som finnes i PDL. Avklar hvilken som er korrekt eller om det skal legges til en utenlandsk ID.")
            .medOppgavetype("BEH_SED")
            .medtildeltEnhetsnr(ID_OG_FORDELING)
            .medAktivDato(today)

    }

    @Test
    fun `Gitt en R005 oppgavehendelse så skal den lage en tilsvarende oppgave`() {
        val response = "src/test/resources/oppgave/opprettOppgaveResponse.json"
        val mockOppgave = mockOppgave("4808", "429434380", "2000101917555", "JFR")

        medRequest("P2000", mockOppgave, HttpMethod.POST, response)

        sendMessageWithDelay(oppgaveProducerTemplate, "src/test/resources/oppgave/oppgavemeldingR005.json")
        OppgaveMeldingVerification("2000101917555")
            .medAktivDato(today)
            .medFristFerdigstillelse(tomorrrow)
            .medBeskrivelse("Utgående R005 - Anmodning om motregning i etterbetalinger (foreløpig eller endelig) / Rina saksnr: 24242424")
            .medOppgavetype("JFR")
            .medtildeltEnhetsnr("4808")
            .medJournalpostId("429434380")
    }

    @Test
    fun `Gitt en P2000 oppgavehendelse med feil så skal den lage en tilsvarende oppgave`() {
        val response = "src/test/resources/oppgave/opprettOppgaveResponse.json"
        val mockOppgave = mockOppgaveBehandleSed(NFP_UTLAND_OSLO, "1000101917222")

        medRequest("RINA sakId: 147666 mangler filnavn", mockOppgave, HttpMethod.POST, response)

        sendMessageWithDelay(oppgaveProducerTemplate, "src/test/resources/oppgave/oppgavemeldingP2000_feilfil.json")
        OppgaveMeldingVerification("1000101917222")
            .medAktivDato(today)
            .medFristFerdigstillelse(tomorrrow)
            .medBeskrivelse("Mottatt vedlegg: etWordDokument.doxc tilhørende RINA sakId: 147666 mangler filnavn eller er i et format som ikke kan journalføres. Be avsenderland/institusjon sende SED med vedlegg på nytt, i støttet filformat ( pdf, jpeg, jpg, png eller tiff ) og filnavn angitt")
            .medOppgavetype("BEH_SED")
            .medtildeltEnhetsnr(NFP_UTLAND_OSLO)
    }

    @Test
    fun `Gitt en P2200 oppgavehendelse så skal den lage en tilsvarende oppgave`() {

        val response = "src/test/resources/oppgave/opprettOppgaveResponse.json"
        medRequest("P2200", mockOppgave(UFORE_UTLAND, "429434322" , "1000101917333",  "BEH_SED"), HttpMethod.POST, response)

        sendMessageWithDelay(oppgaveProducerTemplate, "src/test/resources/oppgave/oppgavemeldingP2200.json")
        OppgaveMeldingVerification("1000101917333")
            .medAktivDato(today)
            .medFristFerdigstillelse(tomorrrow)
            .medBeskrivelse("Det er mottatt P2200 - Krav om uførepensjon, med tilhørende RINA sakId: 148161")
            .medtildeltEnhetsnr(UFORE_UTLAND)
            .medOppgavetype("BEH_SED")
            .medJournalpostId("429434322")
    }

    @Test
    fun `Gitt en P3000 oppgavehendelse så skal den lage en tilsvarende oppgave`() {
        val mockOppgave = mockOppgave("4808", "429434333", "2000101917444", "JFR")

        medRequest("P3000_NO", mockOppgave, HttpMethod.POST, "src/test/resources/oppgave/opprettOppgaveResponse.json")

        sendMessageWithDelay(oppgaveProducerTemplate, "src/test/resources/oppgave/oppgavemeldingP3000_NO.json")
        OppgaveMeldingVerification("2000101917444")
            .medAktivDato(today)
            .medFristFerdigstillelse(tomorrrow)
            .medBeskrivelse("Utgående P3000_NO - Landsspesifikk informasjon - Norge / Rina saksnr: 24242424")
            .medOppgavetype("JFR")
            .medtildeltEnhetsnr("4808")
    }

    private fun mockOppgave(tildeltEnhetsnr: String, journalpostId: String, aktoerId: String, oppgavetype: String): Body<String> {
        return json("""{
                  "tildeltEnhetsnr" : "$tildeltEnhetsnr",
                  "journalpostId" :  "$journalpostId",
                  "aktoerId" : "$aktoerId",
                  "tema" : "PEN",
                  "oppgavetype" : "$oppgavetype",
                  "prioritet" : "NORM",
                  "fristFerdigstillelse" : "$tomorrrow",
                  "aktivDato" : "$today"
            }""".trimIndent())
    }
    private fun mockOppgaveBehandleSed(tildeltEnhetsnr: String, aktoerId: String): Body<String> {
        return json("""{
                  "tildeltEnhetsnr" : "$tildeltEnhetsnr",                  
                  "aktoerId" : "$aktoerId",
                  "tema" : "PEN",
                  "oppgavetype" : "BEH_SED",
                  "prioritet" : "NORM",
                  "fristFerdigstillelse" : "$tomorrrow",
                  "aktivDato" : "$today"                  
            }""".trimIndent())
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
        template.sendDefault(String(Files.readAllBytes(Paths.get(messagePath)))).get(10, TimeUnit.SECONDS)
        oppgaveListener.getLatch().await(10, TimeUnit.SECONDS)
        Thread.sleep(1000)
    }

    private fun sendMessageFraJsonWithDelay(template: KafkaTemplate<String, String>, message: String) {
        template.sendDefault(message).get(10, TimeUnit.SECONDS)
        oppgaveListener.getLatch().await(10, TimeUnit.SECONDS)
        Thread.sleep(10000)
    }

    private fun settOppProducerTemplate(): KafkaTemplate<String, String> {
        return KafkaTemplate<String, String>(DefaultKafkaProducerFactory(KafkaTestUtils.producerProps(embeddedKafka.brokersAsString))).apply {
            setDefaultTopic( OPPGAVE_TOPIC )
        }
    }

    private fun initConsumer(): KafkaMessageListenerContainer<String, String> {
        val consumerProperties = KafkaTestUtils.consumerProps(
            embeddedKafka,
            UUID.randomUUID().toString(),
            false
        )
        consumerProperties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"

        val consumerFactory =  DefaultKafkaConsumerFactory(consumerProperties, StringDeserializer(), StringDeserializer())

        return KafkaMessageListenerContainer(consumerFactory, ContainerProperties(OPPGAVE_TOPIC)).apply {
            setupMessageListener(MessageListener<String, String> { record -> logger.info("Oppgaveintegrasjonstest konsumerer melding:  $record") })
        }
    }

    fun medRequest(beskrivelse: String, body: Body<String>, httpMethod: HttpMethod, bucLocation: String) = apply {
        mockServer.`when`(
            request()
                .withMethod(httpMethod.name())
                .withBody(subString(beskrivelse))
                .withBody(body)
        )
            .respond(
                response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody(String(Files.readAllBytes(Paths.get(bucLocation))))
            )
    }
}
