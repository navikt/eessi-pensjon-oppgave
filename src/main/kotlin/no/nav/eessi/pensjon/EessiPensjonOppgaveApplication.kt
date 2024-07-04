package no.nav.eessi.pensjon

import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Profile

@Profile("prod", "test")
@EnableJwtTokenValidation
@EnableOAuth2Client(cacheEnabled = true)
@SpringBootApplication
class EessiPensjonOppgaveApplication

fun main(args: Array<String>) {
	runApplication<EessiPensjonOppgaveApplication>(*args)
}

