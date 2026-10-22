package com.edith.app

import android.app.Activity
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diário de falhas no próprio aparelho.
 *
 * "A EDITH parou" sem log é adivinhação. Aqui o app guarda o erro que o Android
 * capturou e mostra para o usuário copiar — sem precisar de cabo, adb ou root.
 */
object Diagnostico {

    private const val ARQUIVO = "edith-crash.log"
    private const val LIMITE_BYTES = 64 * 1024

    private fun arquivo(activity: Activity) = File(activity.filesDir, ARQUIVO)

    fun registrar(activity: Activity, erro: Throwable, contexto: String = "") {
        try {
            val texto = StringWriter()
            PrintWriter(texto).use { erro.printStackTrace(it) }
            val quando = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date())
            val linha = buildString {
                appendLine("=== $quando · build ${codigoBuild(activity)} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
                if (contexto.isNotBlank()) appendLine("Contexto: $contexto")
                appendLine(texto.toString().trim())
                appendLine()
            }
            val destino = arquivo(activity)
            if (destino.exists() && destino.length() > LIMITE_BYTES) destino.delete()
            // Visível também no logcat, para quem tiver cabo/adb.
            android.util.Log.e("EDITH-CRASH", linha)
            destino.appendText(linha)
        } catch (e: Exception) {
            android.util.Log.e("EDITH", "Não consegui gravar o diagnóstico", e)
        }
    }

    fun ultimo(activity: Activity): String = try {
        val destino = arquivo(activity)
        if (!destino.exists()) "" else destino.readText().trim()
    } catch (e: Exception) {
        ""
    }

    fun limpar(activity: Activity) {
        try {
            arquivo(activity).delete()
        } catch (e: Exception) {
            android.util.Log.w("EDITH", "Não consegui apagar o diagnóstico", e)
        }
    }

    @Suppress("DEPRECATION")
    fun codigoBuild(activity: Activity): Int = try {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionCode
    } catch (e: Exception) {
        0
    }
}
