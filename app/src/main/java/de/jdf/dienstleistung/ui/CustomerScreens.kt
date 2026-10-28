package de.jdf.dienstleistung.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.jdf.dienstleistung.data.Customer
import de.jdf.dienstleistung.data.address
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(onOpen: (Long) -> Unit, onNew: () -> Unit) {
    val db = app().db
    val customers by remember { db.customers().all() }.collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    val shown = customers.filter {
        query.isBlank() || listOf(it.name, it.zipCity, it.street, it.phone).any { f -> f.contains(query.trim(), true) }
    }

    Scaffold { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            PageTitle("Kunden") {
                androidx.compose.material3.FilledIconButton(
                    onClick = onNew, modifier = Modifier.size(56.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = SignalYellow, contentColor = Navy),
                ) { Icon(Icons.Default.Add, "Neuer Kunde", Modifier.size(30.dp)) }
            }
            SearchField(query, { query = it }, "Name, Ort, Telefon …")
            if (shown.isEmpty()) {
                EmptyHint(if (customers.isEmpty()) "Noch keine Kunden.\nMit + einen anlegen." else "Keine Treffer.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.id }) { c ->
                        AppCard(Modifier.fillMaxWidth(), onClick = { onOpen(c.id) }) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                if (c.address.isNotBlank()) Text(c.address, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerDetailScreen(
    customerId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenJob: (Long) -> Unit,
    onNewJob: (Long) -> Unit,
) {
    val db = app().db
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val customer by remember(customerId) { db.customers().byId(customerId) }.collectAsState(initial = null)
    val jobs by remember(customerId) { db.jobs().listForCustomer(customerId) }.collectAsState(initial = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    val c = customer

    Scaffold(
        topBar = {
            BackTopBar(c?.name ?: "", onBack) {
                IconButton(onClick = { onEdit(customerId) }) { Icon(Icons.Default.Edit, "Bearbeiten") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Löschen") }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNewJob(customerId) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Auftrag", style = MaterialTheme.typography.labelLarge) },
                containerColor = SignalYellow, contentColor = Navy,
            )
        },
    ) { pad ->
        if (c == null) return@Scaffold
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (c.street.isNotBlank()) Text(c.street)
                        if (c.zipCity.isNotBlank()) Text(c.zipCity)
                        if (c.phone.isNotBlank()) Text("Tel: ${c.phone}")
                        if (c.email.isNotBlank()) Text(c.email)
                        if (c.notes.isNotBlank()) Text(c.notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (c.phone.isNotBlank()) BigButton("Anrufen", {
                        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.phone}")))
                    }, Modifier.weight(1f), icon = Icons.Default.Call, style = BtnStyle.Outline, height = 52.dp)
                    if (c.address.isNotBlank()) BigButton("Route", {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(c.address))))
                    }, Modifier.weight(1f), icon = Icons.Default.Directions, style = BtnStyle.Outline, height = 52.dp)
                    if (c.email.isNotBlank()) BigButton("", {
                        ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${c.email}")))
                    }, icon = Icons.Default.Email, style = BtnStyle.Outline, height = 52.dp)
                }
            }
            item { Text("Aufträge (${jobs.size})", style = MaterialTheme.typography.titleLarge) }
            items(jobs, key = { it.job.id }) { JobCard(it) { onOpenJob(it.job.id) } }
        }
    }

    if (confirmDelete && c != null) ConfirmDialog(
        "Kunde löschen?",
        "\"${c.name}\" wird gelöscht. Die Aufträge bleiben erhalten (ohne Kundenzuordnung).",
        onConfirm = { scope.launch { db.customers().delete(c); onBack() } },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
fun CustomerEditScreen(customerId: Long, onDone: () -> Unit, onBack: () -> Unit) {
    val db = app().db
    val scope = rememberCoroutineScope()
    var loaded by rememberSaveable { mutableStateOf(customerId == 0L) }
    var name by rememberSaveable { mutableStateOf("") }
    var street by rememberSaveable { mutableStateOf("") }
    var zipCity by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(customerId) {
        if (!loaded) {
            db.customers().get(customerId)?.let {
                name = it.name; street = it.street; zipCity = it.zipCity
                phone = it.phone; email = it.email; notes = it.notes
            }
            loaded = true
        }
    }

    fun save() = scope.launch {
        db.customers().upsert(
            Customer(id = customerId, name = name.trim(), street = street.trim(), zipCity = zipCity.trim(),
                phone = phone.trim(), email = email.trim(), notes = notes.trim())
        )
        onDone()
    }

    Scaffold(topBar = {
        BackTopBar(if (customerId == 0L) "Neuer Kunde" else "Kunde bearbeiten", onBack) {
            TextButton(onClick = { save() }, enabled = name.isNotBlank()) { Text("Speichern") }
        }
    }) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val m = Modifier.fillMaxWidth()
            OutlinedTextField(name, { name = it }, m, label = { Text("Name / Firma *") }, singleLine = true)
            OutlinedTextField(street, { street = it }, m, label = { Text("Straße, Nr.") }, singleLine = true)
            OutlinedTextField(zipCity, { zipCity = it }, m, label = { Text("PLZ Ort") }, singleLine = true)
            OutlinedTextField(phone, { phone = it }, m, label = { Text("Telefon") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            OutlinedTextField(email, { email = it }, m, label = { Text("E-Mail") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(notes, { notes = it }, m, label = { Text("Notizen") }, minLines = 3)
            BigButton("Speichern", { save() }, m, enabled = name.isNotBlank())
        }
    }
}
