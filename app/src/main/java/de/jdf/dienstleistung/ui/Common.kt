package de.jdf.dienstleistung.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jdf.dienstleistung.App
import de.jdf.dienstleistung.data.JobStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme(primary = Color(0xFF1E3A5F), secondary = Color(0xFFF2A900))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun app(): App = LocalContext.current.applicationContext as App

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
        },
        actions = { actions() },
    )
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

fun statusColor(s: JobStatus): Color = when (s) {
    JobStatus.REQUEST -> Color(0xFF9E9E9E)
    JobStatus.PLANNED -> Color(0xFF1E88E5)
    JobStatus.IN_PROGRESS -> Color(0xFFF2A900)
    JobStatus.DONE -> Color(0xFF43A047)
    JobStatus.INVOICED -> Color(0xFF6D4C41)
}

@Composable
fun StatusChip(status: JobStatus, onClick: () -> Unit = {}) {
    AssistChip(
        onClick = onClick,
        label = { Text(status.label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = statusColor(status).copy(alpha = 0.18f),
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
fun SectionColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

// ---- Formatierung ----

private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val dateTimeFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

fun formatEpochDay(day: Long): String = LocalDate.ofEpochDay(day).format(dateFmt)

fun formatMillis(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateTimeFmt)

fun formatClock(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

/** 125 -> "2:05 h" */
fun formatDuration(minutes: Int): String = "%d:%02d h".format(minutes / 60, minutes % 60)

/** "7:30", "730", "7" -> Minuten seit Mitternacht */
fun parseClock(s: String): Int? {
    val t = s.trim().replace('.', ':')
    val (h, m) = when {
        ':' in t -> t.substringBefore(':') to t.substringAfter(':')
        t.length in 3..4 -> t.dropLast(2) to t.takeLast(2)
        else -> t to "0"
    }
    val hh = h.toIntOrNull() ?: return null
    val mm = m.ifEmpty { "0" }.toIntOrNull() ?: return null
    if (hh !in 0..24 || mm !in 0..59 || (hh == 24 && mm > 0)) return null
    return hh * 60 + mm
}

fun formatQty(q: Double): String =
    if (q % 1.0 == 0.0) q.toLong().toString() else q.toString().replace('.', ',')
