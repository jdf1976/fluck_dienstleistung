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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
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
    "Steckdosen", "Schalter", "Wechselschalter", "Taster", "Schalterwippen", "Serienschalter", "Kreuzschalter",
    "Einfachrahmen", "Zweifachrahmen", "Dreifachrahmen", "Vierfachrahmen", "Fünffachrahmen",
    "Abzweigdosen", "Herdanschlussdosen", "Netzwerkdosen einfach", "Netzwerkdosen doppelt", "TV-Dosen",
)

/** Mit Version 2 neu dazugekommen – werden bestehenden Listen einmalig angehängt. */
private val ADDED_V2 = listOf(
    "Wechselschalter", "Abzweigdosen", "Herdanschlussdosen", "Netzwerkdosen einfach", "Netzwerkdosen doppelt", "TV-Dosen",
)

/** Artikelliste (für alle Aufträge) und Zählerstände je Auftrag. */
private class CountStore(context: Context) {
    private val prefs = context.getSharedPreferences("material_counts", Context.MODE_PRIVATE)

    fun items(): List<String> {
        val raw = prefs.getString("items", null) ?: return DEFAULT_ITEMS
        val arr = JSONArray(raw)
        val list = List(arr.length()) { arr.getString(it) }
        if (prefs.getInt("defaultsVersion", 1) >= 2) return list
        val merged = list + ADDED_V2.filter { add -> list.none { it.equals(add, ignoreCase = true) } }
        saveItems(merged)
        prefs.edit().putInt("defaultsVersion", 2).apply()
        return merged
    }

    fun saveItems(items: List<String>) {
        prefs.edit().putString("items", JSONArray(items).toString()).putInt("defaultsVersion", 2).apply()
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
                TextButton(onClick = { adding = true }, modifier = Modifier.height(48.dp)) {
                    Text("+ Artikel", style = MaterialTheme.typography.labelLarge)
                }
                if (total > 0) TextButton(onClick = { confirmReset = true }, modifier = Modifier.height(48.dp)) {
                    Text("Auf 0", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
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
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(14.dp, 12.dp),
                    icon = Icons.Default.Check,
                    enabled = total > 0,
                )
            }
        },
    ) { pad ->
        val groups = articles.groupBy { groupOf(it) }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    job?.let { Text(it.title, style = MaterialTheme.typography.titleMedium) }
                    Text("+ zählt hoch, – zählt runter · Name lange drücken = Artikel entfernen",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            GROUP_ORDER.forEach { g ->
                val list = groups[g].orEmpty()
                if (list.isNotEmpty()) {
                    item(key = "h_$g") { SectionLabel(g) }
                    items(list, key = { it }) { name ->
                        CountRow(name, counts[name] ?: 0,
                            onPlus = { change(name, +1) }, onMinus = { change(name, -1) }, onLongPress = { removing = name })
                    }
                }
            }
            item {
                TextButton(onClick = { adding = true }, modifier = Modifier.padding(start = 8.dp, top = 8.dp).height(52.dp)) {
                    Icon(Icons.Default.Add, null); Text(" Artikel hinzufügen", style = MaterialTheme.typography.labelLarge)
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

private val GROUP_ORDER = listOf("Schalter & Taster", "Dosen", "Rahmen", "Weitere")

private fun groupOf(name: String): String {
    val n = name.lowercase()
    return when {
        "rahmen" in n -> "Rahmen"
        "dose" in n -> "Dosen"
        "schalter" in n || "taster" in n || "wippe" in n -> "Schalter & Taster"
        else -> "Weitere"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CountRow(name: String, count: Int, onPlus: () -> Unit, onMinus: () -> Unit, onLongPress: () -> Unit) {
    val active = count > 0
    val dark = LocalIsDark.current
    Column(Modifier.fillMaxWidth().background(
        if (!active) MaterialTheme.colorScheme.background else if (dark) androidx.compose.ui.graphics.Color(0xFF2A2410)
        else androidx.compose.ui.graphics.Color(0xFFFFF8E1))) {
        Row(Modifier.fillMaxWidth().height(68.dp).padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).combinedClickable(onClick = onPlus, onLongClick = onLongPress),
            )
            Surface(
                onClick = onMinus, enabled = active, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.primary,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Remove, "$name eins weniger", Modifier.size(26.dp),
                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                }
            }
            Text(
                count.toString(), modifier = Modifier.size(width = 52.dp, height = 40.dp),
                style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
            Surface(
                onClick = onPlus, modifier = Modifier.size(56.dp), shape = RoundedCornerShape(14.dp),
                color = Navy, contentColor = SignalYellow,
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "$name eins mehr", Modifier.size(30.dp)) }
            }
        }
        androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
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
