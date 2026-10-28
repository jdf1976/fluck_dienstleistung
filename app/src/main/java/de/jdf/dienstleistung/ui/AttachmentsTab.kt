package de.jdf.dienstleistung.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.jdf.dienstleistung.data.Attachment
import de.jdf.dienstleistung.data.AttachmentKind
import kotlinx.coroutines.launch
import java.io.File

/** Fotos (Kachelansicht) bzw. Prüfprotokolle (Liste) eines Auftrags. */
@Composable
fun AttachmentsTab(jobId: Long, kind: AttachmentKind) {
    val a = app()
    val db = a.db
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list by remember(jobId, kind) { db.attachments().forJob(jobId, kind) }.collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf<Attachment?>(null) }

    // Pfad der gerade laufenden Kameraaufnahme (überlebt Rotation/Prozess-Neustart)
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }

    suspend fun addImported(uris: List<android.net.Uri>) {
        uris.forEach { uri ->
            runCatching { a.files.import(uri) }
                .onSuccess {
                    db.attachments().insert(
                        Attachment(jobId = jobId, kind = kind, fileName = it.fileName,
                            displayName = it.displayName, mimeType = it.mimeType)
                    )
                }
                .onFailure { Toast.makeText(ctx, "Import fehlgeschlagen: ${it.message}", Toast.LENGTH_LONG).show() }
        }
        if (uris.isNotEmpty()) db.jobs().touch(jobId)
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPhoto ?: return@rememberLauncherForActivityResult
        pendingPhoto = null
        val f = File(path)
        scope.launch {
            if (ok && f.length() > 0) {
                val label = if (kind == AttachmentKind.PHOTO) "Foto" else "Protokoll (Foto)"
                db.attachments().insert(
                    Attachment(jobId = jobId, kind = kind, fileName = f.name,
                        displayName = "$label ${formatMillis(System.currentTimeMillis())}", mimeType = "image/jpeg")
                )
                db.jobs().touch(jobId)
            } else f.delete()
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        scope.launch { addImported(uris) }
    }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch { addImported(uris) }
    }

    fun takePhoto() {
        val f = a.files.newPhotoFile()
        pendingPhoto = f.absolutePath
        camera.launch(a.files.uriFor(f.name))
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { takePhoto() }, Modifier.weight(1f)) {
                Icon(Icons.Default.PhotoCamera, null); Text(if (kind == AttachmentKind.PHOTO) "  Kamera" else "  Abfotografieren")
            }
            if (kind == AttachmentKind.PHOTO) {
                OutlinedButton(onClick = {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, Modifier.weight(1f)) { Icon(Icons.Default.Image, null); Text("  Galerie") }
            } else {
                OutlinedButton(onClick = {
                    documents.launch(arrayOf("application/pdf", "image/*", "text/*", "application/*"))
                }, Modifier.weight(1f)) { Icon(Icons.Default.UploadFile, null); Text("  Datei / PDF") }
            }
        }

        if (list.isEmpty()) {
            EmptyHint(
                if (kind == AttachmentKind.PHOTO) "Noch keine Fotos zu diesem Auftrag."
                else "Noch keine Prüfprotokolle.\nPDF aus der Messgeräte-Software importieren oder Papierprotokoll abfotografieren."
            )
        } else if (kind == AttachmentKind.PHOTO) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(list, key = { it.id }) { att ->
                    AsyncImage(
                        model = a.files.file(att.fileName),
                        contentDescription = att.caption,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selected = att },
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.id }) { att ->
                    Card(Modifier.fillMaxWidth().clickable { selected = att }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when {
                                    att.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
                                    att.mimeType.startsWith("image/") -> Icons.Default.Image
                                    else -> Icons.Default.Description
                                },
                                null, Modifier.size(32.dp),
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(att.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (att.caption.isNotBlank()) Text(att.caption, style = MaterialTheme.typography.bodySmall)
                                Text(formatMillis(att.createdAt), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { AttachmentDialog(it) { selected = null } }
}

@Composable
private fun AttachmentDialog(att: Attachment, onDismiss: () -> Unit) {
    val a = app()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(att.displayName) }
    var caption by remember { mutableStateOf(att.caption) }
    var confirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (att.mimeType.startsWith("image/")) {
                    Box(Modifier.fillMaxWidth().clickable { a.files.open(att) }) {
                        AsyncImage(
                            model = a.files.file(att.fileName), contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                OutlinedTextField(caption, { caption = it }, Modifier.fillMaxWidth(), label = { Text("Beschreibung") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { a.files.open(att) }) { Text("Öffnen") }
                    OutlinedButton(onClick = { a.files.share(att) }) { Text("Teilen") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    a.db.attachments().upsert(att.copy(displayName = name.trim().ifEmpty { att.displayName }, caption = caption.trim()))
                    onDismiss()
                }
            }) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirm = true }) { Text("Löschen") }
                TextButton(onClick = onDismiss) { Text("Schließen") }
            }
        },
    )
    if (confirm) ConfirmDialog("Datei löschen?", "\"${att.displayName}\" wird endgültig gelöscht.",
        onConfirm = {
            scope.launch {
                a.db.attachments().delete(att)
                a.files.delete(att.fileName)
                onDismiss()
            }
        },
        onDismiss = { confirm = false })
}
