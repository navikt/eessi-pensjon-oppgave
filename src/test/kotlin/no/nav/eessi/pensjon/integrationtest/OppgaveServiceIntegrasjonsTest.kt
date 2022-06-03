package no.nav.eessi.pensjon.integrationtest

import com.ninjasquad.springmockk.MockkBean
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.EessiPensjonOppgaveApplicationTest
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.OppgaveMelding
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.services.OppgaveService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockserver.integration.ClientAndServer
import org.mockserver.socket.PortFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.util.*

private lateinit var mockServer: ClientAndServer

private const val OPPGAVE_TOPIC = "privat-eessipensjon-oppgave-v1-test"

@ActiveProfiles("integrationtest")
@SpringBootTest(classes = [EessiPensjonOppgaveApplicationTest::class], value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@EmbeddedKafka(count = 1, controlledShutdown = true, topics = [OPPGAVE_TOPIC], brokerProperties = ["log.dir=out/embedded-kafka1"])

class OppgaveServiceIntegrasjonsTest {

    @Autowired
    lateinit var oppgaveservice: OppgaveService

    @MockkBean
    lateinit var oppgaveOAuthRestTemplate: RestTemplate



//    init {
//        val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
//        metricsHelper.init("opprettOppgave")
//        oppgaveservice = OppgaveService(restTemplate, metricsHelper)
//    }

        init {
            // Start Mockserver in memory
            System.lineSeparator()
            val port = PortFactory.findFreePort()
            mockServer = ClientAndServer.startClientAndServer(port)
            System.setProperty("mockServerport", port.toString())
        }


    @Test
    fun `Ved oppretting av oppgave for en ident som ikke finnes i PDL så skal det kastes`() {

        val oppgavemelding = OppgaveMelding(null, SedType.P11000, null, "", null, "BEHANDLE_SED", "654654321", HendelseType.MOTTATT, "bogus.doc")


        every { oppgaveOAuthRestTemplate.exchange("/", HttpMethod.POST, any(), String::class.java) } throws HttpServerErrorException(HttpStatus.BAD_GATEWAY)
        val messageException =
            assertThrows<RuntimeException> {
                oppgaveservice.opprettOppgaveSendOppgaveInn(oppgavemelding)
            }

        messageException.message?.let { assert(it.contains("En feil oppstod under opprettelse av oppgave")) }

        verify (exactly = 1) { oppgaveOAuthRestTemplate.exchange("/", HttpMethod.POST, any(), String::class.java) }
    }

}