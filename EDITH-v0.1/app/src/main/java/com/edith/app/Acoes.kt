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

    /**
     * Marcadores aceitos. Variações existem porque modelo erra formato de vez em quando.
     *
     * Duas regras que já derrubaram o app uma vez (PatternSyntaxException em Acoes.kt):
     * nada de flag inline (?i)/(?s) — o ICU do Android não aceita; e nada de classe com
     * faixa acentuada tipo [CÇ] — com a flag "i" vira faixa inválida. Por isso os padrões
     * são montados em tempo de execução, com trechos ASCII e grupos opcionais.
     */
    private val marcadores: List<Regex> by lazy {
        // "AÇÃO" / "ACAO" em qualquer caixa: A + (Ç|C) + (Ã|A) + O.
        // Sem depender da flag IGNORE_CASE para isso — acento e caixa não se misturam no ICU.
        val acao = "[Aa][Cc][Aa][Oo]"
        listOf(
            regex(
                "\\[\\[\\s*$acao\\s*\\]\\]\\s*(\\{[^\\n]*})",
                RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL
            ),
            regex(
                "```[a-zA-Z]*\\s*(\\{[^\\n]*\"acao\"[^\\n]*})\\s*```",
                RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL
            ),
            regex("^\\s*$acao\\s*:\\s*(\\{[^\\n]*})\\s*$", RegexOption.MULTILINE),
            regex("^\\s*(\\{\\s*\"acao\"[^\\n]*})\\s*$", RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
        )
    }

    /** Compila um padrão sem nunca deixar o app cair: um marcador inválido é ignorado. */
    private fun regex(padrao: String, vararg opcoes: RegexOption): Regex = try {
        Regex(padrao, opcoes.toSet())
    } catch (e: Exception) {
        android.util.Log.e("EDITH-Parser", "Padrão inválido ignorado: $padrao", e)
        try { Regex("(?!)") } catch (e2: Exception) { Regex("$.^") }
    }

    fun extrair(resposta: String): Pair<String, List<Acao>> = try {
        extrairSeguro(resposta)
    } catch (e: Exception) {
        android.util.Log.e("EDITH-Parser", "Falha ao separar ações da resposta", e)
        resposta.trim() to emptyList()
    }

    private fun extrairSeguro(resposta: String): Pair<String, List<Acao>> {
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
