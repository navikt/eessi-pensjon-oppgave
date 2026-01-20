package no.nav.eessi.pensjon.services.saf

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate

@Component
class SafClient(private val safGraphQlOidcRestTemplate: RestTemplate,
                @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {

    private val logger = LoggerFactory.getLogger(SafClient::class.java)

    private var HentDokumentMetadata: MetricsHelper.Metric
    private var HentDokumentInnhold: MetricsHelper.Metric
    private var HentRinaSakIderFraDokumentMetadata: MetricsHelper.Metric

    init {
        HentDokumentMetadata = metricsHelper.init("HentDokumentMetadata", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        HentDokumentInnhold = metricsHelper.init("HentDokumentInnhold", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED))
        HentRinaSakIderFraDokumentMetadata = metricsHelper.init("HentRinaSakIderFraDokumentMetadata", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    fun hentJournalpost(journalpostId: String) : Journalpost? {

        return HentDokumentInnhold.measure {
            try {
                logger.info("Henter dokumentinnhold for journalpostId:$journalpostId")

                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON
                val response = safGraphQlOidcRestTemplate.exchange(
                    "/",
                    HttpMethod.POST,
                    HttpEntity(SafRequest(journalpostId).toJson(), headers),
                    String::class.java
                )
                val journalPostReponse = mapJsonToAny<Response>(response.body!!).takeIf { true }
                return@measure journalPostReponse?.data?.journalpost

            } catch (ex: Exception) {
                logger.error("En feil oppstod under henting av journalpost fra SAF: $ex")
            }
            null
        }
    }

    data class Data(
        @JsonProperty("journalpost") val journalpost: Journalpost
    )

    data class Response(
        @JsonProperty("data") val data: Data
    )
}
