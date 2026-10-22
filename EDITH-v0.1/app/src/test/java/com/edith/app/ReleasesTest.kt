package com.edith.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleasesTest {

    private val json = """
        {
          "tag_name": "v0.4.2+b11",
          "body": "build: 11\n\nAPK gerado automaticamente.",
          "assets": [
            {"name": "notas.txt", "browser_download_url": "https://exemplo/notas.txt"},
            {"name": "app-release.apk", "browser_download_url": "https://exemplo/release.apk"},
            {"name": "app-debug.apk", "browser_download_url": "https://exemplo/debug.apk"}
          ]
        }
    """.trimIndent()

    @Test
    fun leVersaoBuildEApkDebug() {
        val dados = Releases.ler(json)!!
        assertEquals("0.4.2+b11", dados.versao)
        assertEquals(11, dados.build)
        assertEquals("https://exemplo/debug.apk", dados.apkUrl)
    }

    @Test
    fun semApkDevolveNull() {
        assertNull(Releases.ler("""{"tag_name":"v0.1.0","assets":[]}"""))
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
