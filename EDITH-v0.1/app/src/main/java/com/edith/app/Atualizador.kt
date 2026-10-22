package com.edith.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Modo de ação "upgrade": descobre no GitHub Releases se existe versão nova,
 * baixa o APK e abre o instalador do Android.
 *
 * Nada aqui roda na thread principal: sempre chame de uma Thread.
 */
class Atualizador(private val activity: Activity) {

    data class Release(val versao: String, val apkUrl: String, val notas: String, val build: Int = 0)

    companion object {
        const val REPO = "viniciusnpaula-bit/EDITH"
        private const val URL_API = "https://api.github.com/repos/$REPO/releases/latest"
    }

    @Suppress("DEPRECATION")
    fun versaoAtual(): String = try {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "0.0.0"
    } catch (e: Exception) {
        "0.0.0"
    }

    /** versionCode instalado (o "build" que aparece na tag da release). */
    @Suppress("DEPRECATION")
    fun buildAtual(): Int = try {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionCode
    } catch (e: Exception) {
        0
    }

    /** Consulta a última release publicada. Lança exceção com mensagem em português. */
    fun buscarRelease(): Release {
        val conexao = (URL(URL_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "EDITH/${versaoAtual()}")
        }
        val codigo = try {
            conexao.responseCode
        } catch (e: Exception) {
            throw IllegalStateException("sem internet ou o GitHub não respondeu")
        }
        when {
            codigo == 404 -> throw IllegalStateException("o repositório ainda não tem nenhuma versão publicada")
            codigo == 403 -> throw IllegalStateException("o GitHub bloqueou o pedido por excesso de consultas, tente mais tarde")
            codigo !in 200..299 -> throw IllegalStateException("o GitHub respondeu $codigo")
        }

        val texto = conexao.inputStream.bufferedReader().use { it.readText() }
        val dados = Releases.ler(texto)
            ?: throw IllegalStateException("a última versão publicada não tem APK aproveitável")

        return Release(dados.versao, dados.apkUrl, dados.notas, dados.build)
    }

    /** Baixa o APK para o cache do app e devolve o arquivo. */
    fun baixar(release: Release): File {
        val pasta = File(activity.cacheDir, "updates").apply { mkdirs() }
        val destino = File(pasta, "edith-${release.versao}-b${release.build}.apk")
        if (destino.exists() && destino.length() > 0) return destino

        val conexao = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20000
            readTimeout = 60000
            setRequestProperty("User-Agent", "EDITH/${versaoAtual()}")
        }
        val codigo = conexao.responseCode
        if (codigo !in 200..299) throw IllegalStateException("download falhou (HTTP $codigo)")

        val temporario = File(pasta, "parcial.apk")
        try {
            conexao.inputStream.use { entrada ->
                temporario.outputStream().use { saida -> entrada.copyTo(saida) }
            }
            if (temporario.length() < 10000 || !ehApk(temporario)) {
                throw IllegalStateException("o que o GitHub entregou não parece um APK")
            }
            if (destino.exists()) destino.delete()
            if (!temporario.renameTo(destino)) {
                temporario.copyTo(destino, overwrite = true)
                temporario.delete()
            }
        } finally {
            if (temporario.exists()) temporario.delete()
        }
        return destino
    }

    private fun ehApk(arquivo: File): Boolean = try {
        arquivo.inputStream().use { entrada ->
            val assinatura = ByteArray(2)
            entrada.read(assinatura) == 2 && assinatura[0] == 'P'.code.toByte() && assinatura[1] == 'K'.code.toByte()
        }
    } catch (e: Exception) {
        false
    }

    /** true se o Android já deixa a EDITH instalar apps. */
    fun podeInstalar(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        activity.packageManager.canRequestPackageInstalls()
    } else {
        true
    }

    fun pedirPermissaoInstalacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        }
    }

    /** Abre a tela de instalação do Android com o APK baixado. */
    fun instalar(apk: File) {
        val uri = Uri.parse("content://${ApkProvider.AUTORIDADE}/${apk.name}")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }
}
