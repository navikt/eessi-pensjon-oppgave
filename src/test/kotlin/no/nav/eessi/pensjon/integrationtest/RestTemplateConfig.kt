package no.nav.eessi.pensjon.integrationtest

import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import org.mockserver.socket.PortFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

private var mockServerPort = PortFactory.findFreePort()

@Profile("integrationtest")
@Configuration
class RestTemplateConfig {

    @Value("\${oppgave.oppgaver.url}")
    lateinit var oppgaveUrl: String

    @Bean
    fun oppgaveOAuthRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .baseUri(oppgaveUrl)
                .additionalInterceptors(
                        RequestIdHeaderInterceptor(),
                        RequestResponseLoggerInterceptor(),
                )
                .build().apply {
                    // HttpComponentsClientHttpRequestFactory handles MockServer's Netty keep-alive/chunked
                    // responses more reliably than the JDK-based SimpleClientHttpRequestFactory.
                    requestFactory = BufferingClientHttpRequestFactory(HttpComponentsClientHttpRequestFactory())
                }
    }
}
