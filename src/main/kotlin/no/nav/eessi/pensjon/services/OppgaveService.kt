package no.nav.eessi.pensjon.services

import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.OppdaterOppgaveMelding
import no.nav.eessi.pensjon.models.Oppgave
import no.nav.eessi.pensjon.models.OppgaveResponse
import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.net.http.HttpHeaders

/**
 * @param metricsHelper Usually injected by Spring Boot, can be set manually in tests - no way to read metrics if not set.
 */
@Service
class OppgaveService(
    private val oppgaveOAuthRestTemplate: RestTemplate,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger = LoggerFactory.getLogger(OppgaveService::class.java)
    private lateinit var opprettoppgave: MetricsHelper.Metric
    private lateinit var oppdateroppgave: MetricsHelper.Metric

    init {
        opprettoppgave = metricsHelper.init("opprettoppgave")
        oppdateroppgave = metricsHelper.init("oppdateroppgave")
    }

    // https://oppgave.nais.preprod.local/?url=https://oppgave.nais.preprod.local/api/swagger.json#/v1oppgaver/opprettOppgave
    fun opprettOppgaveSendOppgaveInn(oppgave: Oppgave) {
        opprettoppgave.measure {

            try {
                val requestBody = mapAnyToJson(oppgave, true)
                logger.info("Oppretter oppgave: $requestBody")

                countEnhet(oppgave.tildeltEnhetsnr)

                val httpEntity = HttpEntity(requestBody)
                val exchange = oppgaveOAuthRestTemplate.exchange("/api/v1/oppgaver", HttpMethod.POST, httpEntity, String::class.java)

                logger.info("""
                    | Opprettet oppgave av type ${oppgave.oppgavetype} med tildeltEnhetsnr:  ${oppgave.tildeltEnhetsnr} 
                    | Result: ${exchange.statusCode}""".trimMargin())
            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under opprettelse av oppgave ex: $ex body: ${ex.responseBodyAsString}")
                throw RuntimeException("En feil oppstod under opprettelse av oppgave ex: ${ex.message} body: ${ex.responseBodyAsString}", ex)
            } catch(ex: Exception) {
                logger.error("En feil oppstod under opprettelse av oppgave ex: $ex")
                throw RuntimeException("En feil oppstod under opprettelse av oppgave ex: ${ex.message}", ex)
            }
        }
    }

    fun oppdaterOppgave(oppgave: OppdaterOppgaveMelding) {
        oppdateroppgave.measure {
            try {
                val requestBody = mapAnyToJson(oppgave, true)
                logger.info("Oppdaterer oppgave: $requestBody")
                val headers = org.springframework.http.HttpHeaders().apply {
                    contentType = MediaType.APPLICATION_JSON
                }
                val exchange = oppgaveOAuthRestTemplate.exchange("/api/v1/oppgaver/${oppgave.id}", HttpMethod.PATCH, HttpEntity(requestBody, headers), String::class.java)
                logger.info("""
                    | Oppdaterer oppgave av med tildeltEnhetsnr:  ${oppgave.tildeltEnhetsnr}, tema: ${oppgave.tema}, status: ${oppgave.status} 
                    | Result: ${exchange.statusCode}""".trimMargin())
            } catch(ex: HttpStatusCodeException) {
                logger.error("En feil oppstod under oppdatering av oppgave ex: $ex body: ${ex.responseBodyAsString}")
                throw RuntimeException("En feil oppstod under oppdatering av oppgave ex: ${ex.message} body: ${ex.responseBodyAsString}", ex)
            } catch(ex: Exception) {
                logger.error("En feil oppstod under oppdatering av oppgave ex: $ex")
                throw RuntimeException("En feil oppstod under oppdatering av oppgave ex: ${ex.message}", ex)
            }
        }
    }
    fun hentAvsluttetOppgave(journalpostId: String): Oppgave? {
        //TODO kalle oppgave for å hente inn oppgave vhja journalpostId
        try {
            val oppgaveResponse = oppgaveOAuthRestTemplate.getForEntity("/api/v1/oppgaver?statuskategori=AVSLUTTET&journalpostId=$journalpostId", String::class.java).body?.let { it ->
                mapJsonToAny<OppgaveResponse>(it).also {
                    logger.info("Hentet avslutte oppgave for journalpostId, antall treff: ${it.antallTreffTotalt}") }
            }
            return oppgaveResponse?.oppgaver?.firstOrNull().also { logger.debug("Hentet oppgave for journalpostId: $journalpostId, oppgave: ${it?.toJson()}")}

        } catch (ex: Exception) {
            logger.error("En feil oppstod under henting av oppgave", ex)
            throw RuntimeException(ex)
        }
    }

    fun hentAapenOppgave(journalpostId: String): Oppgave? {
        //TODO kalle oppgave for å hente inn oppgave vhja journalpostId
        try {
            val oppgaveResponse = oppgaveOAuthRestTemplate.getForEntity("/api/v1/oppgaver?statuskategori=AAPEN&journalpostId=$journalpostId", String::class.java).body?.let { it ->
                mapJsonToAny<OppgaveResponse>(it).also {
                    logger.info("Hentet åpen oppgave for journalpostId, antall treff: ${it.antallTreffTotalt}") }
            }
            return oppgaveResponse?.oppgaver?.firstOrNull().also { logger.debug("Hentet oppgave for journalpostId: $journalpostId, oppgave: ${it?.toJson()}")}

        } catch (ex: Exception) {
            logger.error("En feil oppstod under henting av oppgave", ex)
            throw RuntimeException(ex)
        }
    }

    fun countEnhet(tildeltEnhetsnr: String?) {
        try {
            Metrics.counter("TildeltEnhet",   "enhet", tildeltEnhetsnr).increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet på enhet: $tildeltEnhetsnr")
        }
    }
}

