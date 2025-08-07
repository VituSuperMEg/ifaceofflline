package com.example.iface_offilne.cities

object Ceara {
    val cidades = listOf<String>("Selecione a Cidade", "Abaiara", "Acarape", "Acopiara", "Aiuaba", "Altaneira", "Alto Santo", "Amontada", "Aquiraz", "Aracati", "Fortaleza")
}

object CodigoIBge {
    private val codigos = mapOf(
        "Abaiara" to "2300101",
        "Acarape" to "2300150",
        "Fortaleza" to "2304400"
    )

    operator fun invoke(nome: String?): String? {
        return codigos[nome]
    }
}
