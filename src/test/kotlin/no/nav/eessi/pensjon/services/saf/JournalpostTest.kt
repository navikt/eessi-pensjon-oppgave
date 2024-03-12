package no.nav.eessi.pensjon.services.saf

import no.nav.eessi.pensjon.models.BehandlingTema
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JournalpostTest {
    @Test
    fun `Serdes Journalpost`(){

        val jsonModel = """
            {
                "journalpostId": "453863307",
                "bruker": {
                  "id": "62526817082",
                  "type": "FNR"
                },
                "tittel": "Utgående P9000 - Svar på forespørsel om informasjon",
                "journalposttype": "U",
                "journalstatus": "UNDER_ARBEID",
                "tema": "PEN",
                "behandlingstema": "ab0011",
                "journalforendeEnhet": "2970",
                "eksternReferanseId": "9c794555-39a3-4e3e-a9f9-844c163a6c5a",
                "tilleggsopplysninger": [
                  {
                    "nokkel": "eessi_pensjon_bucid",
                    "verdi": "1447748"
                  }
                ],
                "datoOpprettet": "2024-03-11T15:17:58"
              }
        """.trimIndent()
        val response = mapJsonToAny<Journalpost>(jsonModel)
        assertEquals("453863307", response.journalpostId)
        assertEquals("62526817082", response.bruker?.id)
        assertEquals(BehandlingTema.GJENLEVENDEPENSJON, response.behandlingstema)
        assertEquals(Tema.PENSJON, response.tema)
        assertEquals(Journalstatus.UNDER_ARBEID, response.journalstatus)
        assertEquals("2024-03-11T15:17:58", response.datoOpprettet.toString())
    }
}