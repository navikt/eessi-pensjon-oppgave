package no.nav.eessi.pensjon.security.sts

/**
 * Documentation at https://confluence.adeo.no/display/KES/STS+-+Brukerdokumentasjon
 * Example implementation at http://stash.devillo.no/projects/KES/repos/eksempelapp-token/browse
 */

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

lateinit var STS_URL: String
lateinit var SERVICEUSER_USERNAME: String
lateinit var SERVICEUSER_PASSWORD: String

/**
 * Denne STS tjenesten benyttes ved kall mot gamle tjenester sånn som PersonV3
 */
@Component
class STSClientConfig {

    @Value("\${securitytokenservice.url}")
    fun setSTSurl(url: String) {
        STS_URL = url
    }

    @Value("\${srvusername}")
    fun setUsername(username: String) {
        SERVICEUSER_USERNAME = username
    }

    @Value("\${srvpassword}")
    fun setPassword(password: String) {
        SERVICEUSER_PASSWORD = password
    }
}

