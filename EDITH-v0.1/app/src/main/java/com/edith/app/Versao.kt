package com.edith.app

/**
 * Comparação de versões no formato "0.3.0" (aceita tag "v0.3.0" e sufixos).
 * Sem dependência do Android, para poder ser testado fora do aparelho.
 */
object Versao {

    /** "v0.3.0" -> "0.3.0" */
    fun limpar(texto: String): String =
        texto.trim().removePrefix("v").removePrefix("V").trim()

    private fun partes(texto: String): List<Int> =
        limpar(texto)
            .split(".", "-", "+", " ")
            .mapNotNull { it.toIntOrNull() }

    /** true se [remota] for mais nova que [atual]. */
    fun maisNova(remota: String, atual: String): Boolean {
        val r = partes(remota)
        val a = partes(atual)
        if (r.isEmpty()) return false
        for (i in 0 until maxOf(r.size, a.size)) {
            val ri = r.getOrElse(i) { 0 }
            val ai = a.getOrElse(i) { 0 }
            if (ri != ai) return ri > ai
        }
        return false
    }
}
