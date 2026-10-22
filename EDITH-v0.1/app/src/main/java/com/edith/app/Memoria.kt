package com.edith.app

import android.app.Activity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Histórico da conversa que sobrevive ao fechar o app.
 *
 * Antes o histórico era um JSONArray em memória: fechou, esqueceu. Agora as últimas
 * trocas ficam em arquivo, e a EDITH lembra do que foi dito ontem.
 */
class Memoria(private val activity: Activity) {

    companion object {
        private const val ARQUIVO = "conversa.json"
        /** Quantas mensagens guardamos em disco (usuário + assistente). */
        private const val LIMITE_DISCO = 40
        /** Quantas vão para a API a cada chamada. */
        private const val LIMITE_PROMPT = 20
    }

    private fun arquivo(): File = File(activity.filesDir, ARQUIVO)

    /** Carrega o histórico salvo. Arquivo corrompido vira histórico vazio, nunca crash. */
    fun carregar(): JSONArray = try {
        val destino = arquivo()
        if (!destino.exists()) JSONArray() else JSONArray(destino.readText())
    } catch (e: Exception) {
        android.util.Log.w("EDITH-Memoria", "Histórico ilegível, começando do zero", e)
        JSONArray()
    }

    fun salvar(historico: JSONArray) {
        try {
            val copia = JSONArray()
            val inicio = (historico.length() - LIMITE_DISCO).coerceAtLeast(0)
            for (i in inicio until historico.length()) copia.put(historico.getJSONObject(i))
            arquivo().writeText(copia.toString())
        } catch (e: Exception) {
            android.util.Log.w("EDITH-Memoria", "Não consegui salvar o histórico", e)
        }
    }

    fun limpar() {
        try {
            arquivo().delete()
        } catch (e: Exception) {
            android.util.Log.w("EDITH-Memoria", "Não consegui apagar o histórico", e)
        }
    }

    /** Recorta o histórico para não estourar o prompt. */
    fun paraPrompt(historico: JSONArray): JSONArray {
        val recorte = JSONArray()
        val inicio = (historico.length() - LIMITE_PROMPT).coerceAtLeast(0)
        for (i in inicio until historico.length()) recorte.put(historico.getJSONObject(i))
        return recorte
    }

    /** Uma linha para a tela: quantas mensagens estão guardadas. */
    fun resumo(historico: JSONArray): String =
        if (historico.length() == 0) "Nenhuma conversa guardada."
        else "${historico.length()} mensagens guardadas neste aparelho."
}

/** Preferências de voz e comportamento, separadas da interface. */
object Preferencias {
    const val VIVA = "voz_continua"
    const val FALAR_RESPOSTAS = "falar_respostas"

    fun vozContinua(prefs: android.content.SharedPreferences): Boolean =
        prefs.getBoolean(VIVA, true)

    fun falarRespostas(prefs: android.content.SharedPreferences): Boolean =
        prefs.getBoolean(FALAR_RESPOSTAS, true)

    fun definir(prefs: android.content.SharedPreferences, chave: String, valor: Boolean) {
        prefs.edit().putBoolean(chave, valor).apply()
    }

    /** Uma linha para a tela de ajustes. */
    fun resumo(prefs: android.content.SharedPreferences): String = buildString {
        append(if (vozContinua(prefs)) "Escuta contínua ligada" else "Escuta contínua desligada")
        append(" · ")
        append(if (falarRespostas(prefs)) "voz ligada" else "voz desligada")
    }
}
