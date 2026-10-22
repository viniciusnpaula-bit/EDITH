package com.edith.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * org.json e org.json.JSONObject são stubs no teste de unidade (o Android os fornece
 * só no aparelho). Por isso o JSON é montado com JSONObject de verdade aqui.
 */
class ReleasesTest {

    private fun release(tag: String, corpo: String, assets: List<Pair<String, String>>): String {
        val obj = JSONObject()
        obj.put("tag_name", tag)
        obj.put("body", corpo)
        val lista = org.json.JSONArray()
        assets.forEach { (nome, url) ->
            lista.put(JSONObject().put("name", nome).put("browser_download_url", url))
        }
        obj.put("assets", lista)
        return obj.toString()
    }

    @Test
    fun leVersaoBuildEApkDebug() {
        val json = release(
            "v0.4.3+b12",
            "build: 12\n\nAPK gerado automaticamente.",
            listOf(
                "notas.txt" to "https://exemplo/notas.txt",
                "app-release.apk" to "https://exemplo/release.apk",
                "app-debug.apk" to "https://exemplo/debug.apk"
            )
        )
        val dados = Releases.ler(json)!!
        assertEquals("0.4.3+b12", dados.versao)
        assertEquals(12, dados.build)
        assertEquals("https://exemplo/debug.apk", dados.apkUrl)
    }

    @Test
    fun semApkDevolveNull() {
        assertNull(Releases.ler(release("v0.1.0", "", emptyList())))
    }

    @Test
    fun jsonInvalidoDevolveNull() {
        assertNull(Releases.ler("isso não é json"))
    }

    @Test
    fun buildAusenteEhZero() {
        assertEquals(0, Releases.buildDe("APK gerado automaticamente."))
        assertEquals(12, Releases.buildDe("build 12"))
    }
}
