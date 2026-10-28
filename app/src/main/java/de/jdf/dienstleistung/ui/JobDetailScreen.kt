package de.jdf.dienstleistung.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    // Dialog-Zustände der Unter-Tabs (FAB sitzt im gemeinsamen Scaffold)
    var editTime by remember { mutableStateOf<TimeEntry?>(null) }
    var editNote by remember { mutableStateOf<Note?>(null) }
    var editMaterial by remember { mutableStateOf<Material?>(null) }

    val j = job
    Scaffold(
        topBar = {
            BackTopBar(j?.title ?: "", onBack) {
                IconButton(onClick = { onEdit(jobId) }) { Icon(Icons.Default.Edit, "Bearbeiten") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Löschen") }
            }
        },
        floatingActionButton = {
            when (tab) {
                1 -> Fab("Zeit") { editTime = TimeEntry(jobId = jobId, date = todayEpochDay(), startMinutes = -1, endMinutes = -1) }
                2 -> Fab("Notiz") { editNote = Note(jobId = jobId, text = "") }
                3 -> Fab("Material") { editMaterial = Material(jobId = jobId, name = "") }
            }
        },
    ) { pad ->
        if (j == null) return@Scaffold
        Column(Modifier.padding(pad).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> OverviewTab(j, onOpenCustomer)
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
                db.attachments().allForJob(j.id).forEach { a.files.delete(it.fileName) }
                db.jobs().delete(j)
                onBack()
            }
        },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun Fab(text: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(onClick = onClick, icon = { Icon(Icons.Default.Add, null) }, text = { Text(text) })
}

@Composable
private fun OverviewTab(job: Job, onOpenCustomer: (Long) -> Unit) {
    val db = app().db
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val customer by remember(job.customerId) { db.customers().byId(job.customerId ?: -1) }.collectAsState(initial = null)
    val times by remember(job.id) { db.times().forJob(job.id) }.collectAsState(initial = emptyList())
    val materials by remember(job.id) { db.materials().forJob(job.id) }.collectAsState(initial = emptyList())
    val photos by remember(job.id) { db.attachments().forJob(job.id, AttachmentKind.PHOTO) }.collectAsState(initial = emptyList())
    val protocols by remember(job.id) { db.attachments().forJob(job.id, AttachmentKind.PROTOCOL) }.collectAsState(initial = emptyList())

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Status", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            JobStatus.entries.forEach { s ->
                FilterChip(
                    selected = job.status == s,
                    onClick = { scope.launch { db.jobs().setStatus(job.id, s) } },
                    label = { Text(s.label) },
                )
            }
        }

        customer?.let { c ->
            Card(Modifier.fillMaxWidth().clickable { onOpenCustomer(c.id) }) {
                Column(Modifier.padding(16.dp)) {
                    Text("Kunde", style = MaterialTheme.typography.labelMedium)
                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                    if (c.address.isNotBlank()) Text(c.address)
                    if (c.phone.isNotBlank()) Text("Tel: ${c.phone}")
                }
            }
        }

        val place = job.location.ifBlank { customer?.address.orEmpty() }
        if (place.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Einsatzort", style = MaterialTheme.typography.labelMedium)
                    Text(place)
                }
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place))))
                }) { Icon(Icons.Default.Directions, null); Text(" Route") }
            }
        }

        if (job.description.isNotBlank()) {
            Text("Beschreibung", style = MaterialTheme.typography.labelMedium)
            Text(job.description)
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Zusammenfassung", style = MaterialTheme.typography.titleSmall)
                Text("Arbeitszeit: ${formatDuration(times.sumOf { it.netMinutes })} (${times.size} Einträge)")
                Text("Material: ${materials.size} Positionen")
                Text("Fotos: ${photos.size} · Protokolle: ${protocols.size}")
                Text("Angelegt: ${formatMillis(job.createdAt)}", style = MaterialTheme.typography.bodySmall)
                Text("Geändert: ${formatMillis(job.updatedAt)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TimesTab(jobId: Long, onEdit: (TimeEntry) -> Unit) {
    val db = app().db
    val times by remember(jobId) { db.times().forJob(jobId) }.collectAsState(initial = emptyList())
    if (times.isEmpty()) return EmptyHint("Noch keine Zeiten erfasst.")
    LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Gesamt: ${formatDuration(times.sumOf { it.netMinutes })}", style = MaterialTheme.typography.titleMedium)
        }
        items(times, key = { it.id }) { t ->
            Card(Modifier.fillMaxWidth().clickable { onEdit(t) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(formatEpochDay(t.date), style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${formatClock(t.startMinutes)} – ${formatClock(t.endMinutes)}" +
                                if (t.breakMinutes > 0) " (Pause ${t.breakMinutes} min)" else ""
                        )
                        if (t.note.isNotBlank()) Text(t.note, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(formatDuration(t.netMinutes), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun NotesTab(jobId: Long, onEdit: (Note) -> Unit) {
    val db = app().db
    val notes by remember(jobId) { db.notes().forJob(jobId) }.collectAsState(initial = emptyList())
    if (notes.isEmpty()) return EmptyHint("Noch keine Notizen.")
    LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(notes, key = { it.id }) { n ->
            Card(Modifier.fillMaxWidth().clickable { onEdit(n) }) {
                Column(Modifier.padding(12.dp)) {
                    Text(formatMillis(n.createdAt), style = MaterialTheme.typography.labelSmall)
                    Text(n.text)
                }
            }
        }
    }
}

@Composable
private fun MaterialsTab(jobId: Long, onEdit: (Material) -> Unit) {
    val db = app().db
    val mats by remember(jobId) { db.materials().forJob(jobId) }.collectAsState(initial = emptyList())
    if (mats.isEmpty()) return EmptyHint("Noch kein Material erfasst.")
    LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(mats, key = { it.id }) { m ->
            Card(Modifier.fillMaxWidth().clickable { onEdit(m) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${formatQty(m.quantity)} ${m.unit}", Modifier.padding(end = 12.dp), style = MaterialTheme.typography.titleSmall)
                    Column(Modifier.weight(1f)) {
                        Text(m.name)
                        if (m.note.isNotBlank()) Text(m.note, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
