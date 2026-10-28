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
import androidx.compose.material.icons.filled.Call
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
            BackTopBar("Auftrag", onBack) {
                IconButton(onClick = { onEdit(jobId) }, Modifier.size(52.dp)) { Icon(Icons.Default.Edit, "Bearbeiten") }
                IconButton(onClick = { confirmDelete = true }, Modifier.size(52.dp)) { Icon(Icons.Default.Delete, "Löschen") }
            }
        },
        bottomBar = {
            Column {
                if (timerHere && timer != null) {
                    val now = rememberNow(true)
                    Row(Modifier.fillMaxWidth().background(Navy).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiveDot()
                        Text("Timer läuft für diesen Auftrag", Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC9D6E8))
                        Text(formatStopwatch((now - timer.startedAt) / 1000), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = 8.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp, 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionButton("Foto", Icons.Default.PhotoCamera, BtnStyle.Primary, Modifier.weight(1f), takePhoto)
                        ActionButton(if (timerHere) "Stopp" else "Zeit",
                            if (timerHere) Icons.Default.Stop else Icons.Default.PlayArrow, BtnStyle.Dark, Modifier.weight(1f)) {
                            if (timerHere) timerActions.stop() else timerActions.start(jobId)
                        }
                        ActionButton("Notiz", Icons.AutoMirrored.Filled.NoteAdd, BtnStyle.Outline, Modifier.weight(1f)) {
                            editNote = Note(jobId = jobId, text = "")
                        }
                    }
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
        Column(Modifier.padding(pad).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i },
                        text = { Text(t, style = MaterialTheme.typography.labelLarge) })
                }
            }
            when (tab) {
                0 -> OverviewTab(j, onOpenCustomer, onShowTab = { tab = it }, onEditNote = { editNote = it })
                1 -> TimesTab(jobId) { editTime = it }
                2 -> NotesTab(jobId) { editNote = it }
                3 -> MaterialsTab(jobId) { editMaterial = it }
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

@Composable
private fun ActionButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, style: BtnStyle,
                         modifier: Modifier, onClick: () -> Unit) {
    val (bg, fg) = when (style) {
        BtnStyle.Primary -> SignalYellow to Navy
        BtnStyle.Dark -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        BtnStyle.Outline -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.primary
    }
    Surface(
        onClick = onClick, modifier = modifier.height(68.dp), shape = RoundedCornerShape(20.dp),
        color = bg, contentColor = fg,
        border = if (style == BtnStyle.Outline) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(26.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Fab(text: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(onClick = onClick, icon = { Icon(Icons.Default.Add, null) },
        text = { Text(text, style = MaterialTheme.typography.labelLarge) },
        containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
}

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

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(job.title, style = MaterialTheme.typography.headlineMedium)
            customer?.let {
                Text(it.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onOpenCustomer(it.id) })
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Status – antippen zum Ändern", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JobStatus.entries.forEach { s ->
                    val on = job.status == s
                    val look = statusLook(s)
                    Surface(
                        onClick = { scope.launch { db.jobs().setStatus(job.id, s) } },
                        shape = RoundedCornerShape(16.dp),
                        color = if (on) look.bg else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (on) look.fg else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = BorderStroke(2.dp, if (on) look.fg else MaterialTheme.colorScheme.outline),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(look.icon, null, Modifier.size(18.dp))
                            Text(s.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        val place = job.location.ifBlank { customer?.address.orEmpty() }
        val phone = customer?.phone.orEmpty()
        if (place.isNotBlank() || phone.isNotBlank()) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (place.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary)
                        Text(place, style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (place.isNotBlank()) BigButton("Route", {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place))))
                        }, Modifier.weight(1f), icon = Icons.Default.Directions, style = BtnStyle.Outline, height = 52.dp)
                        if (phone.isNotBlank()) BigButton("Anrufen", {
                            ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                        }, Modifier.weight(1f), icon = Icons.Default.Call, style = BtnStyle.Outline, height = 52.dp)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Arbeitszeit", times.sumOf { it.netMinutes }.let { if (it > 0) formatDuration(it) else "–" },
                Modifier.weight(1f)) { onShowTab(1) }
            StatTile("Material", "${materials.size} Pos.", Modifier.weight(1f)) { onShowTab(3) }
            StatTile("Fotos", photos.size.toString(), Modifier.weight(1f)) { onShowTab(4) }
        }

        if (job.description.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Beschreibung", style = MaterialTheme.typography.titleLarge)
                Text(job.description, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (photos.isNotEmpty()) {
            SectionHeader("Fotos", "Alle ${photos.size}") { onShowTab(4) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                photos.take(3).forEach { p ->
                    AsyncImage(
                        model = a.files.file(p.fileName), contentDescription = p.caption,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onShowTab(4) },
                    )
                }
                repeat(3 - photos.size.coerceAtMost(3)) { Box(Modifier.weight(1f)) }
            }
        }

        SectionHeader("Prüfprotokolle", if (protocols.isEmpty()) "Hinzufügen" else "Alle ${protocols.size}") { onShowTab(5) }
        if (protocols.isEmpty()) {
            Text("Noch keine Protokolle.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        protocols.take(3).forEach { p ->
            AppCard(Modifier.fillMaxWidth(), onClick = { a.files.open(p) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFDECEC), contentColor = Color(0xFFB3261E),
                        modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.PictureAsPdf, null, Modifier.padding(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(p.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(formatMillis(p.createdAt), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (notes.isNotEmpty()) {
            SectionHeader("Notizen", "Alle ${notes.size}") { onShowTab(2) }
            notes.take(2).forEach { n -> NoteCard(n) { onEditNote(n) } }
        }
    }
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
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = Color(0xFFFFF8E1), contentColor = Color(0xFF14202E),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatMillis(n.createdAt), style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B5300))
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
private fun MaterialsTab(jobId: Long, onEdit: (Material) -> Unit) {
    val db = app().db
    val mats by remember(jobId) { db.materials().forJob(jobId) }.collectAsState(initial = emptyList())
    if (mats.isEmpty()) return EmptyHint("Noch kein Material erfasst.")
    LazyColumn(contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(mats, key = { it.id }) { m ->
            AppCard(Modifier.fillMaxWidth(), onClick = { onEdit(m) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${formatQty(m.quantity)} ${m.unit}", Modifier.padding(end = 14.dp), style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.bodyLarge)
                        if (m.note.isNotBlank()) Text(m.note, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
