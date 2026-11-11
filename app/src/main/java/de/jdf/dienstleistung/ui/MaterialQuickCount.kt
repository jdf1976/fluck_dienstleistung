package de.jdf.dienstleistung.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.jdf.dienstleistung.data.Material
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Start-Artikel der Zählknöpfe; eigene kommen dazu und bleiben gespeichert. */
private val DEFAULT_ITEMS = listOf(
    "Steckdosen", "Schalter", "Taster", "Schalterwippen", "Serienschalter", "Kreuzschalter",
    "Einfachrahmen", "Zweifachrahmen", "Dreifachrahmen", "Vierfachrahmen", "Fünffachrahmen",
)

/** Artikelliste (für alle Aufträge) und Zählerstände je Auftrag. */
private class CountStore(context: Context) {
    private val prefs = context.getSharedPreferences("material_counts", Context.MODE_PRIVATE)

    fun items(): List<String> {
        val raw = prefs.getString("items", null) ?: return DEFAULT_ITEMS
        val arr = JSONArray(raw)
        return List(arr.length()) { arr.getString(it) }
    }

    fun saveItems(items: List<String>) {
        prefs.edit().putString("items", JSONArray(items).toString()).apply()
    }

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
 * Eigene Seite für den Baustellen-Durchgang: jeder Tipp zählt +1, „–“ nimmt eins weg.
 * „Übernehmen“ addiert alles in die Materialliste des Auftrags und setzt die Zähler zurück.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickCountScreen(jobId: Long, onBack: () -> Unit) {
    val a = app()
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val store = remember { CountStore(ctx) }
    val job by remember(jobId) { a.db.jobs().byId(jobId) }.collectAsState(initial = null)
    var articles by remember { mutableStateOf(store.items()) }
    var counts by remember(jobId) { mutableStateOf(store.load(jobId)) }
    var confirmReset by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<String?>(null) }

    fun change(name: String, delta: Int) {
        val n = ((counts[name] ?: 0) + delta).coerceAtLeast(0)
        counts = counts + (name to n)
        store.save(jobId, counts)
        haptic.performHapticFeedback(if (delta > 0) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
    }

    val total = counts.filterKeys { it in articles }.values.sum()
    val positions = articles.count { (counts[it] ?: 0) > 0 }

    Scaffold(
        topBar = {
            BackTopBar("Schnellzählung", onBack) {
                if (total > 0) TextButton(onClick = { confirmReset = true }, modifier = Modifier.height(48.dp)) {
                    Text("Auf 0", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shadowElevation = 8.dp) {
                BigButton(
                    if (total > 0) "Übernehmen: $positions Pos. / $total Stk" else "Zum Zählen Artikel antippen",
                    onClick = {
                        val picked = articles.mapNotNull { n -> counts[n]?.takeIf { it > 0 }?.let { n to it } }
                        scope.launch {
                            val dao = a.db.materials()
                            picked.forEach { (name, n) ->
                                val existing = dao.find(jobId, name, "Stk")
                                if (existing != null) dao.upsert(existing.copy(quantity = existing.quantity + n))
                                else dao.upsert(Material(jobId = jobId, name = name, quantity = n.toDouble(), unit = "Stk"))
                            }
                            a.db.jobs().touch(jobId)
                            counts = emptyMap()
                            store.save(jobId, counts)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            Toast.makeText(ctx, "${picked.size} Positionen in die Materialliste übernommen", Toast.LENGTH_LONG).show()
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp, 12.dp),
                    icon = Icons.Default.Check,
                    enabled = total > 0,
                )
            }
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    job?.let { Text(it.title, style = MaterialTheme.typography.titleLarge) }
                    Text("Antippen = +1 · „–“ = eins weniger · lange drücken = Artikel entfernen",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(articles, key = { it }) { name ->
                CountTile(
                    name = name, count = counts[name] ?: 0,
                    onPlus = { change(name, +1) }, onMinus = { change(name, -1) },
                    onLongPress = { removing = name },
                )
            }
            item {
                // Neues Feld anlegen
                Box(
                    Modifier.height(132.dp).clip(RoundedCornerShape(24.dp))
                        .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(24.dp))
                        .combinedClickable(onClick = { adding = true }),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Artikel\nhinzufügen", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (adding) AddItemDialog(
        existing = articles,
        onAdd = { name -> articles = articles + name; store.saveItems(articles); adding = false },
        onDismiss = { adding = false },
    )

    removing?.let { name ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("„$name“ entfernen?") },
            text = { Text("Der Zählknopf verschwindet für alle Aufträge. Bereits übernommenes Material bleibt in den Listen.") },
            confirmButton = {
                TextButton(onClick = {
                    articles = articles - name; store.saveItems(articles)
                    counts = counts - name; store.save(jobId, counts)
                    removing = null
                }) { Text("Entfernen") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Abbrechen") } },
        )
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Zähler zurücksetzen?") },
        text = { Text("Alle gezählten Mengen dieses Durchgangs werden gelöscht.") },
        confirmButton = {
            TextButton(onClick = { counts = emptyMap(); store.save(jobId, counts); confirmReset = false }) { Text("Zurücksetzen") }
        },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Abbrechen") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CountTile(name: String, count: Int, onPlus: () -> Unit, onMinus: () -> Unit, onLongPress: () -> Unit) {
    val active = count > 0
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.height(132.dp).clip(shape)
            .background(if (active) SignalYellow else MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(BorderStroke(1.dp, if (active) SignalYellow else MaterialTheme.colorScheme.outlineVariant), shape)
            .combinedClickable(onClick = onPlus, onLongClick = onLongPress),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (active) Navy else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 4.dp),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (active) Navy else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = onMinus, enabled = active, modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Navy, contentColor = SignalYellow,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.outline,
                    ),
                ) { Icon(Icons.Default.Remove, "$name eins weniger", Modifier.size(28.dp)) }
            }
        }
    }
}

@Composable
private fun AddItemDialog(existing: List<String>, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val clean = name.trim()
    val duplicate = existing.any { it.equals(clean, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Artikel hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it }, Modifier.fillMaxWidth(),
                    label = { Text("Bezeichnung") }, placeholder = { Text("z. B. Wechselschalter") },
                    singleLine = true, isError = duplicate,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (duplicate) Text("Gibt es schon.", color = MaterialTheme.colorScheme.error)
                Text("Der Knopf bleibt gespeichert und steht in allen Aufträgen zur Verfügung.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(clean) }, enabled = clean.isNotEmpty() && !duplicate) { Text("Hinzufügen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
