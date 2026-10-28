package de.jdf.dienstleistung.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Komplettsicherung als ZIP: data.json (alle Tabellen) + attachments/ (Fotos, Protokolle).
 * JSON statt DB-Datei, damit Backups auch nach Schema-Änderungen lesbar bleiben.
 */
class Backup(private val context: Context, private val db: AppDatabase, private val files: FileStore) {

    suspend fun export(target: Uri): Int = withContext(Dispatchers.IO) {
        val json = JSONObject()
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("customers", JSONArray(db.customers().dump().map { it.toJson() }))
            .put("jobs", JSONArray(db.jobs().dump().map { it.toJson() }))
            .put("times", JSONArray(db.times().dump().map { it.toJson() }))
            .put("notes", JSONArray(db.notes().dump().map { it.toJson() }))
            .put("materials", JSONArray(db.materials().dump().map { it.toJson() }))
            .put("attachments", JSONArray(db.attachments().dump().map { it.toJson() }))

        var count = 0
        context.contentResolver.openOutputStream(target, "wt")!!.use { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(json.toString(1).toByteArray())
                zip.closeEntry()
                files.dir.listFiles()?.forEach { f ->
                    zip.putNextEntry(ZipEntry("attachments/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                }
            }
        }
        count
    }

    /** Ersetzt ALLE vorhandenen Daten durch den Inhalt der Sicherung. */
    suspend fun import(source: Uri) = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var json: JSONObject? = null
        context.contentResolver.openInputStream(source)!!.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    when {
                        e.name == "data.json" -> json = JSONObject(zip.readBytes().decodeToString())
                        e.name.startsWith("attachments/") && !e.isDirectory -> {
                            val name = File(e.name).name // schützt vor ../-Pfaden
                            File(staging, name).outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
            }
        }
        val data = json ?: error("Keine data.json in der Sicherung gefunden")

        db.clearAllTables()
        db.withTransaction {
            db.customers().insertAll(data.list("customers") { customer() })
            db.jobs().insertAll(data.list("jobs") { job() })
            db.times().insertAll(data.list("times") { timeEntry() })
            db.notes().insertAll(data.list("notes") { note() })
            db.materials().insertAll(data.list("materials") { material() })
            db.attachments().insertAll(data.list("attachments") { attachment() })
        }
        files.dir.listFiles()?.forEach { it.delete() }
        staging.listFiles()?.forEach { it.copyTo(File(files.dir, it.name), overwrite = true) }
        staging.deleteRecursively()
    }
}

private inline fun <T> JSONObject.list(key: String, map: JSONObject.() -> T): List<T> {
    val arr = optJSONArray(key) ?: return emptyList()
    return List(arr.length()) { arr.getJSONObject(it).map() }
}

private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)

private fun Customer.toJson() = JSONObject()
    .put("id", id).put("name", name).put("street", street).put("zipCity", zipCity)
    .put("phone", phone).put("email", email).put("notes", notes)

private fun JSONObject.customer() = Customer(
    id = getLong("id"), name = getString("name"), street = optString("street"),
    zipCity = optString("zipCity"), phone = optString("phone"), email = optString("email"),
    notes = optString("notes"),
)

private fun Job.toJson() = JSONObject()
    .put("id", id).put("customerId", customerId ?: JSONObject.NULL).put("title", title)
    .put("description", description).put("location", location).put("status", status.name)
    .put("createdAt", createdAt).put("updatedAt", updatedAt)

private fun JSONObject.job() = Job(
    id = getLong("id"), customerId = optLongOrNull("customerId"), title = getString("title"),
    description = optString("description"), location = optString("location"),
    status = runCatching { JobStatus.valueOf(getString("status")) }.getOrDefault(JobStatus.REQUEST),
    createdAt = getLong("createdAt"), updatedAt = getLong("updatedAt"),
)

private fun TimeEntry.toJson() = JSONObject()
    .put("id", id).put("jobId", jobId).put("date", date).put("startMinutes", startMinutes)
    .put("endMinutes", endMinutes).put("breakMinutes", breakMinutes).put("note", note)

private fun JSONObject.timeEntry() = TimeEntry(
    id = getLong("id"), jobId = getLong("jobId"), date = getLong("date"),
    startMinutes = getInt("startMinutes"), endMinutes = getInt("endMinutes"),
    breakMinutes = optInt("breakMinutes"), note = optString("note"),
)

private fun Note.toJson() = JSONObject()
    .put("id", id).put("jobId", jobId).put("text", text).put("createdAt", createdAt)

private fun JSONObject.note() = Note(
    id = getLong("id"), jobId = getLong("jobId"), text = getString("text"), createdAt = getLong("createdAt"),
)

private fun Material.toJson() = JSONObject()
    .put("id", id).put("jobId", jobId).put("name", name).put("quantity", quantity)
    .put("unit", unit).put("note", note)

private fun JSONObject.material() = Material(
    id = getLong("id"), jobId = getLong("jobId"), name = getString("name"),
    quantity = optDouble("quantity", 1.0), unit = optString("unit"), note = optString("note"),
)

private fun Attachment.toJson() = JSONObject()
    .put("id", id).put("jobId", jobId).put("kind", kind.name).put("fileName", fileName)
    .put("displayName", displayName).put("mimeType", mimeType).put("caption", caption)
    .put("createdAt", createdAt)

private fun JSONObject.attachment() = Attachment(
    id = getLong("id"), jobId = getLong("jobId"), kind = AttachmentKind.valueOf(getString("kind")),
    fileName = getString("fileName"), displayName = optString("displayName"),
    mimeType = optString("mimeType"), caption = optString("caption"), createdAt = getLong("createdAt"),
)
