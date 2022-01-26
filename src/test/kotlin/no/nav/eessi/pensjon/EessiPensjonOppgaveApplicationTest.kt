package no.nav.eessi.pensjon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Profile

@Profile("integrationtest")
@SpringBootApplication
class EessiPensjonOppgaveApplicationTest

fun main(args: Array<String>) {
	runApplication<EessiPensjonJournalforingApplication>(*args)
}

