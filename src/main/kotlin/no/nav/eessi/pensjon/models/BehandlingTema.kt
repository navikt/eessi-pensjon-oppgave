package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonValue

enum class BehandlingTema(@JsonValue val kode: String) {
    GJENLEVENDEPENSJON("ab0011"),
    ALDERSPENSJON("ab0254"),
    UFOREPENSJON("ab0194"),
    BARNEP("ab0255"),
    TILBAKEBETALING("ab0007")
}