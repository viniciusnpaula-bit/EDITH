package com.edith.app

import android.content.Context
import android.graphics.Color
import org.json.JSONObject
import java.util.Locale

/**
 * Paleta da EDITH — verde claro, como o usuário pediu.
 * Um lugar só: mudar de cor aqui muda o app inteiro.
 */
object Cores {
    const val FUNDO = "#F2FBF3"
    const val SUPERFICIE = "#FFFFFF"
    const val CARTAO = "#E4F6E6"
    const val BORDA = "#C3E7C8"
    const val PRIMARIA = "#3FBF63"
    const val PRIMARIA_ESCURA = "#1E7A3C"
    const val TEXTO = "#14261A"
    const val TEXTO_SUAVE = "#5A7563"
    const val ALERTA = "#D9822B"
    const val BOLHA_USUARIO = "#CBEFD4"

    fun cor(hex: String): Int = Color.parseColor(hex)
}

/** Uma ação pedida pela EDITH, já interpretada a partir do texto do modelo. */
class Acao(val nome: String, private val dados: JSONObject) {
    fun texto(chave: String): String? = dados.optString(chave, "").takeIf { it.isNotBlank() }
    fun inteiro(chave: String, padrao: Int = 0): Int = dados.optInt(chave, padrao)
    fun tem(chave: String): Boolean = dados.has(chave)
    internal fun dados(): JSONObject = dados

    private val parametros = setOf(
        "acao", "action", "app", "nome", "pacote", "estado", "nivel", "delta", "fluxo",
        "hora", "label", "segundos", "minutos", "texto", "titulo", "inicio", "termo",
        "apelido", "destino", "contato", "numero", "mensagem", "termo_busca"
    )

    /**
     * Aceita o formato errado que o modelo às vezes manda: {"abrir_app":"spotify"}.
     * Devolve uma ação com nome válido e o valor guardado como "app".
     */
    fun comChaveComoAcao(): Acao {
        val textos = LinkedHashMap<String, String>()
        val chaves = dados.keys()
        while (chaves.hasNext()) {
            val chave = chaves.next()
            if (chave.toIntOrNull() != null) continue
            val valor = dados.opt(chave)
            if (valor is String && valor.isNotBlank()) textos[chave] = valor
        }
        val candidata = textos.keys.firstOrNull { it !in parametros } ?: return this
        val valor = textos[candidata] ?: return this
        val novo = JSONObject()
        textos.forEach { (chave, v) -> if (chave != candidata) novo.put(chave, v) }
        novo.put("acao", candidata)
        if (!novo.has("app") && !novo.has("nome")) novo.put("app", valor)
        return Acao(candidata.trim().lowercase(Locale.ROOT), novo)
    }
}

/**
 * Separa o texto que a EDITH fala dos comandos que o aparelho executa.
 * Puro: não usa nada do Android, então roda nos testes do Actions.
 */
object ParserAcoes {

    /**
     * Padrões compatíveis com java.util.regex.Pattern no Android.
     * As chaves do JSON são escapadas explicitamente como \\{ e \\}.
     * Os padrões são compilados de forma preguiçosa, mas erros de sintaxe não
     * são substituídos por padrões falsos: assim, testes e logs revelam a causa real.
     */
    private val marcadores: List<Regex> by lazy {
        val acao = "[Aa][Cc][Aa][Oo]"
        listOf(
            // [[ACAO]] {...} ou [ACAO] {...}
            Regex(
                """\[\[?\s*$acao\s*\]?\]\s*[:\-]?\s*(\{[^\n]*\})"""
            ),
            // Bloco de código contendo JSON
            Regex(
                "```[a-zA-Z]*\\s*(\\{[^\\n]*\"acao\"[^\\n]*\\})\\s*```",
                RegexOption.IGNORE_CASE
            ),
            // ACAO: {"acao":...}
            Regex(
                """^\s*$acao\s*:\s*(\{[^\n]*\})\s*$""",
                RegexOption.MULTILINE
            ),
            // Linha contendo apenas o JSON
            Regex(
                """^\s*(\{\s*"acao"[^\n]*\})\s*$""",
                setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
            ),
            // JSON solto no meio de uma frase
            Regex(
                """(\{\s*"acao"\s*:\s*"[^"]+"[^\n}]*\})""",
                RegexOption.IGNORE_CASE
            )
        )
    }

    fun extrair(resposta: String): Pair<String, List<Acao>> = try {
        extrairSeguro(resposta)
    } catch (e: Exception) {
        throw IllegalStateException("Falha ao interpretar a resposta da EDITH", e)
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
        return limparTexto(texto) to acoes
    }

    /** Tira sobras de marcador e linhas vazias do que vai aparecer e ser falado. */
    private fun limparTexto(texto: String): String = texto
        .replace(Regex("""\[\[?\s*A[Cc][Aa][Oo]\s*\]?\]""", RegexOption.IGNORE_CASE), "")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
        .trim()

    private fun parsear(json: String): Acao? = try {
        val corrigida = Acao("", JSONObject(json)).comChaveComoAcao()
        val nome = corrigida.texto("acao") ?: corrigida.texto("action")
        if (nome.isNullOrBlank()) null else Acao(nome.trim().lowercase(Locale.ROOT), corrigida.dados())
    } catch (e: Exception) {
        null
    }
}
