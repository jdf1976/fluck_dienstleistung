package de.jdf.dienstleistung.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.jdf.dienstleistung.data.AttachmentKind
import de.jdf.dienstleistung.data.Job
import de.jdf.dienstleistung.data.JobStatus
import de.jdf.dienstleistung.data.Material
import de.jdf.dienstleistung.data.Note
import de.jdf.dienstleistung.data.TimeEntry
import de.jdf.dienstleistung.data.address
import de.jdf.dienstleistung.data.netMinutes
import kotlinx.coroutines.launch

private val tabs = listOf("Übersicht", "Zeiten", "Notizen", "Material", "Fotos", "Protokolle")

@Composable
fun JobDetailScreen(
    jobId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenCustomer: (Long) -> Unit,
    onQuickCount: (Long) -> Unit,
) {
    val a = app()
    val db = a.db
    val scope = rememberCoroutineScope()
    val job by remember(jobId) { db.jobs().byId(jobId) }.collectAsState(initial = null)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }

    var editTime by remember { mutableStateOf<TimeEntry?>(null) }
    var editNote by remember { mutableStateOf<Note?>(null) }
    var editMaterial by remember { mutableStateOf<Material?>(null) }

    val timer = runningTimer()
    val timerHere = timer?.jobId == jobId
    val timerActions = rememberTimerActions()
    val takePhoto = rememberPhotoCapture(jobId)

    val j = job
    Scaffold(
        topBar = {
            BackTopBar(if (tab == 0) "Auftrag" else tabs[tab], { if (tab == 0) onBack() else tab = 0 }) {
                if (tab == 0) {
                    IconButton(onClick = { onEdit(jobId) }, Modifier.size(52.dp)) { Icon(Icons.Default.Edit, "Bearbeiten") }
                    IconButton(onClick = { confirmDelete = true }, Modifier.size(52.dp)) { Icon(Icons.Default.Delete, "Löschen") }
                }
            }
        },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val now = rememberNow(timerHere)
                    Button(
                        onClick = { if (timerHere) timerActions.stop() else timerActions.start(jobId) },
                        modifier = Modifier.weight(1f).height(60.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SignalYellow, contentColor = Navy),
                    ) {
                        Icon(if (timerHere) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                        Text(
                            if (timerHere && timer != null) " Stopp ${formatStopwatch((now - timer.startedAt) / 1000)}" else " Zeit starten",
                            style = MaterialTheme.typography.titleSmall, maxLines = 1,
                        )
                    }
                    SquareAction(Icons.Default.PhotoCamera, "Foto", takePhoto)
                    SquareAction(Icons.AutoMirrored.Filled.NoteAdd, "Notiz") { editNote = Note(jobId = jobId, text = "") }
                    SquareAction(Icons.Default.Calculate, "Zählen") { onQuickCount(jobId) }
                }
            }
        },
        floatingActionButton = {
            when (tab) {
                1 -> Fab("Nachtragen") { editTime = TimeEntry(jobId = jobId, date = todayEpochDay(), startMinutes = -1, endMinutes = -1) }
                3 -> Fab("Material") { editMaterial = Material(jobId = jobId, name = "") }
            }
        },
    ) { pad ->
        if (j == null) return@Scaffold
        androidx.activity.compose.BackHandler(enabled = tab != 0) { tab = 0 }
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> OverviewTab(j, onOpenCustomer, onShowTab = { tab = it }, onEditNote = { editNote = it })
                1 -> TimesTab(jobId) { editTime = it }
                2 -> NotesTab(jobId) { editNote = it }
                3 -> MaterialsTab(j, onQuickCount = { onQuickCount(jobId) }) { editMaterial = it }
                4 -> AttachmentsTab(jobId, AttachmentKind.PHOTO)
                5 -> AttachmentsTab(jobId, AttachmentKind.PROTOCOL)
            }
        }
    }

    editTime?.let { TimeEntryDialog(it) { editTime = null } }
    editNote?.let { NoteDialog(it) { editNote = null } }
    editMaterial?.let { MaterialDialog(it) { editMaterial = null } }

    if (confirmDelete && j != null) ConfirmDialog(
        "Auftrag löschen?",
        "\"${j.title}\" mit allen Zeiten, Notizen, Material, Fotos und Protokollen wird endgültig gelöscht.",
        onConfirm = {
            scope.launch {
                if (timerHere) a.timer.stop()
                db.attachments().allForJob(j.id).forEach { a.files.delete(it.fileName) }
                db.jobs().delete(j)
                onBack()
            }
        },
        onDismiss = { confirmDelete = false },
    )
}

/** Quadratischer Knopf mit Symbol in der unteren Leiste. */
@Composable
private fun SquareAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = Modifier.size(60.dp), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(26.dp)) }
    }
}

@Composable
private fun Fab(text: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(onClick = onClick, icon = { Icon(Icons.Default.Add, null) },
        text = { Text(text, style = MaterialTheme.typography.labelLarge) },
        containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
}

private val shortStatus = mapOf(
    JobStatus.REQUEST to "Anfrage", JobStatus.PLANNED to "Geplant", JobStatus.IN_PROGRESS to "In Arbeit",
    JobStatus.DONE to "Erledigt", JobStatus.INVOICED to "Abgerech.",
)

@Composable
private fun OverviewTab(job: Job, onOpenCustomer: (Long) -> Unit, onShowTab: (Int) -> Unit, onEditNote: (Note) -> Unit) {
    val a = app()
    val db = a.db
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val customer by remember(job.customerId) { db.customers().byId(job.customerId ?: -1) }.collectAsState(initial = null)
    val times by remember(job.id) { db.times().forJob(job.id) }.collectAsState(initial = emptyList())
    val materials by remember(job.id) { db.materials().forJob(job.id) }.collectAsState(initial = emptyList())
    val photos by remember(job.id) { db.attachments().forJob(job.id, AttachmentKind.PHOTO) }.collectAsState(initial = emptyList())
    val protocols by remember(job.id) { db.attachments().forJob(job.id, AttachmentKind.PROTOCOL) }.collectAsState(initial = emptyList())
    val notes by remember(job.id) { db.notes().forJob(job.id) }.collectAsState(initial = emptyList())
    val place = job.location.ifBlank { customer?.address.orEmpty() }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp)) {
            Text(job.title, style = MaterialTheme.typography.headlineMedium)
            val sub = listOfNotNull(customer?.name, place.ifBlank { null }).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Status als Schieberegler
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            JobStatus.entries.forEach { s ->
                val on = job.status == s
                val look = statusLook(s)
                Surface(
                    onClick = { scope.launch { db.jobs().setStatus(job.id, s) } },
                    modifier = Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(11.dp),
                    color = if (on) MaterialTheme.colorScheme.background else Color.Transparent,
                    shadowElevation = if (on) 2.dp else 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(shortStatus.getValue(s), style = MaterialTheme.typography.labelMedium, maxLines = 1,
                            color = if (on) look.fg else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (job.description.isNotBlank()) {
            Text(job.description, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp))
        }

        SectionLabel("Auftrag")
        OverviewRow(Icons.Default.Schedule, "Arbeitszeit",
            times.sumOf { it.netMinutes }.let { if (it > 0) formatDuration(it) else "–" }) { onShowTab(1) }
        OverviewRow(Icons.Default.Inventory2, "Material", "${materials.size} Pos.") { onShowTab(3) }
        OverviewRow(Icons.Default.PhotoCamera, "Fotos", photos.size.toString()) { onShowTab(4) }
        OverviewRow(Icons.Default.Description, "Prüfprotokolle", protocols.size.toString()) { onShowTab(5) }
        OverviewRow(Icons.Default.EditNote, "Notizen", notes.size.toString()) { onShowTab(2) }

        if (photos.isNotEmpty()) {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                photos.take(4).forEach { p ->
                    AsyncImage(
                        model = a.files.file(p.fileName), contentDescription = p.caption, contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onShowTab(4) },
                    )
                }
                repeat(4 - photos.size.coerceAtMost(4)) { Box(Modifier.weight(1f)) }
            }
        }

        notes.firstOrNull()?.let { n ->
            SectionLabel("Letzte Notiz")
            Box(Modifier.padding(horizontal = 16.dp)) { NoteCard(n) { onEditNote(n) } }
        }

        if (customer != null || place.isNotBlank()) {
            SectionLabel("Kunde")
            customer?.let { c ->
                ListRow(c.name, subtitle = c.phone.ifBlank { null }, onClick = { onOpenCustomer(c.id) },
                    trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) })
            }
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (place.isNotBlank()) BigButton("Route", {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place))))
                }, Modifier.weight(1f), icon = Icons.Default.Directions, style = BtnStyle.Outline, height = 52.dp)
                val phone = customer?.phone.orEmpty()
                if (phone.isNotBlank()) BigButton("Anrufen", {
                    ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                }, Modifier.weight(1f), icon = Icons.Default.Call, style = BtnStyle.Outline, height = 52.dp)
            }
        }
    }
}

@Composable
private fun OverviewRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, onClick: () -> Unit) {
    ListRow(
        title, onClick = onClick,
        leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MaterialTheme.typography.titleSmall)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        androidx.compose.material3.TextButton(onClick = onAction, modifier = Modifier.height(44.dp)) {
            Text(action, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun NoteCard(n: Note, onClick: () -> Unit) {
    val dark = LocalIsDark.current
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp),
        color = if (dark) Color(0xFF2A2410) else Color(0xFFFFF8E1),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatMillis(n.createdAt), style = MaterialTheme.typography.labelSmall,
                color = if (dark) SignalYellow else Color(0xFF6B5300))
            Text(n.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun TimesTab(jobId: Long, onEdit: (TimeEntry) -> Unit) {
    val db = app().db
    val times by remember(jobId) { db.times().forJob(jobId) }.collectAsState(initial = emptyList())
    if (times.isEmpty()) return EmptyHint("Noch keine Zeiten.\nUnten „Zeit“ startet den Timer,\n„Nachtragen“ für nachträgliche Eingabe.")
    LazyColumn(contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Gesamt: ${formatDuration(times.sumOf { it.netMinutes })}", style = MaterialTheme.typography.headlineSmall)
        }
        items(times, key = { it.id }) { t ->
            AppCard(Modifier.fillMaxWidth(), onClick = { onEdit(t) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(formatEpochDay(t.date), style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${formatClock(t.startMinutes)} – ${formatClock(t.endMinutes)}" +
                                if (t.breakMinutes > 0) " (Pause ${t.breakMinutes} min)" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (t.note.isNotBlank()) Text(t.note, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatDuration(t.netMinutes), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun NotesTab(jobId: Long, onEdit: (Note) -> Unit) {
    val db = app().db
    val notes by remember(jobId) { db.notes().forJob(jobId) }.collectAsState(initial = emptyList())
    if (notes.isEmpty()) return EmptyHint("Noch keine Notizen.\nUnten „Notiz“ antippen – Spracheingabe der Tastatur geht auch.")
    LazyColumn(contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(notes, key = { it.id }) { n -> NoteCard(n) { onEdit(n) } }
    }
}

@Composable
private fun MaterialsTab(job: Job, onQuickCount: () -> Unit, onEdit: (Material) -> Unit) {
    val jobId = job.id
    val db = app().db
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<Material?>(null) }
    val mats by remember(jobId) { db.materials().forJob(jobId) }.collectAsState(initial = emptyList())
    val customer by remember(job.customerId) { db.customers().byId(job.customerId ?: -1) }.collectAsState(initial = null)
    val text = { materialListText(job, customer, mats) }
    LazyColumn(contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            BigButton("Schnellzählung", onQuickCount, Modifier.fillMaxWidth(), icon = Icons.Default.Calculate)
        }
        item {
            Text("Materialliste", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            if (mats.isEmpty()) Text("Noch keine Positionen. Über die Schnellzählung oder „+ Material“ erfassen.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (mats.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("Senden (WhatsApp, E-Mail …)", { shareText(ctx, "Materialliste – ${job.title}", text()) },
                    Modifier.fillMaxWidth(), icon = Icons.Default.Share, style = BtnStyle.Dark, height = 56.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Kopieren", {
                        copyToClipboard(ctx, "Materialliste", text())
                        android.widget.Toast.makeText(ctx, "Materialliste kopiert", android.widget.Toast.LENGTH_SHORT).show()
                    }, Modifier.weight(1f), icon = Icons.Default.ContentCopy, style = BtnStyle.Outline, height = 52.dp)
                    BigButton("Als Notiz", {
                        scope.launch {
                            db.notes().upsert(Note(jobId = jobId, text = text().replace("*", "")))
                            db.jobs().touch(jobId)
                            android.widget.Toast.makeText(ctx, "Als Notiz gespeichert", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }, Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.NoteAdd, style = BtnStyle.Outline, height = 52.dp)
                }
            }
        }
        items(mats, key = { it.id }) { m ->
            AppCard(Modifier.fillMaxWidth(), onClick = { onEdit(m) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${formatQty(m.quantity)} ${m.unit}", Modifier.padding(end = 14.dp), style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.bodyLarge)
                        if (m.note.isNotBlank()) Text(m.note, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { toDelete = m }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Default.Delete, "${m.name} löschen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    toDelete?.let { m ->
        ConfirmDialog("Position löschen?", "${formatQty(m.quantity)} ${m.unit} ${m.name} wird aus der Materialliste entfernt.",
            onConfirm = { scope.launch { db.materials().delete(m) } }, onDismiss = { toDelete = null })
    }
}
