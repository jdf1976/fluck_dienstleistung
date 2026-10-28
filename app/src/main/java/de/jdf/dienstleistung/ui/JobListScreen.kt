package de.jdf.dienstleistung.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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

    Scaffold { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            PageTitle("Aufträge") {
                FilledIconButton(
                    onClick = onNew, modifier = Modifier.size(56.dp), shape = RoundedCornerShape(18.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = SignalYellow, contentColor = Navy),
                ) { Icon(Icons.Default.Add, "Neuer Auftrag", Modifier.size(30.dp)) }
            }
            SearchField(query, { query = it }, "Kunde, Ort, Auftrag …")
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BigChip("Offen", filter == null && !showAll) { filter = null; showAll = false }
                BigChip("Alle", filter == null && showAll) { filter = null; showAll = true }
                JobStatus.entries.forEach { s -> BigChip(s.label, filter == s) { filter = s } }
            }
            if (shown.isEmpty()) {
                EmptyHint(if (jobs.isEmpty()) "Noch keine Aufträge.\nMit + einen neuen anlegen." else "Keine Treffer.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.job.id }) { JobCard(it) { onOpen(it.job.id) } }
                }
            }
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        leadingIcon = { Icon(Icons.Default.Search, null) },
        placeholder = { Text(placeholder) }, singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    )
}

@Composable
fun BigChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        modifier = Modifier.height(44.dp),
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            labelColor = MaterialTheme.colorScheme.primary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected,
            borderColor = MaterialTheme.colorScheme.outline, selectedBorderColor = MaterialTheme.colorScheme.primary,
            borderWidth = 2.dp, selectedBorderWidth = 2.dp,
        ),
    )
}

@Composable
fun JobCard(item: JobListItem, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.job.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                StatusBadge(item.job.status)
            }
            Text(
                listOfNotNull(item.customerName, item.job.location.ifBlank { null }).joinToString(" · ").ifEmpty { "ohne Kunde" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (item.totalMinutes > 0) "Arbeitszeit ${formatDuration(item.totalMinutes)}" else "Noch keine Zeiten",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
