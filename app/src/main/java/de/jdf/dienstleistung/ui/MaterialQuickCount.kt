package de.jdf.dienstleistung.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.jdf.dienstleistung.data.Note
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Artikel der Zähl-Knöpfe, in dieser Reihenfolge auch in der Notiz. */
private val QUICK_ITEMS = listOf(
    "Steckdosen", "Schalter", "Taster", "Schalterwippen", "Serienschalter", "Kreuzschalter",
    "Einfachrahmen", "Zweifachrahmen", "Dreifachrahmen", "Vierfachrahmen", "Fünffachrahmen",
)

/** Zählerstände je Auftrag; überstehen das Schließen der App bis „Fertig“ gedrückt wird. */
private class CountStore(context: Context) {
    private val prefs = context.getSharedPreferences("material_counts", Context.MODE_PRIVATE)

    fun load(jobId: Long): Map<String, Int> {
        val json = JSONObject(prefs.getString("job_$jobId", null) ?: return emptyMap())
        return json.keys().asSequence().associateWith { json.getInt(it) }
    }

    fun save(jobId: Long, counts: Map<String, Int>) {
        val clean = counts.filterValues { it > 0 }
        if (clean.isEmpty()) prefs.edit().remove("job_$jobId").apply()
        else prefs.edit().putString("job_$jobId", JSONObject(clean).toString()).apply()
    }
}

/**
 * Baustellen-Durchgang: jeder Tipp auf einen Artikel zählt eins hoch.
 * „Fertig“ schreibt die Liste als Notiz in den Auftrag und setzt die Zähler zurück.
 */
@Composable
fun MaterialQuickCount(jobId: Long) {
    val a = app()
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val store = remember { CountStore(ctx) }
    var counts by remember(jobId) { mutableStateOf(store.load(jobId)) }
    var confirmReset by remember { mutableStateOf(false) }

    fun change(name: String, delta: Int) {
        val n = ((counts[name] ?: 0) + delta).coerceAtLeast(0)
        counts = counts + (name to n)
        store.save(jobId, counts)
        haptic.performHapticFeedback(if (delta > 0) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
    }

    val total = counts.values.sum()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Schnellzählen", style = MaterialTheme.typography.titleLarge)
        Text("Antippen zählt +1. Mit „–“ korrigieren.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        QUICK_ITEMS.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { name ->
                    CountTile(name, counts[name] ?: 0, Modifier.weight(1f),
                        onPlus = { change(name, +1) }, onMinus = { change(name, -1) })
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }

        BigButton(
            if (total > 0) "Fertig – $total Teile als Notiz" else "Fertig – als Notiz speichern",
            onClick = {
                val lines = QUICK_ITEMS.mapNotNull { n -> counts[n]?.takeIf { it > 0 }?.let { "$it × $n" } }
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                val text = "Material-Zählung $stamp\n" + lines.joinToString("\n")
                scope.launch {
                    a.db.notes().upsert(Note(jobId = jobId, text = text))
                    a.db.jobs().touch(jobId)
                    counts = emptyMap()
                    store.save(jobId, counts)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(ctx, "Notiz mit ${lines.size} Positionen gespeichert", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Check,
            enabled = total > 0,
        )
        if (total > 0) TextButton(onClick = { confirmReset = true }, modifier = Modifier.height(44.dp)) {
            Text("Alle Zähler auf 0 setzen", style = MaterialTheme.typography.labelLarge)
        }
    }

    if (confirmReset) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Zähler zurücksetzen?") },
        text = { Text("Alle gezählten Mengen werden gelöscht, ohne Notiz.") },
        confirmButton = {
            TextButton(onClick = { counts = emptyMap(); store.save(jobId, counts); confirmReset = false }) { Text("Zurücksetzen") }
        },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Abbrechen") } },
    )
}

@Composable
private fun CountTile(name: String, count: Int, modifier: Modifier, onPlus: () -> Unit, onMinus: () -> Unit) {
    val active = count > 0
    Surface(
        onClick = onPlus,
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(if (active) 2.dp else 1.dp,
            if (active) SignalYellow else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(count.toString(), style = MaterialTheme.typography.headlineSmall)
            }
            if (active) IconButton(onClick = onMinus, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Remove, "$name eins weniger")
            }
        }
    }
}
