package no.nav.eessi.pensjon.services

class JournalposterSomInneholderFeil {

    companion object{
        fun feilendeJournalposterTest(): List<String> {
            return listOf("453863307","453863345")
        }

        fun feilendeJournalposterProd(): List<String> {
            return listOf("550754036","550765832")
        }
    }
}