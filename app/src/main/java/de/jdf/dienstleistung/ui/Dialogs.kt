package de.jdf.dienstleistung.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.jdf.dienstleistung.data.Material
import de.jdf.dienstleistung.data.Note
import de.jdf.dienstleistung.data.TimeEntry
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

private fun nowRoundedToQuarter(): Int {
    val t = LocalTime.now()
    return (t.hour * 60 + t.minute) / 15 * 15
}

/** Bearbeiten-Dialog mit Löschen-Knopf, wenn der Datensatz schon existiert. */
@Composable
private fun EditDialog(
    title: String,
    isNew: Boolean,
    canSave: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = canSave) { Text("Speichern") } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { confirm = true }) { Text("Löschen") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
    if (confirm) ConfirmDialog("Wirklich löschen?", "Der Eintrag wird entfernt.",
        onConfirm = { onDelete(); onDismiss() }, onDismiss = { confirm = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeEntryDialog(entry: TimeEntry, onDismiss: () -> Unit) {
    val db = app().db
    val scope = rememberCoroutineScope()
    val isNew = entry.id == 0L
    var date by remember { mutableLongStateOf(entry.date) }
    var start by remember { mutableStateOf(if (entry.startMinutes >= 0) formatClock(entry.startMinutes) else "") }
    var end by remember { mutableStateOf(if (entry.endMinutes >= 0) formatClock(entry.endMinutes) else "") }
    var pause by remember { mutableStateOf(if (entry.breakMinutes > 0) entry.breakMinutes.toString() else "") }
    var note by remember { mutableStateOf(entry.note) }
    var pickDate by remember { mutableStateOf(false) }

    val s = parseClock(start)
    val e = parseClock(end)
    val p = pause.trim().ifEmpty { "0" }.toIntOrNull()
    val valid = s != null && e != null && p != null && e > s && (e - s - p) >= 0

    EditDialog(
        title = if (isNew) "Zeit erfassen" else "Zeit bearbeiten",
        isNew = isNew,
        canSave = valid,
        onSave = {
            scope.launch {
                db.times().upsert(entry.copy(date = date, startMinutes = s!!, endMinutes = e!!, breakMinutes = p!!, note = note.trim()))
                db.jobs().touch(entry.jobId)
                onDismiss()
            }
        },
        onDelete = { scope.launch { db.times().delete(entry) } },
        onDismiss = onDismiss,
    ) {
        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.CalendarMonth, null); Text("  " + formatEpochDay(date))
        }
        val num = KeyboardOptions(keyboardType = KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(start, { start = it }, Modifier.weight(1f), label = { Text("Von") },
                placeholder = { Text("07:30") }, singleLine = true, keyboardOptions = num,
                isError = start.isNotEmpty() && s == null)
            OutlinedTextField(end, { end = it }, Modifier.weight(1f), label = { Text("Bis") },
                placeholder = { Text("16:00") }, singleLine = true, keyboardOptions = num,
                isError = end.isNotEmpty() && (e == null || (s != null && e <= s)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { start = formatClock(nowRoundedToQuarter()) }) { Text("Von = jetzt") }
            TextButton(onClick = { end = formatClock(nowRoundedToQuarter()) }) { Text("Bis = jetzt") }
        }
        OutlinedTextField(pause, { pause = it }, Modifier.fillMaxWidth(), label = { Text("Pause (Minuten)") },
            singleLine = true, keyboardOptions = num, isError = p == null)
        if (valid) Text("Netto: ${formatDuration(e!! - s!! - p!!)}")
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Tätigkeit") }, minLines = 2)
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = it / 86_400_000L }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Abbrechen") } },
        ) { DatePicker(state) }
    }
}

@Composable
fun NoteDialog(note: Note, onDismiss: () -> Unit) {
    val db = app().db
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(note.text) }
    EditDialog(
        title = if (note.id == 0L) "Neue Notiz" else "Notiz bearbeiten",
        isNew = note.id == 0L,
        canSave = text.isNotBlank(),
        onSave = {
            scope.launch {
                db.notes().upsert(note.copy(text = text.trim()))
                db.jobs().touch(note.jobId)
                onDismiss()
            }
        },
        onDelete = { scope.launch { db.notes().delete(note) } },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Notiz") }, minLines = 5)
    }
}

@Composable
fun MaterialDialog(material: Material, onDismiss: () -> Unit) {
    val db = app().db
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(material.name) }
    var qty by remember { mutableStateOf(formatQty(material.quantity)) }
    var unit by remember { mutableStateOf(material.unit) }
    var note by remember { mutableStateOf(material.note) }
    val q = qty.trim().replace(',', '.').toDoubleOrNull()

    EditDialog(
        title = if (material.id == 0L) "Material hinzufügen" else "Material bearbeiten",
        isNew = material.id == 0L,
        canSave = name.isNotBlank() && q != null,
        onSave = {
            scope.launch {
                db.materials().upsert(material.copy(name = name.trim(), quantity = q!!, unit = unit.trim(), note = note.trim()))
                db.jobs().touch(material.jobId)
                onDismiss()
            }
        },
        onDelete = { scope.launch { db.materials().delete(material) } },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Bezeichnung") },
            placeholder = { Text("NYM-J 3x1,5") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(qty, { qty = it }, Modifier.weight(1f), label = { Text("Menge") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = q == null)
            OutlinedTextField(unit, { unit = it }, Modifier.weight(1f), label = { Text("Einheit") }, singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Stk", "m", "Std", "Pkg").forEach { u -> TextButton(onClick = { unit = u }) { Text(u) } }
        }
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Bemerkung") }, singleLine = true)
    }
}
