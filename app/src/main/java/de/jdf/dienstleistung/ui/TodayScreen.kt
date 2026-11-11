package de.jdf.dienstleistung.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.jdf.dienstleistung.data.JobListItem
import de.jdf.dienstleistung.data.JobStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

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
    val working = open.filter { it.job.status == JobStatus.IN_PROGRESS }
    val rest = open.filter { it.job.status != JobStatus.IN_PROGRESS }
    val suggested = open.firstOrNull()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNewJob, containerColor = SignalYellow, contentColor = Navy,
                shape = RoundedCornerShape(20.dp), modifier = Modifier.size(64.dp)) {
                Icon(Icons.Default.Add, "Neuer Auftrag", Modifier.size(30.dp))
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            today.format(DateTimeFormatter.ofPattern("EE, d. MMM", Locale.GERMAN)) +
                                " · KW ${today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Heute", style = MaterialTheme.typography.headlineLarge)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatDuration(weekMinutes), style = MaterialTheme.typography.titleLarge)
                        Text("diese Woche", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                if (timer != null) {
                    TimerStrip(timer.jobTitle, timer.startedAt, onOpen = { onOpenJob(timer.jobId) }, onStop = actions.stop)
                } else if (suggested != null) {
                    IdleStrip(suggested, onStart = { actions.start(suggested.job.id) }, onOther = { picker = true })
                }
            }

            if (working.isNotEmpty()) {
                item { SectionLabel("In Arbeit · ${working.size}") }
                items(working, key = { it.job.id }) { JobRow(it) { onOpenJob(it.job.id) } }
            }
            if (rest.isNotEmpty()) {
                item {
                    SectionLabel("Offen · ${rest.size}") {
                        TextButton(onClick = onShowJobs, modifier = Modifier.height(40.dp)) { Text("Alle") }
                    }
                }
                items(rest.take(8), key = { it.job.id }) { JobRow(it) { onOpenJob(it.job.id) } }
            }
            if (open.isEmpty()) {
                item {
                    Text("Keine offenen Aufträge. Mit + einen neuen anlegen.", Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (picker) JobPickerSheet(open, onPick = { picker = false; actions.start(it) }, onDismiss = { picker = false })
}

/** Laufender Timer als kompakter Streifen. */
@Composable
private fun TimerStrip(title: String, startedAt: Long, onOpen: () -> Unit, onStop: () -> Unit) {
    val now = rememberNow(true)
    Surface(
        onClick = onOpen, shape = RoundedCornerShape(18.dp), color = Navy, contentColor = Color.White,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiveDot()
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC9D6E8),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatStopwatch((now - startedAt) / 1000), style = MaterialTheme.typography.headlineMedium)
            }
            Button(onClick = onStop, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SignalYellow, contentColor = Navy)) {
                Icon(Icons.Default.Stop, null); Text(" Stopp", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** Kein Timer: Vorschlag mit Start-Knopf. */
@Composable
private fun IdleStrip(suggested: JobListItem, onStart: () -> Unit, onOther: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Kein Timer · Vorschlag", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(suggested.job.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Button(onClick = onStart, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SignalYellow, contentColor = Navy)) {
                    Icon(Icons.Default.PlayArrow, null); Text(" Start", style = MaterialTheme.typography.titleSmall)
                }
            }
            TextButton(onClick = onOther, modifier = Modifier.height(44.dp)) { Text("Anderen Auftrag wählen …") }
        }
    }
}
