package no.nav.eessi.pensjon.config

import io.micrometer.core.instrument.MeterRegistry
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
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
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

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

    internal class IOExceptionRetryInterceptor : ClientHttpRequestInterceptor {
        private val logger = LoggerFactory.getLogger(IOExceptionRetryInterceptor::class.java)

        override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution) =
            withRetries { execution.execute(request, body) }

        private fun <T> withRetries(maxAttempts: Int = 3, waitTime: Long = 1L, timeUnit: TimeUnit = TimeUnit.SECONDS, func: () -> T): T {
            var failException: Throwable? = null
            var count = 0
            while (count < maxAttempts) {
                try {
                    return func.invoke()
                } catch (ex: IOException) { // Dette bør ta seg av IOException - som typisk skjer der som det er nettverksissues.
                    count++
                    logger.warn("Attempt $count failed with ${ex.message} caused by ${ex.cause}")
                    failException = ex
                    Thread.sleep(timeUnit.toMillis(waitTime))
                }
            }
            logger.warn("Giving up after $count attempts.")
            throw failException!!
        }
    }
}
