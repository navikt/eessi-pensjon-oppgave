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
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.util.*

@Profile("prod", "test")
@Configuration
class RestTemplateConfig(
    private val clientConfigurationProperties: ClientConfigurationProperties,
    private val oAuth2AccessTokenService: OAuth2AccessTokenService?,
    private val meterRegistry: MeterRegistry
) {

    @Value("\${oppgave.oppgaver.url}")
    lateinit var oppgaveUrl: String

    @Value("\${SAF_GRAPHQL_URL}")
    lateinit var graphQlUrl: String

    private val logger = LoggerFactory.getLogger(RestTemplateConfig::class.java)

    @Bean("oppgaveOAuthRestTemplate")
    internal fun oppgaveOAuthRestTemplate() = restTemplate(oppgaveUrl, oAuth2BearerTokenInterceptor(clientProperties("oppgave-credentials"), oAuth2AccessTokenService!!))

    @Bean("safGraphQlOidcRestTemplate")
    fun safGraphQlOidcRestTemplate() = restTemplate(graphQlUrl, oAuth2BearerTokenInterceptor(clientProperties("saf-credentials"), oAuth2AccessTokenService!!))

    private fun restTemplate(url: String, tokenInterceptor: ClientHttpRequestInterceptor?, defaultErrorHandler: ResponseErrorHandler = DefaultResponseErrorHandler()) : RestTemplate {
        logger.info("init restTemplate: $url")
        return RestTemplateBuilder()
            .rootUri(url)
            .errorHandler(defaultErrorHandler)
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                IOExceptionRetryInterceptor(),
                RequestCountInterceptor(meterRegistry),
                RequestInterceptor(),
                RequestResponseLoggerInterceptor(),
                tokenInterceptor
            )
            .build().apply {
                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
            }
    }

    private fun clientProperties(oAuthKey: String): ClientProperties {
        return Optional.ofNullable(clientConfigurationProperties.registration[oAuthKey])
            .orElseThrow { RuntimeException("could not find oauth2 client config for example-onbehalfof") }
    }

    private fun oAuth2BearerTokenInterceptor( clientProperties: ClientProperties, oAuth2AccessTokenService: OAuth2AccessTokenService ): ClientHttpRequestInterceptor {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray?, execution: ClientHttpRequestExecution ->
            val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
            response.access_token?.let { request.headers.setBearerAuth(it) }
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
