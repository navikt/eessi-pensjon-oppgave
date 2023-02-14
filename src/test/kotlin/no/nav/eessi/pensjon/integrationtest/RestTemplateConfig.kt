package no.nav.eessi.pensjon.integrationtest

import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.io.HttpClientConnectionManager
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder
import org.apache.hc.core5.ssl.SSLContexts
import org.apache.hc.core5.ssl.TrustStrategy
import org.mockserver.socket.PortFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext

private var mockServerPort = PortFactory.findFreePort()

@Profile("integrationtest")
@Configuration
class RestTemplateConfig {

    @Value("\${oppgave.oppgaver.url}")
    lateinit var oppgaveUrl: String

//    @Bean
//    fun oppgaveOAuthRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
//        return templateBuilder
//                .rootUri(oppgaveUrl)
//                .additionalInterceptors(
//                        RequestIdHeaderInterceptor(),
//                        RequestResponseLoggerInterceptor(),
//                )
//                .build().apply {
//                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
//                }
//    }

    @Bean
    fun euxClientCredentialsResourceRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        val acceptingTrustStrategy = TrustStrategy { _: Array<X509Certificate?>?, _: String? -> true }

        val sslcontext: SSLContext = SSLContexts.custom()
            .loadTrustMaterial(null, acceptingTrustStrategy)
            .build()
        val sslSocketFactory: SSLConnectionSocketFactory = SSLConnectionSocketFactoryBuilder.create()
            .setSslContext(sslcontext)
            .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
            .build()
        val connectionManager: HttpClientConnectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            .setSSLSocketFactory(sslSocketFactory)
            .build()
        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .build()

        val customRequestFactory = HttpComponentsClientHttpRequestFactory()
        customRequestFactory.httpClient = httpClient

        return RestTemplateBuilder()
            .rootUri("https://localhost:${mockServerPort}")
            .build().apply {
                requestFactory = customRequestFactory
            }
    }

}
