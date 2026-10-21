package com.edith.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lê o JSON que a API de releases do GitHub devolve.
 * Puro (só org.json), para poder ser testado fora do aparelho.
 */
object Releases {

    data class Dados(val versao: String, val apkUrl: String, val notas: String)

    /** Devolve null se o JSON não tiver versão ou APK. */
    fun ler(json: String): Dados? {
        val obj = try {
            JSONObject(json)
        } catch (e: Exception) {
            return null
        }
        val versao = Versao.limpar(obj.optString("tag_name"))
        if (versao.isBlank()) return null
        val url = melhorApk(obj.optJSONArray("assets") ?: JSONArray()) ?: return null
        return Dados(versao, url, obj.optString("body", "").trim())
    }

    /** Prefere o APK de debug (o que o Actions gera), mas aceita qualquer .apk. */
    fun melhorApk(assets: JSONArray): String? {
        var primeiro = ""
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val nome = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (!nome.endsWith(".apk", ignoreCase = true) || url.isBlank()) continue
            if (nome.contains("debug", ignoreCase = true)) return url
            if (primeiro.isBlank()) primeiro = url
        }
        return primeiro.ifBlank { null }
    }
}
