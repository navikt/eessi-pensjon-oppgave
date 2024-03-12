package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class OppgaveTest {

    @Test
    fun serdes() {
        val json = """
            {
              "antallTreffTotalt":1,
              "oppgaver":[
                {
                  "id":195060,
                  "tildeltEnhetsnr":"4303",
                  "endretAvEnhetsnr":"2012",
                  "opprettetAvEnhetsnr":"9999",
                  "journalpostId":"453863307",
                  "aktoerId":"2356709109499",
                  "beskrivelse":"Utgående P9000 - Svar på forespørsel om informasjon / Rina saksnr: 1447748",
                  "tema":"PEN",
                  "oppgavetype":"JFR",
                  "versjon":"2",
                  "opprettetAv":"eessi-pensjon-oppgave-q2",
                  "endretAv":"Z990724",
                  "prioritet":"NORM",
                  "status":"FERDIGSTILT",
                  "metadata":{
                  },
                  "fristFerdigstillelse":"2024-03-12",
                  "aktivDato":"2024-03-11",
                  "opprettetTidspunkt":"2024-03-11T15:17:59.963+01:00",
                  "ferdigstiltTidspunkt":"2024-03-12T08:27:54.949+01:00",
                  "endretTidspunkt":"2024-03-12T08:27:54.949+01:00"
                }
              ]
            }
        """.trimIndent()

        val oppgaveFraJson = mapJsonToAny<OppgaveResponse>(json)
        JSONAssert.assertEquals(json, oppgaveFraJson.toJson(), false)
    }
}