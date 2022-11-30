package no.nav.eessi.pensjon.config

import io.micrometer.core.instrument.MeterRegistry
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.metrics.RequestCountInterceptor
import no.nav.eessi.pensjon.shared.retry.IOExceptionRetryInterceptor
import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpRequest
import org.springframework.http.MediaType
import org.springframework.http.client.*
import org.springframework.web.client.RestTemplate
import java.util.*

@Profile("prod", "test")
@Configuration
class RestTemplateConfig(private val meterRegistry: MeterRegistry) {

    @Value("\${oppgave.oppgaver.url}")
    lateinit var oppgaveUrl: String

    private val logger = LoggerFactory.getLogger(RestTemplateConfig::class.java)

    @Bean
    internal fun oppgaveOAuthRestTemplate(templateBuilder: RestTemplateBuilder, clientConfigurationProperties: ClientConfigurationProperties, oAuth2AccessTokenService: OAuth2AccessTokenService): RestTemplate {
        val clientProperties = clientConfigurationProperties.registration.getOrElse("oppgave-credentials") {
            throw IllegalStateException("Mangler Oauth2Client oppgave-credentials")
        }
        return templateBuilder
            .rootUri(oppgaveUrl)
            .additionalInterceptors(
                IOExceptionRetryInterceptor(),
                oAuthBearerTokenInterceptor(oAuth2AccessTokenService, clientProperties),
                RequestIdHeaderInterceptor(),
                RequestCountInterceptor(meterRegistry),
                RequestInterceptor(),
                RequestResponseLoggerInterceptor()
            )
            .build().apply {
                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
            }
    }

    private fun oAuthBearerTokenInterceptor(oAuth2AccessTokenService: OAuth2AccessTokenService, clientProperties: ClientProperties): ClientHttpRequestInterceptor {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray?, execution: ClientHttpRequestExecution ->
            logger.debug("accesstoken før: ${request.headers}")
            val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
            request.headers.setBearerAuth(response.accessToken)
            logger.debug("legger til accesstoken: ${response.accessToken}")
            execution.execute(request, body!!)
        }
    }

    internal class RequestInterceptor : ClientHttpRequestInterceptor {
        override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
            request.headers["X-Correlation-ID"] = UUID.randomUUID().toString()
            request.headers["Content-Type"] = MediaType.APPLICATION_JSON.toString()
            return execution.execute(request, body)
        }
    }

}
