package com.edith.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O parser já derrubou o app no celular com PatternSyntaxException.
 * Estes testes rodam no Actions e pegam esse erro antes de chegar ao aparelho.
 */
class ParserAcoesTest {

    @Test
    fun textoSimplesNaoViraComando() {
        val (texto, acoes) = ParserAcoes.extrair("São 23:40 agora.")
        assertEquals("São 23:40 agora.", texto)
        assertTrue(acoes.isEmpty())
    }

    @Test
    fun leMarcadorEntreColchetes() {
        val (texto, acoes) = ParserAcoes.extrair("Já vou. [[ACAO]] {\"acao\":\"lanterna\",\"estado\":\"ligar\"}")
        assertTrue(texto.startsWith("Já vou."))
        assertEquals(1, acoes.size)
        assertEquals("lanterna", acoes[0].nome)
        assertEquals("ligar", acoes[0].texto("estado"))
    }

    @Test
    fun leLinhaDeComando() {
        val (_, acoes) = ParserAcoes.extrair("Feito.\n{\"acao\":\"abrir_app\",\"app\":\"spotify\"}\n")
        assertEquals(1, acoes.size)
        assertEquals("abrir_app", acoes[0].nome)
    }

    @Test
    fun leBlocoDeCodigo() {
        val (_, acoes) = ParserAcoes.extrair("```json\n{\"acao\":\"timer\",\"segundos\":600}\n```")
        assertEquals(1, acoes.size)
        assertEquals(600, acoes[0].inteiro("segundos"))
    }

    @Test
    fun aceitaAcaoComoChave() {
        // O modelo às vezes manda {"abrir_app":"spotify"}: a chave é a ação.
        // O marcador é a própria chave: "abrir_app" (o caso que o modelo manda de vez em quando).
        val (_, acoes) = ParserAcoes.extrair("[[ACAO]] {\"abrir_app\":\"spotify\"}")
        assertEquals(1, acoes.size)
        val corrigida = acoes[0].comChaveComoAcao()
        assertEquals("abrir_app", corrigida.nome)
        // O valor vira "app" (o nome que o executor lê primeiro).
        assertEquals("spotify", corrigida.texto("app"))
    }

    @Test
    fun comandoIlegivelFicaNoTexto() {
        val (texto, acoes) = ParserAcoes.extrair("[[ACAO]] {isso não é json}")
        assertTrue(acoes.isEmpty())
        assertTrue(texto.contains("[[ACAO]]"))
    }
}
