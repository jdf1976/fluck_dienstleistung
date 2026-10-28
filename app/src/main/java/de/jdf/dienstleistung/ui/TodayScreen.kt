package de.jdf.dienstleistung.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.jdf.dienstleistung.data.JobListItem
import de.jdf.dienstleistung.data.JobStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private val openOrder = listOf(JobStatus.IN_PROGRESS, JobStatus.PLANNED, JobStatus.REQUEST)

fun openJobs(all: List<JobListItem>): List<JobListItem> =
    all.filter { it.job.status in openOrder }
        .sortedWith(compareBy<JobListItem> { openOrder.indexOf(it.job.status) }.thenByDescending { it.job.updatedAt })

@Composable
fun TodayScreen(onOpenJob: (Long) -> Unit, onShowJobs: () -> Unit, onNewJob: () -> Unit) {
    val a = app()
    val jobs by remember { a.db.jobs().list() }.collectAsState(initial = emptyList())
    val today = LocalDate.now()
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekMinutes by remember(monday) {
        a.db.times().minutesBetween(monday.toEpochDay(), monday.plusDays(6).toEpochDay())
    }.collectAsState(initial = 0)
    val timer = runningTimer()
    val actions = rememberTimerActions()
    var picker by remember { mutableStateOf(false) }

    val open = openJobs(jobs)
    val suggested = open.firstOrNull()

    Scaffold { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(Modifier.padding(top = 20.dp)) {
                    Text(formatLongDate(today), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Heute", style = MaterialTheme.typography.headlineLarge)
                }
            }

            item {
                if (timer != null) {
                    RunningCard(timer.jobTitle, jobs.firstOrNull { it.job.id == timer.jobId }, timer.startedAt,
                        onOpen = { onOpenJob(timer.jobId) }, onStop = actions.stop)
                } else {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (suggested != null) "Kein Timer aktiv · Vorschlag" else "Kein Timer aktiv",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (suggested != null) {
                                Column {
                                    Text(suggested.job.title, style = MaterialTheme.typography.titleLarge)
                                    Text(listOfNotNull(suggested.customerName, suggested.job.location.ifBlank { null }).joinToString(" · "),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                BigButton("Arbeit starten", { actions.start(suggested.job.id) }, Modifier.fillMaxWidth(),
                                    icon = Icons.Default.PlayArrow)
                                BigButton("Anderer Auftrag …", { picker = true }, Modifier.fillMaxWidth(),
                                    style = BtnStyle.Outline, height = 52.dp)
                            } else {
                                Text("Noch keine offenen Aufträge.", style = MaterialTheme.typography.bodyLarge)
                                BigButton("Auftrag anlegen", onNewJob, Modifier.fillMaxWidth(), icon = Icons.Default.Add)
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Diese Woche", formatDuration(weekMinutes), Modifier.weight(1f))
                    StatTile("Offene Aufträge", open.size.toString(), Modifier.weight(1f), onClick = onShowJobs)
                }
            }

            if (open.isNotEmpty()) {
                item { Text("Als Nächstes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp)) }
                items(open.take(4), key = { it.job.id }) { JobCard(it) { onOpenJob(it.job.id) } }
            }
        }
    }

    if (picker) JobPickerSheet(open, onPick = { picker = false; actions.start(it) }, onDismiss = { picker = false })
}

@Composable
private fun RunningCard(title: String, item: JobListItem?, startedAt: Long, onOpen: () -> Unit, onStop: () -> Unit) {
    val now = rememberNow(true)
    val started = java.time.Instant.ofEpochMilli(startedAt).atZone(java.time.ZoneId.systemDefault())
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Navy, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveDot()
                Text("Läuft seit %02d:%02d Uhr".format(started.hour, started.minute),
                    style = MaterialTheme.typography.labelMedium, color = Color(0xFFC9D6E8))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                val sub = listOfNotNull(item?.customerName, item?.job?.location?.ifBlank { null }).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC9D6E8))
            }
            Text(formatStopwatch((now - startedAt) / 1000), style = MaterialTheme.typography.displaySmall)
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp).then(Modifier),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Navy),
                contentPadding = PaddingValues(vertical = 20.dp),
            ) {
                Icon(Icons.Default.Stop, null); Text("  Stopp & speichern", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
