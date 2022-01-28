package no.nav.eessi.pensjon.integrationtest

import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Profile("integrationtest")
@Configuration
class RestTemplateConfig {

    @Value("\${oppgave.oppgaver.url}")
    lateinit var oppgaveUrl: String

    @Bean
    fun oppgaveOAuthRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .rootUri(oppgaveUrl)
                .additionalInterceptors(
                        RequestIdHeaderInterceptor(),
                        RequestResponseLoggerInterceptor(),
                )
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }

}
