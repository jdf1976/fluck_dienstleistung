package de.jdf.dienstleistung.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Ablage für Fotos und Protokolle im privaten App-Speicher. */
class FileStore(private val context: Context) {

    val dir: File = File(context.filesDir, "attachments").apply { mkdirs() }

    fun file(name: String) = File(dir, name)

    fun uriFor(name: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file(name))

    /** Leere Datei für ein Kamerafoto anlegen. */
    fun newPhotoFile(): File = File(dir, "${UUID.randomUUID()}.jpg")

    /** Externe Datei (Galerie, Dateimanager) in den App-Speicher kopieren. */
    suspend fun import(uri: Uri): Imported = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        var display = "Datei"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) display = c.getString(0)
        }
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: display.substringAfterLast('.', "bin")
        val target = File(dir, "${UUID.randomUUID()}.$ext")
        resolver.openInputStream(uri)!!.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        Imported(target.name, display, mime)
    }

    fun delete(name: String) {
        file(name).delete()
    }

    fun open(a: Attachment) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(a.fileName), a.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(intent, a.displayName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun share(a: Attachment) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType(a.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uriFor(a.fileName))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    data class Imported(val fileName: String, val displayName: String, val mimeType: String)
}
