package com.edith.app

import org.json.JSONObject
import java.util.Locale

/** Uma ação pedida pela EDITH, já interpretada a partir do texto do modelo. */
class Acao(val nome: String, private val dados: JSONObject) {
    fun texto(chave: String): String? = dados.optString(chave, "").takeIf { it.isNotBlank() }
    fun inteiro(chave: String, padrao: Int = 0): Int = dados.optInt(chave, padrao)
    fun tem(chave: String): Boolean = dados.has(chave)

    /**
     * Aceita o formato errado que o modelo às vezes manda: {"abrir_app":"spotify"}.
     * Devolve uma ação com nome válido e o valor guardado como "app"/"nome".
     */
    fun comChaveComoAcao(): Acao {
        val chaves = dados.keys()
        while (chaves.hasNext()) {
            val chave = chaves.next()
            if (chave == "acao" || chave == "action") continue
            val valor = dados.opt(chave)
            if (valor !is String) continue
            val novo = JSONObject(dados.toString())
            novo.remove(chave)
            novo.put("acao", chave)
            if (!novo.has("app") && !novo.has("nome")) novo.put("nome", valor)
            return Acao(chave.trim().lowercase(Locale.ROOT), novo)
        }
        return this
    }
}

/**
 * Separa o texto que a EDITH fala dos comandos que o aparelho executa.
 * Não usa nada do Android, então dá para testar fora do celular.
 */
object ParserAcoes {

    /** Marcadores aceitos. Variações existem porque modelo erra formato de vez em quando. */
    private val marcadores = listOf(
        Regex("""(?is)\[\[\s*A[CÇ][AÃ]O\s*\]\]\s*(\{[^\n]*})"""),
        Regex("""(?is)```[a-zA-Z]*\s*(\{[^\n]*"acao"[^\n]*})\s*```"""),
        Regex("""(?im)^\s*A[CÇ][AÃ]O\s*:\s*(\{[^\n]*})\s*$"""),
        Regex("""(?im)^\s*(\{\s*"acao"[^\n]*})\s*$""")
    )

    fun extrair(resposta: String): Pair<String, List<Acao>> {
        var texto = resposta
        val acoes = mutableListOf<Acao>()
        for (marcador in marcadores) {
            texto = marcador.replace(texto) { m ->
                val acao = parsear(m.groupValues[1])
                if (acao == null) {
                    m.value // comando ilegível: mantém o texto em vez de sumir com ele
                } else {
                    acoes.add(acao)
                    ""
                }
            }
        }
        return texto.trim() to acoes
    }

    private fun parsear(json: String): Acao? = try {
        val obj = JSONObject(json)
        val nome = obj.optString("acao").ifBlank { obj.optString("action") }
            .trim().lowercase(Locale.ROOT)
        if (nome.isBlank()) null else Acao(nome, obj)
    } catch (e: Exception) {
        null
    }
}
