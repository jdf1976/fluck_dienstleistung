package de.jdf.dienstleistung.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.jdf.dienstleistung.data.JobListItem
import de.jdf.dienstleistung.data.RunningTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Aktuelle Zeit in ms, aktualisiert jede Sekunde (nur solange [active]). */
@Composable
fun rememberNow(active: Boolean): Long {
    val now by produceState(System.currentTimeMillis(), active) {
        while (active) {
            value = System.currentTimeMillis()
            delay(1000 - (value % 1000))
        }
    }
    return now
}

@Composable
fun runningTimer(): RunningTimer? {
    val t by app().timer.state.collectAsState()
    return t
}

/** Start/Stopp mit Rückmeldung (Hinweis + Vibration). */
class TimerActions(val start: (Long) -> Unit, val stop: () -> Unit)

@Composable
fun rememberTimerActions(): TimerActions {
    val timer = app().timer
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    return remember(timer) {
        fun saved(min: Int?) = when (min) {
            null -> null
            0 -> "Unter einer Minute – nicht gespeichert"
            else -> "${formatDuration(min)} gespeichert"
        }
        TimerActions(
            start = { jobId ->
                scope.launch {
                    val prev = saved(timer.start(jobId))
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(ctx, listOfNotNull(prev, "Timer läuft").joinToString(" · "), Toast.LENGTH_SHORT).show()
                }
            },
            stop = {
                scope.launch {
                    val msg = saved(timer.stop()) ?: return@launch
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                }
            },
        )
    }
}

/** Schmale Leiste mit laufender Zeit – erscheint auf allen anderen Seiten. */
@Composable
fun MiniTimerBar(timer: RunningTimer, onOpen: () -> Unit, onStop: () -> Unit) {
    val now = rememberNow(true)
    Surface(color = Navy, contentColor = Color.White) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiveDot()
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(timer.jobTitle, style = MaterialTheme.typography.labelMedium, color = Color(0xFFC9D6E8),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatStopwatch((now - timer.startedAt) / 1000), style = MaterialTheme.typography.titleLarge)
            }
            Button(onClick = onStop, modifier = Modifier.height(48.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Navy)) {
                Icon(Icons.Default.Stop, null); Text(" Stopp", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Auswahlliste „Auftrag wählen“ (von unten). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobPickerSheet(jobs: List<JobListItem>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text("Auftrag wählen", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))
        if (jobs.isEmpty()) {
            Text("Keine offenen Aufträge.", Modifier.padding(20.dp))
        }
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(jobs, key = { it.job.id }) { item ->
                Surface(
                    onClick = { onPick(item.job.id) },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.job.title, style = MaterialTheme.typography.titleMedium)
                            Text(item.customerName ?: "ohne Kunde", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(shape = RoundedCornerShape(14.dp), color = SignalYellow, contentColor = Navy,
                            modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.PlayArrow, "Starten", Modifier.padding(10.dp))
                        }
                    }
                }
            }
        }
    }
}
