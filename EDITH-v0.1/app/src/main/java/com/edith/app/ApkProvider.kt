package com.edith.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/**
 * Entrega o APK baixado para o instalador do Android por content://
 * (a partir do Android 7 o sistema não aceita mais file://).
 */
class ApkProvider : ContentProvider() {

    companion object {
        const val AUTORIDADE = "com.edith.app.updates"
        private const val PASTA = "updates"
    }

    private fun arquivo(uri: Uri): File {
        val raiz = File(requireNotNull(context).cacheDir, PASTA).canonicalFile
        val alvo = File(raiz, uri.lastPathSegment ?: "").canonicalFile
        if (!alvo.path.startsWith(raiz.path + File.separator)) {
            throw SecurityException("Caminho fora da pasta de atualizações")
        }
        return alvo
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(arquivo(uri), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun getType(uri: Uri): String = "application/vnd.android.package-archive"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val apk = arquivo(uri)
        val colunas = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(colunas, 1)
        val linha = cursor.newRow()
        for (coluna in colunas) {
            when (coluna) {
                OpenableColumns.DISPLAY_NAME -> linha.add(apk.name)
                OpenableColumns.SIZE -> linha.add(apk.length())
                else -> linha.add(null)
            }
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
