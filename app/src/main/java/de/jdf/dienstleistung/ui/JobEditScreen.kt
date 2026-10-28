package de.jdf.dienstleistung.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.jdf.dienstleistung.data.Job
import de.jdf.dienstleistung.data.JobStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobEditScreen(
    jobId: Long,
    presetCustomerId: Long,
    onDone: (savedId: Long, isNew: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val db = app().db
    val scope = rememberCoroutineScope()
    val customers by remember { db.customers().all() }.collectAsState(initial = emptyList())

    var loaded by rememberSaveable { mutableStateOf(jobId == 0L) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf(JobStatus.REQUEST) }
    var customerId by rememberSaveable { mutableLongStateOf(presetCustomerId) }
    var original by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(jobId) {
        val j = db.jobs().get(jobId)
        original = j
        if (!loaded && j != null) {
            title = j.title; description = j.description; location = j.location
            status = j.status; customerId = j.customerId ?: 0L
        }
        loaded = true
    }

    fun save() = scope.launch {
        val base = original ?: Job(customerId = null, title = "")
        val id = db.jobs().upsert(
            base.copy(
                id = jobId,
                customerId = customerId.takeIf { it != 0L },
                title = title.trim(),
                description = description.trim(),
                location = location.trim(),
                status = status,
                updatedAt = System.currentTimeMillis(),
            )
        )
        onDone(if (jobId == 0L) id else jobId, jobId == 0L)
    }

    Scaffold(topBar = {
        BackTopBar(if (jobId == 0L) "Neuer Auftrag" else "Auftrag bearbeiten", onBack) {
            TextButton(onClick = { save() }, enabled = title.isNotBlank()) { Text("Speichern") }
        }
    }) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val m = Modifier.fillMaxWidth()
            OutlinedTextField(title, { title = it }, m, label = { Text("Titel *") }, singleLine = true,
                placeholder = { Text("z. B. Zählerschrank erneuern") })

            var custOpen by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(custOpen, { custOpen = it }) {
                OutlinedTextField(
                    value = customers.firstOrNull { it.id == customerId }?.name ?: "– kein Kunde –",
                    onValueChange = {}, readOnly = true, label = { Text("Kunde") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(custOpen) },
                    modifier = m.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(custOpen, { custOpen = false }) {
                    DropdownMenuItem(text = { Text("– kein Kunde –") }, onClick = { customerId = 0L; custOpen = false })
                    customers.forEach { c ->
                        DropdownMenuItem(text = { Text(c.name) }, onClick = { customerId = c.id; custOpen = false })
                    }
                }
            }

            var statusOpen by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(statusOpen, { statusOpen = it }) {
                OutlinedTextField(
                    value = status.label, onValueChange = {}, readOnly = true, label = { Text("Status") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(statusOpen) },
                    modifier = m.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(statusOpen, { statusOpen = false }) {
                    JobStatus.entries.forEach { s ->
                        DropdownMenuItem(text = { Text(s.label) }, onClick = { status = s; statusOpen = false })
                    }
                }
            }

            OutlinedTextField(location, { location = it }, m, label = { Text("Einsatzort (falls abweichend)") }, singleLine = true)
            OutlinedTextField(description, { description = it }, m, label = { Text("Beschreibung") }, minLines = 4)
            Button(onClick = { save() }, enabled = title.isNotBlank(), modifier = m) { Text("Speichern") }
        }
    }
}
