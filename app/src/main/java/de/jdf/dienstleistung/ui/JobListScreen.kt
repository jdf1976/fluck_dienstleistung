package de.jdf.dienstleistung.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.jdf.dienstleistung.data.JobListItem
import de.jdf.dienstleistung.data.JobStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobListScreen(onOpen: (Long) -> Unit, onNew: () -> Unit) {
    val db = app().db
    val jobs by remember { db.jobs().list() }.collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    // null = alle offenen (ohne Abgerechnet)
    var filter by rememberSaveable { mutableStateOf<JobStatus?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }

    val shown = jobs.filter { item ->
        val statusOk = when {
            filter != null -> item.job.status == filter
            showAll -> true
            else -> item.job.status != JobStatus.INVOICED
        }
        val q = query.trim()
        statusOk && (q.isEmpty() || listOf(item.job.title, item.customerName.orEmpty(), item.job.location, item.job.description)
            .any { it.contains(q, ignoreCase = true) })
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Aufträge") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) { Icon(Icons.Default.Add, "Neuer Auftrag") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Suchen …") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == null && !showAll,
                    onClick = { filter = null; showAll = false },
                    label = { Text("Offen") },
                )
                FilterChip(
                    selected = filter == null && showAll,
                    onClick = { filter = null; showAll = true },
                    label = { Text("Alle") },
                )
                JobStatus.entries.forEach { s ->
                    FilterChip(selected = filter == s, onClick = { filter = s }, label = { Text(s.label) })
                }
            }
            if (shown.isEmpty()) {
                EmptyHint(if (jobs.isEmpty()) "Noch keine Aufträge.\nMit + einen neuen anlegen." else "Keine Treffer.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.job.id }) { JobCard(it) { onOpen(it.job.id) } }
                }
            }
        }
    }
}

@Composable
fun JobCard(item: JobListItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = statusColor(item.job.status), modifier = Modifier.size(12.dp)) {}
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.job.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(item.customerName, item.job.location.ifBlank { null }).joinToString(" · ").ifEmpty { "ohne Kunde" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(item.job.status.label, style = MaterialTheme.typography.labelMedium)
                if (item.totalMinutes > 0) Text(formatDuration(item.totalMinutes), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
