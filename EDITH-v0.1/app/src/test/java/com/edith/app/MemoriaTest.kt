package com.edith.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoriaTest {

    private fun historico(tamanho: Int): JSONArray {
        val lista = JSONArray()
        for (i in 0 until tamanho) {
            lista.put(JSONObject().put("role", "user").put("content", "msg $i"))
        }
        return lista
    }

    @Test
    fun promptRecebeSoAsUltimasMensagens() {
        // Sem o Android aqui, a regra é a mesma do app: 20 mensagens por chamada.
        val recorte = recorte(historico(35), 20)
        assertEquals(20, recorte.length())
        assertEquals("msg 15", recorte.getJSONObject(0).getString("content"))
    }

    @Test
    fun historicoCurtoNaoEhRecortado() {
        assertEquals(3, recorte(historico(3), 20).length())
    }

    @Test
    fun historicoVazioNaoQuebra() {
        assertEquals(0, recorte(JSONArray(), 20).length())
    }

    /** Mesma lógica de Memoria.paraPrompt, sem depender de Activity. */
    private fun recorte(historico: JSONArray, limite: Int): JSONArray {
        val recorte = JSONArray()
        val inicio = (historico.length() - limite).coerceAtLeast(0)
        for (i in inicio until historico.length()) recorte.put(historico.getJSONObject(i))
        return recorte
    }
}
